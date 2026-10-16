package com.soildtunnel.app.core

import android.content.Context
import ca.psiphon.PsiphonTunnel
import com.soildtunnel.app.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object PsiphonManager : PsiphonTunnel.HostService {
    private const val TAG = "psiphon"

    @Volatile private var appContext: Context? = null
    @Volatile private var tunnel: PsiphonTunnel? = null
    @Volatile private var running = false
    @Volatile private var connected = false
    @Volatile private var socksPort = 0
    @Volatile private var serverRegion = ""
    @Volatile private var availableRegions: List<String> = emptyList()
    @Volatile private var upstreamPort = TunnelConfig.SOCKS_PORT
    @Volatile private var wantedRegion = ""

    private val lock = Any()

    fun isAlive(): Boolean = running
    fun isConnected(): Boolean = running && connected
    fun localSocksPort(): Int = socksPort
    fun connectedRegion(): String = serverRegion
    fun regions(): List<String> = availableRegions

    override fun getContext(): Context =
        appContext ?: throw IllegalStateException("PsiphonManager not started")

    override fun getPsiphonConfig(): String = buildConfigJson()

    override fun onConnecting() {
        DiagnosticsLog.i(TAG, "Psiphon handshake started.")
    }

    override fun onConnected() {
        connected = true
        DiagnosticsLog.i(TAG, "Psiphon tunnel connected.")
    }

    override fun onListeningSocksProxyPort(port: Int) {
        socksPort = port
        DiagnosticsLog.i(TAG, "Psiphon SOCKS listening on 127.0.0.1:$port")
    }

    override fun onListeningHttpProxyPort(port: Int) {
        DiagnosticsLog.i(TAG, "Psiphon HTTP listening on 127.0.0.1:$port")
    }

    override fun onSocksProxyPortInUse(port: Int) {
        DiagnosticsLog.e(TAG, "Psiphon SOCKS port $port already in use.")
    }

    override fun onAvailableEgressRegions(regions: List<String>) {
        availableRegions = regions.filter { it.length == 2 }.map { it.uppercase() }
        DiagnosticsLog.i(TAG, "Psiphon egress regions: ${availableRegions.joinToString(" ")}")
    }

    override fun onConnectedServerRegion(region: String) {
        serverRegion = region.uppercase()
        DiagnosticsLog.i(TAG, "Psiphon exit region: $serverRegion")
    }

    override fun onUpstreamProxyError(message: String) {
        DiagnosticsLog.e(TAG, "Psiphon upstream proxy error: $message")
    }

    override fun onDiagnosticMessage(message: String) {
        DiagnosticsLog.i(TAG, "psiphon: $message")
    }

    suspend fun start(
        context: Context,
        upstreamSocksPort: Int,
        egressRegion: String,
        onProgress: (percent: Int, summary: String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        stop()
        if (!PsiphonDefaults.hasCredentials()) {
            throw IllegalStateException("Psiphon partner credentials are not configured")
        }
        appContext = context.applicationContext
        upstreamPort = upstreamSocksPort
        wantedRegion = egressRegion.trim().uppercase().takeIf { it.length == 2 } ?: ""
        connected = false
        socksPort = 0
        serverRegion = ""
        File(appContext!!.filesDir, PsiphonDefaults.DATA_DIR).apply { mkdirs() }
        val t = synchronized(lock) {
            val created = PsiphonTunnel.newPsiphonTunnel(this@PsiphonManager)
            tunnel = created
            running = true
            created
        }
        try {
            t.startTunneling("")
            t.appResumed()
        } catch (e: Exception) {
            stop()
            throw e
        }
        onProgress(15, "psiphon handshake")
        val deadline = System.currentTimeMillis() + PsiphonDefaults.ESTABLISH_TIMEOUT_MS
        while (isAlive() && !connected && System.currentTimeMillis() < deadline) {
            delay(1_000L)
        }
        if (!isConnected()) {
            stop()
            throw IllegalStateException("Psiphon did not connect in time")
        }
        onProgress(100, serverRegion.ifBlank { "connected" })
    }

    fun stop() {
        running = false
        connected = false
        val t = synchronized(lock) {
            val current = tunnel
            tunnel = null
            current
        }
        runCatching { t?.stop() }
    }

    suspend fun awaitExit(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (running && System.currentTimeMillis() < deadline) {
            delay(1_000L)
        }
        return !running
    }

    suspend fun switchRegion(
        context: Context,
        countryCode: String,
        onProgress: (percent: Int, summary: String) -> Unit,
    ) {
        start(context, upstreamPort, countryCode, onProgress)
    }

    private fun buildConfigJson(): String {
        val dir = File(appContext!!.filesDir, PsiphonDefaults.DATA_DIR).apply { mkdirs() }
        val config = JSONObject()
        config.put("ClientVersion", BuildConfig.VERSION_CODE.toString())
        config.put("PropagationChannelId", PsiphonDefaults.PROPAGATION_CHANNEL_ID)
        config.put("SponsorId", PsiphonDefaults.SPONSOR_ID)
        config.put("DataRootDirectory", dir.absolutePath)
        config.put("EgressRegion", wantedRegion)
        config.put("UpstreamProxyURL", "socks5://127.0.0.1:$upstreamPort")
        config.put("EstablishTunnelTimeoutSeconds", 0)
        config.put("LocalSocksProxyPort", PsiphonDefaults.SOCKS_PORT)
        config.put("LocalHttpProxyPort", PsiphonDefaults.HTTP_PORT)
        config.put("EmitDiagnosticNotices", true)
        runCatching { JSONArray(PsiphonDefaults.REMOTE_SERVER_LIST_URLS_JSON) }
            .getOrNull()
            ?.takeIf { it.length() > 0 }
            ?.let { config.put("RemoteServerListURLs", it) }
        PsiphonDefaults.REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY
            .takeIf { it.isNotBlank() }
            ?.let { config.put("RemoteServerListSignaturePublicKey", it) }
        return config.toString()
    }
}
