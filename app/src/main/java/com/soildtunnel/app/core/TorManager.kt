package com.soildtunnel.app.core

import IPtProxy.Controller
import IPtProxy.IPtProxy as PtProxy
import IPtProxy.OnTransportEvents
import android.content.Context
import com.soildtunnel.app.R
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.TorTransport
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the tor daemon's whole lifetime: entry transports (Snowflake /
 * Lyrebird for custom bridges), torrc, the libtor.so child process and the
 * control port. The WARP engine is never involved — in Tor mode the TUN
 * bridge talks straight to tor's loopback SOCKS port.
 */
object TorManager {
    private const val TAG = "tor"

    private var process: Process? = null
    private var torDir: File? = null
    /**
     * The transport backend lives as long as the app process: its Go side
     * registers transports globally and refuses a second registration, so a
     * fresh controller per connect would die on every reconnect after the
     * first. One controller, started transports toggled per session.
     *
     * Every touch of the controller, the transport set and the tor process
     * handle goes through [ptLock]: a connect racing a still-running
     * disconnect used to hit Go maps from two threads at once, which kills
     * the whole process instantly (the kick-out on fast protocol switches).
     */
    private val ptLock = Any()
    private var ptController: Controller? = null
    /** Transports actually started this session — only these get stopped. */
    private val liveTransports = mutableSetOf<String>()

    /**
     * Transport callbacks run on Go threads and have nowhere useful to go in
     * the UI — except errors, which are worth one log line. Passed explicitly
     * instead of null: the bridge does not accept a missing listener.
     */
    private val silentEvents = object : OnTransportEvents {
        override fun connected(methodName: String) {}
        override fun error(methodName: String, e: Exception) {
            DiagnosticsLog.w(TAG, "Transport $methodName reported: ${e.message}")
        }
        override fun stopped(methodName: String, e: Exception) {}
    }

    @Volatile
    var running = false
        private set

    fun isAlive(): Boolean = running && process?.isAlive == true

    /**
     * Starts transports + tor and blocks until tor is bootstrapped (circuits
     * can be built). [onProgress] gets bootstrap percent + summary for the
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

        val iptDir = File(filesDir, "ipt-state")
        checkStateDir(iptDir)

        val bridges = userBridges(profile)
        val wantSnowflake = profile.torTransport == TorTransport.SNOWFLAKE ||
            (profile.torTransport == TorTransport.CUSTOM &&
                bridges.any { it.lowercase().startsWith("bridge snowflake") })

        // Fail fast with a NAMED cause instead of hanging at 10% forever.
        if (wantSnowflake) {
            val pre = SnowflakePreflight.run()
            DiagnosticsLog.i(TAG, "Snowflake precheck: ${pre.detail}")
            if (!pre.ok) throw IllegalStateException("Snowflake precheck failed: ${pre.detail}")
        }

        // One backend per process (see ptLock): fast local init, no network,
        // so holding the lock here is fine and closes the create/stop race.
        val controller = synchronized(ptLock) {
            ptController ?: createController(iptDir).also {
                ptController = it
                DiagnosticsLog.i(
                    TAG,
                    "Transports: snowflake ${PtProxy.snowflakeVersion()}, ${PtProxy.lyrebirdVersion()}",
                )
            }
        }

        var snowflakePort = 0L
        var obfs4Port = 0L
        var webtunnelPort = 0L
        // Transports start and stop under the same lock: Go maps are not
        // thread-safe, and overlapping calls abort the whole process.
        // Only fast local binds happen here — no network, no suspension.
        synchronized(ptLock) {
            if (wantSnowflake) {
                DiagnosticsLog.i(TAG, "Starting the Snowflake entry transport…")
                controller.snowflakeIceServers = TorDefaults.SNOWFLAKE_ICE
                controller.snowflakeBrokerUrl = TorDefaults.SNOWFLAKE_BROKER
                controller.snowflakeFrontDomains = TorDefaults.SNOWFLAKE_FRONT
                controller.snowflakeMaxPeers = 1
                runCatching { controller.start("snowflake", "") }
                    .onFailure {
                        throw IllegalStateException(
                            "Snowflake failed to start (${it.javaClass.simpleName}: ${it.message})",
                        )
                    }
                snowflakePort = controller.port("snowflake")
                if (snowflakePort <= 0) throw IllegalStateException("Snowflake failed to start.")
                liveTransports += "snowflake"
                DiagnosticsLog.i(TAG, "Snowflake listening on 127.0.0.1:$snowflakePort")
            }
            if (profile.torTransport == TorTransport.CUSTOM) {
                DiagnosticsLog.i(TAG, "Starting Lyrebird for custom bridges…")
                runCatching {
                    controller.start("obfs4", "")
                    controller.start("webtunnel", "")
                }.onFailure {
                    throw IllegalStateException(
                        "Bridge transport failed to start (${it.javaClass.simpleName}: ${it.message})",
                    )
                }
                obfs4Port = controller.port("obfs4")
                webtunnelPort = controller.port("webtunnel")
                if (obfs4Port <= 0) throw IllegalStateException("Bridge transport failed to start.")
                liveTransports += "obfs4"
                liveTransports += "webtunnel"
            }
        }

        File(dir, "torrc").writeText(
            buildTorrc(dir, profile, snowflakePort, obfs4Port, webtunnelPort),
        )
        // Privacy note: only the SHAPE is logged (how many lines, how many
        // parts each) — never the bridges themselves. A pasted bridge that
        // got wrapped into two lines shows up here as a short fragment line,
        // which is the classic "stuck at 10%" cause.
        if (profile.torTransport == TorTransport.CUSTOM) {
            val shapes = userBridges(profile).map { it.split(" ").size }
            DiagnosticsLog.i(TAG, "Custom bridges in torrc: ${shapes.size} (parts: ${shapes.joinToString(",")})")
        }
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
        synchronized(ptLock) { process = proc }
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
        val proc = synchronized(ptLock) {
            val p = process
            process = null
            p
        }
        if (proc != null) {
            runCatching {
                proc.destroy()
                if (!proc.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            }
        }
        synchronized(ptLock) {
            ptController?.let { controller ->
                liveTransports.toList().forEach { name ->
                    runCatching { controller.stop(name) }
                }
            }
            liveTransports.clear()
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

    /**
     * The Go side only says "nil" when it cannot use this folder, so prove
     * writability here first — with a message that names the actual problem.
     */
    private fun createController(iptDir: File): Controller {
        return try {
            PtProxy.newController(iptDir.absolutePath, true, false, "WARN", silentEvents)
                ?: throw IllegalStateException(readableNilReason(iptDir))
        } catch (t: Throwable) {
            // Anything the bridge throws here must surface as a readable
            // error, not a silent death.
            if (t is CancellationException) throw t
            if (t is IllegalStateException) throw t
            throw IllegalStateException(
                "Transport backend failed (${t.javaClass.simpleName}: ${t.message})",
            )
        }
    }

