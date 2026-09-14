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
 * Owns the tor daemon's whole lifetime: bridges, entry transports, torrc,
 * the libtor.so child process and the control port. The WARP engine stays
 * off; the TUN bridge talks straight to tor's loopback SOCKS port.
 *
 * Bridged entry follows the official apps' proven shape: official built-in
 * bridges (refreshed every two days) through IPtProxy transports driven
 * exactly like Orbot drives them.
 */
object TorManager {
    private const val TAG = "tor"

    private var process: Process? = null
    private var torDir: File? = null

    /**
     * Every touch of the controller, the transport set and the tor process
     * handle goes through here. A connect racing a still-running disconnect
     * used to hit Go maps from two threads at once, which kills the whole
     * process instantly.
     */
    private val ptLock = Any()

    /**
     * One backend per process: the Go side registers transports globally, so
     * a fresh controller per connect dies on every reconnect after the
     * first. Transports themselves are toggled per session.
     */
    private var ptController: Controller? = null

    /** Transports actually started this session — only these get stopped. */
    private val liveTransports = mutableSetOf<String>()

    /**
     * Transport callbacks run on Go threads and only feed the log. Nullable
     * params, like the official apps do — the bridge may call back empty.
     */
    private val transportEvents = object : OnTransportEvents {
        override fun connected(name: String?) {
            if (name != null) DiagnosticsLog.d(TAG, "Transport $name up.")
        }
        override fun error(name: String?, error: Exception?) {
            DiagnosticsLog.w(TAG, "Transport ${name ?: "?"} event: ${error?.message}")
        }
        override fun stopped(name: String?, error: Exception?) {}
    }

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

        val iptDir = File(filesDir, "ipt-state")
        checkStateDir(iptDir)
        val table = TorBridges.load(context)

        // One backend per process (see ptLock): fast local init, no network,
        // so holding the lock here is fine and closes the create/stop race.
        val controller = synchronized(ptLock) {
            ptController ?: try {
                Controller(iptDir.absolutePath, true, false, "WARN", transportEvents)
            } catch (t: Throwable) {
                // Anything the bridge throws here must surface as a readable
                // error, not a silent death.
                if (t is CancellationException) throw t
                throw IllegalStateException(
                    "Transport backend failed (${t.javaClass.simpleName}: ${t.message})",
                )
            }.also {
                ptController = it
                DiagnosticsLog.i(
                    TAG,
                    "Transports: snowflake ${PtProxy.snowflakeVersion()}, ${PtProxy.lyrebirdVersion()}",
                )
            }
        }

        // Which transports to run and which bridge lines to feed tor. The
        // torrc plugin lines always point at the loopback listeners started
        // below; bridge args (url=/fronts=/ice=) ride along inside the Bridge
        // lines themselves, exactly like the official apps do it.
        val plugins = mutableMapOf<String, Long>()
        val lines = mutableListOf<String>()
        synchronized(ptLock) {
            when (profile.torTransport) {
                TorTransport.DIRECT -> Unit
                TorTransport.OBFS4 -> {
                    val list = table["obfs4"].orEmpty()
                    if (list.isEmpty()) throw IllegalStateException("No built-in obfs4 bridges.")
                    plugins[PtProxy.Obfs4] = startTransport(controller, PtProxy.Obfs4)
                    lines += list
                }
                TorTransport.SNOWFLAKE -> {
                    val list = table["snowflake"].orEmpty()
                    if (list.isEmpty()) throw IllegalStateException("No built-in snowflake bridges.")
                    applySnowflakeArgs(controller, list)
                    plugins[PtProxy.Snowflake] = startTransport(controller, PtProxy.Snowflake)
                    lines += list
                }
                TorTransport.MEEK -> {
                    val list = table["meek"] ?: table["meek-azure"].orEmpty()
                    if (list.isEmpty()) throw IllegalStateException("No built-in meek bridges.")
                    plugins[PtProxy.MeekLite] = startTransport(controller, PtProxy.MeekLite)
                    lines += list
                }
                TorTransport.CUSTOM -> {
                    val customs = userBridges(profile)
                    if (customs.isEmpty()) throw IllegalStateException("No bridges entered.")
                    for (name in customTransports(customs)) {
                        if (name == PtProxy.Snowflake) applySnowflakeArgs(controller, customs)
                        plugins[name] = startTransport(controller, name)
                    }
                    lines += customs
                    // Privacy note: only the SHAPE is logged (how many lines,
                    // how many parts each) — never the bridges themselves. A
                    // pasted bridge that got wrapped into two lines shows up
                    // here as a short fragment line.
                    val shapes = customs.map { it.split(" ").size }
                    DiagnosticsLog.i(TAG, "Custom bridges: ${shapes.size} (parts: ${shapes.joinToString(",")})")
                }
            }
        }

