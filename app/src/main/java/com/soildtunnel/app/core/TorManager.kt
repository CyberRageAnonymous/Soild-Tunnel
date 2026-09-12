package com.soildtunnel.app.core

import android.content.Context
import com.soildtunnel.app.R
import com.soildtunnel.app.model.ConnectionProfile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the tor daemon's whole lifetime: torrc, the libtor.so child process
 * and the control port. Direct entry only — the WARP engine stays off and
 * the TUN bridge talks straight to tor's loopback SOCKS port.
 */
object TorManager {
    private const val TAG = "tor"

    private var process: Process? = null
    private var torDir: File? = null

    @Volatile
    var running = false
        private set

    fun isAlive(): Boolean = running && process?.isAlive == true

    /**
     * Starts tor and blocks until it is bootstrapped (circuits can be
     * built). [onProgress] gets bootstrap percent + summary for the
     * notification. Throws with a user-readable message on failure.
     */
    suspend fun start(
        context: Context,
        profile: ConnectionProfile,
        onProgress: (percent: Int, summary: String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        stop()
        val filesDir = context.filesDir
        val dir = File(filesDir, "tor-data").apply { mkdirs() }
        torDir = dir
        ensureGeoip(context, dir)

        File(dir, "torrc").writeText(buildTorrc(dir, profile))
        val bin = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
        if (!bin.exists()) throw IllegalStateException("Tor binary missing: ${bin.absolutePath}")
        runCatching { bin.setExecutable(true) }

        val proc = ProcessBuilder(bin.absolutePath, "-f", File(dir, "torrc").absolutePath)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = filesDir.absolutePath
                environment()["TMPDIR"] = filesDir.absolutePath
            }
            .start()
        process = proc
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { DiagnosticsLog.d(TAG, it) }
                }
            } catch (_: Exception) {
            }
        }, "tor-log").apply { isDaemon = true }.start()
        running = true

        try {
            waitForControl()
            waitForBootstrap(onProgress)
            if (!PortProbe.awaitOpen("127.0.0.1", TorDefaults.SOCKS_PORT, 15_000)) {
                throw IllegalStateException(context.getString(R.string.err_tor_socks))
            }
            DiagnosticsLog.i(TAG, "Tor is up — SOCKS on 127.0.0.1:${TorDefaults.SOCKS_PORT}.")
        } catch (e: CancellationException) {
            stop()
            throw e
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        running = false
        val proc = process
        process = null
        if (proc != null) {
            runCatching {
                proc.destroy()
                if (!proc.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            }
        }
    }

    /** Parks the caller until tor exits or the timeout elapses. */
    suspend fun awaitExit(timeoutMs: Long): Boolean = runCatching {
        runInterruptible(Dispatchers.IO) {
            process?.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) ?: true
        }
    }.getOrDefault(false)

    /**
     * Switches the exit country live (blank = automatic). Only opens the
     * control port for a moment; safe to call from the UI layer.
     */
    suspend fun switchExitCountry(countryCode: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            control().use { it.setExit(countryCode) }
        }.getOrDefault(false)
    }

    private fun control(): TorControl {
        val dir = torDir ?: throw IllegalStateException("Tor is not running.")
        val c = TorControl()
        c.connect()
        if (!c.authenticate(File(dir, "control_auth_cookie"))) {
            c.close()
            throw IllegalStateException("Tor control auth failed.")
        }
        return c
    }

    private suspend fun waitForControl() {
        val ok = withTimeoutOrNull(45_000L) {
            while (true) {
                val alive = runCatching { control().close(); true }.getOrDefault(false)
                if (alive) return@withTimeoutOrNull true
                delay(750)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        if (!ok) throw IllegalStateException("Tor control port never came up.")
    }

    private suspend fun waitForBootstrap(onProgress: (Int, String) -> Unit) {
        val done = withTimeoutOrNull(240_000L) {
            while (true) {
                val state = runCatching {
                    control().use { it.bootstrap() }
                }.getOrNull()
                if (state != null) {
                    onProgress(state.first, state.second)
                    if (state.first >= 100) return@withTimeoutOrNull true
                }
                delay(1000)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        if (!done) throw IllegalStateException("Tor could not bootstrap in time.")
    }

    fun buildTorrc(dir: File, profile: ConnectionProfile): String = buildString {
        appendLine("SocksPort 127.0.0.1:${TorDefaults.SOCKS_PORT}")
        appendLine("DNSPort 127.0.0.1:${TorDefaults.DNS_PORT}")
        appendLine("ControlPort 127.0.0.1:${TorDefaults.CONTROL_PORT}")
        appendLine("CookieAuthentication 1")
        appendLine("DataDirectory ${dir.absolutePath}")
        appendLine("GeoIPFile ${File(dir, "geoip").absolutePath}")
        appendLine("GeoIPv6File ${File(dir, "geoip6").absolutePath}")
        // Phones die without this: tor otherwise fsyncs its state constantly.
        appendLine("AvoidDiskWrites 1")
        appendLine("UseBridges 0")
        val exit = profile.torExitCountry.trim().uppercase()
        if (exit.matches(Regex("^[A-Z]{2}$"))) {
            appendLine("ExitNodes {$exit}")
            appendLine("StrictNodes 1")
        }
    }

    /**
     * Country lookup for ExitNodes lives in these two files. They ship in
     * assets (from the matching tor release) and are copied once — 10 MB
     * copied on every connect would be silly.
     */
    private fun ensureGeoip(context: Context, dir: File) {
        val v4 = File(dir, "geoip")
        val v6 = File(dir, "geoip6")
        if (v4.exists() && v6.exists()) return
        context.assets.open("tor/geoip").use { ins -> v4.outputStream().use { ins.copyTo(it) } }
        context.assets.open("tor/geoip6").use { ins -> v6.outputStream().use { ins.copyTo(it) } }
        DiagnosticsLog.i(TAG, "GeoIP database installed.")
    }
}