    private fun checkStateDir(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("State folder cannot be created: ${dir.absolutePath}")
        }
        val probe = File(dir, ".writetest")
        try {
            probe.writeBytes(byteArrayOf(1))
            if (!probe.delete()) DiagnosticsLog.w(TAG, "State folder probe file lingers.")
        } catch (e: Exception) {
            throw IllegalStateException("State folder not writable: ${dir.absolutePath} (${e.message})")
        }
    }

    /**
     * The Go backend reports failure only as nil. Its own file log (when it
     * got far enough to open one) usually names the real cause — surface it.
     */
    private fun readableNilReason(dir: File): String {
        val tail = runCatching {
            File(dir, "ipt.log").takeIf { it.exists() }
                ?.bufferedReader()?.useLines { lines -> lines.toList().takeLast(6) }
                ?.joinToString(" | ")?.take(600)
        }.getOrNull().orEmpty()
        return if (tail.isNotBlank()) "Transport backend refused to start: $tail"
        else "Transport backend refused to start (no backend log)."
    }

    /**
     * The user's bridges, lightly cleaned. Lines may be pasted with or
     * without the leading "Bridge" (bots share both shapes) and "#" comments
     * are skipped. Tor itself validates each line and warns in the log about
     * the ones it cannot use, so nothing is silently dropped here.
     */
    fun userBridges(profile: ConnectionProfile): List<String> =
        profile.torBridges.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 400 && !it.startsWith("#") }
            .map { if (it.startsWith("Bridge ")) it else "Bridge $it" }
            .take(20)
            .map { it.split(Regex("\\s+")).joinToString(" ") }
            .toList()

    fun buildTorrc(
        dir: File,
        profile: ConnectionProfile,
        snowflakePort: Long,
        obfs4Port: Long,
        webtunnelPort: Long,
    ): String = buildString {
        appendLine("SocksPort 127.0.0.1:${TorDefaults.SOCKS_PORT}")
        appendLine("DNSPort 127.0.0.1:${TorDefaults.DNS_PORT}")
        appendLine("ControlPort 127.0.0.1:${TorDefaults.CONTROL_PORT}")
        appendLine("CookieAuthentication 1")
        appendLine("DataDirectory ${dir.absolutePath}")
        appendLine("GeoIPFile ${File(dir, "geoip").absolutePath}")
        appendLine("GeoIPv6File ${File(dir, "geoip6").absolutePath}")
        // Phones die without this: tor otherwise fsyncs its state constantly.
        appendLine("AvoidDiskWrites 1")
        val exit = profile.torExitCountry.trim().uppercase()
        if (exit.matches(Regex("^[A-Z]{2}$"))) {
            appendLine("ExitNodes {$exit}")
            appendLine("StrictNodes 1")
        }
        when (profile.torTransport) {
            TorTransport.DIRECT -> appendLine("UseBridges 0")
            TorTransport.SNOWFLAKE -> {
                appendLine("UseBridges 1")
                appendLine("ClientTransportPlugin snowflake socks5 127.0.0.1:$snowflakePort")
                appendLine(TorDefaults.SNOWFLAKE_BRIDGE)
            }
            TorTransport.CUSTOM -> {
                appendLine("UseBridges 1")
                appendLine("ClientTransportPlugin obfs4 socks5 127.0.0.1:$obfs4Port")
                appendLine("ClientTransportPlugin webtunnel socks5 127.0.0.1:$webtunnelPort")
                if (snowflakePort > 0) {
                    appendLine("ClientTransportPlugin snowflake socks5 127.0.0.1:$snowflakePort")
                }
                userBridges(profile).forEach { appendLine(it) }
            }
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