        File(dir, "torrc").writeText(buildTorrc(dir, profile, plugins, lines))
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
                proc.inputStream.bufferedReader().useLines { output ->
                    output.forEach { DiagnosticsLog.d(TAG, it) }
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

    /** Starts one transport and returns its loopback port. Throws readable. */
    private fun startTransport(controller: Controller, name: String): Long {
        try {
            controller.start(name, null)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            throw IllegalStateException("Transport $name failed (${t.javaClass.simpleName}: ${t.message})")
        }
        val port = controller.port(name)
        if (port <= 0) throw IllegalStateException("Transport $name failed to open a port.")
        liveTransports += name
        DiagnosticsLog.i(TAG, "Transport $name listening on 127.0.0.1:$port")
        return port
    }

    /** Snowflake rendezvous details come from the bridge data, not memory. */
    private fun applySnowflakeArgs(controller: Controller, lines: List<String>) {
        val args = TorBridges.snowflakeArgs(lines)
        args["ice"]?.let { controller.snowflakeIceServers = it }
        args["url"]?.let { controller.snowflakeBrokerUrl = it }
        args["fronts"]?.let { controller.snowflakeFrontDomains = it }
        controller.snowflakeAmpCacheUrl = ""
        controller.snowflakeSqsUrl = ""
        controller.snowflakeSqsCreds = ""
        controller.snowflakeMaxPeers = 1
    }

    /**
     * The user's bridges, lightly cleaned and WITHOUT the "Bridge" keyword
     * (the torrc writer adds it uniformly). Lines may be pasted with or
     * without it; "#" comments are skipped. Tor itself validates each line.
     */
    fun userBridges(profile: ConnectionProfile): List<String> =
        profile.torBridges.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 400 && !it.startsWith("#") }
            .map { it.removePrefix("Bridge ").trim() }
            .filter { it.isNotEmpty() }
            .take(20)
            .map { it.split(Regex("\\s+")).joinToString(" ") }
            .toList()

    /** Transport names a set of (prefix-stripped) bridge lines needs. */
    private fun customTransports(lines: List<String>): Set<String> =
        lines.mapNotNull { line ->
            when (line.substringBefore(" ").lowercase()) {
                "obfs4" -> PtProxy.Obfs4
                "snowflake" -> PtProxy.Snowflake
                "webtunnel" -> PtProxy.Webtunnel
                "meek_lite", "meek" -> PtProxy.MeekLite
                else -> null
            }
        }.toSet()

    /**
     * The Go side only says "nil" by refusing to construct, so prove
     * writability here first — with a message that names the actual problem.
     */
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

    fun buildTorrc(
        dir: File,
        profile: ConnectionProfile,
        plugins: Map<String, Long>,
        lines: List<String>,
    ): String = buildString {
        // Tor is dual-stack by default; these make that explicit so the
        // config reads the way it behaves. IPv6 destinations only succeed
        // when the exit supports them -- exactly like the official apps.
        appendLine("SocksPort 127.0.0.1:${TorDefaults.SOCKS_PORT} IPv6Traffic")
        appendLine("ClientUseIPv6 1")
        appendLine("ClientPreferIPv6ORPort 1")
        appendLine("DNSPort 127.0.0.1:${TorDefaults.DNS_PORT}")
        appendLine("ControlPort 127.0.0.1:${TorDefaults.CONTROL_PORT}")
        appendLine("CookieAuthentication 1")
        appendLine("DataDirectory ${dir.absolutePath}")
        appendLine("GeoIPFile ${File(dir, "geoip").absolutePath}")
        appendLine("GeoIPv6File ${File(dir, "geoip6").absolutePath}")
        // Phones die without this: tor otherwise fsyncs its state constantly.
        appendLine("AvoidDiskWrites 1")
        if (lines.isEmpty()) {
            appendLine("UseBridges 0")
        } else {
            appendLine("UseBridges 1")
            plugins.forEach { (name, port) ->
                appendLine("ClientTransportPlugin $name socks5 127.0.0.1:$port")
            }
            lines.forEach { appendLine("Bridge $it") }
        }
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
