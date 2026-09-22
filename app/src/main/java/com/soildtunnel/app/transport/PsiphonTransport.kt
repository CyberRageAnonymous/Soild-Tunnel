package com.soildtunnel.app.transport

import android.content.Context
import ca.psiphon.PsiphonTunnel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import com.soildtunnel.app.core.TunnelConfig
import java.io.File

class PsiphonTransport(
    private val context: Context,
    private val region: String,
    private val upstreamProxy: String?,
    private val log: (String) -> Unit = {},
) : ExternalTransport, PsiphonTunnel.HostService {
    private var tunnel: PsiphonTunnel? = null
    private var ready = CompletableDeferred<Int>()
    private var localPort = TunnelConfig.PSIPHON_SOCKS_PORT
    @Volatile private var connected = false
    @Volatile private var config = "{}"
    private var front: PsiphonSocksFront? = null

    override suspend fun start(): Int = withContext(Dispatchers.IO) {
        val wanted = region.trim().uppercase()
        val entries = runCatching {
            context.assets.open("server_entries.txt").bufferedReader().use { it.readText().trim() }
        }.getOrElse { throw IllegalStateException("Psiphon server list missing from assets") }
        check(entries.isNotEmpty()) { "Psiphon server list empty" }
        log("entries=${entries.length} chars, wanted region='${wanted.ifEmpty { "auto" }}', upstream=${upstreamProxy ?: "direct"}")
        val attempts = if (wanted.isEmpty()) listOf("") else listOf(wanted, "")
        var lastError: Exception? = null
        for ((index, egress) in attempts.withIndex()) {
            if (index > 0) {
                stopTunnelQuietly()
                File(context.filesDir, "psiphon").deleteRecursively()
                com.soildtunnel.app.core.PortProbe.awaitClosed(
                    TunnelConfig.SOCKS_HOST, TunnelConfig.PSIPHON_SOCKS_PORT, 5_000,
                )
            }
            try {
                return@withContext establish(egress, entries)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                stopTunnelQuietly()
            }
        }
        throw lastError ?: IllegalStateException("Psiphon did not establish")
    }

    private suspend fun establish(egress: String, entries: String): Int {
        val deferred = CompletableDeferred<Int>()
        ready = deferred
        localPort = TunnelConfig.PSIPHON_SOCKS_PORT
        config = buildConfig(egress)
        log("creating tunnel, exit=${egress.ifEmpty { "auto" }}")
        val created = PsiphonTunnel.newPsiphonTunnel(this)
        tunnel = created
        created.setVpnMode(true)
        log("starting tunneling")
        created.startTunneling(entries)
        log("tunneling started, waiting for connect")
        val boundPort = try {
            withTimeout(200_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Psiphon found no usable server in 200s")
        }
        front = PsiphonSocksFront().also { it.start(TunnelConfig.CHAIN_SOCKS_PORT, boundPort) }
        return TunnelConfig.CHAIN_SOCKS_PORT
    }

    private fun buildConfig(egress: String): String = JSONObject().apply {
        put("PropagationChannelId", "FFFFFFFFFFFFFFFF")
        put("SponsorId", "1111111111111111")
        if (egress.isNotEmpty()) put("EgressRegion", egress)
        put("EstablishTunnelTimeoutSeconds", 180)
        put("DataStoreDirectory", File(context.filesDir, "psiphon").apply { mkdirs() }.absolutePath)
        put("ClientVersion", "127")
        put("LocalSocksProxyPort", TunnelConfig.PSIPHON_SOCKS_PORT)
        put("DisableLocalHTTPProxy", true)
        put("RemoteServerListSignaturePublicKey", REMOTE_SERVER_LIST_PUBLIC_KEY)
        put("ServerEntrySignaturePublicKey", "sHuUVTWaRyh5pZwy4UguSgkwmBe0EHtJJkoF5WrxmvA=")
        put("ExchangeObfuscationKey", "DpXzloJk1Hw6aSzmKKky0xcahsEHubch81Mi6K0XMlU=")
        put("EmitBytesTransferred", true)
        put("EmitDiagnosticNotices", true)
        put("DeviceRegion", "IR")
        put("ConnectionWorkerPoolSize", 12)
        put("DNSResolverPreferredAlternateServers", JSONArray(listOf("1.1.1.1:53", "8.8.8.8:53", "9.9.9.9:53")))
        put("DNSResolverPreferAlternateServerProbability", 1.0)
        upstreamProxy?.takeIf { it.isNotBlank() }?.let { put("UpstreamProxyUrl", it) }
    }.toString()

    private fun stopTunnelQuietly() {
        try { tunnel?.stop() } catch (_: Exception) {}
        tunnel = null
        connected = false
    }

    override fun bindToDevice(fd: Long) {}
    override fun onListeningSocksProxyPort(port: Int) {
        localPort = port
    }
    override fun onListeningHttpProxyPort(port: Int) = Unit
    override fun onConnecting() {
        log("psiphon connecting")
    }
    override fun onConnected() {
        connected = true
        log("psiphon connected on local port $localPort")
        if (!ready.isCompleted) ready.complete(localPort)
    }
    override fun onExiting() {
        connected = false
        log("psiphon exiting")
        if (!ready.isCompleted) {
            ready.completeExceptionally(IllegalStateException("Psiphon stopped before it established"))
        }
    }
    override fun onClientAddress(address: String?) = Unit
    override fun onHomepage(homepage: String?) = Unit
    override fun onClientRegion(region: String?) = Unit
    override fun onAvailableEgressRegions(regions: MutableList<String>?) {}
    override fun onConnectedServerRegion(region: String?) = Unit
    override fun onBytesTransferred(sent: Long, received: Long) = Unit
    override fun getContext(): Context = context
    override fun getPsiphonConfig(): String = config

    override fun isAlive(): Boolean = connected && tunnel != null
    override fun stop() {
        stopTunnelQuietly()
        try { front?.stop() } catch (_: Exception) {}
        front = null
        File(context.filesDir, "psiphon").deleteRecursively()
    }

    companion object {
        const val REMOTE_SERVER_LIST_PUBLIC_KEY =
            "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="
    }
}
