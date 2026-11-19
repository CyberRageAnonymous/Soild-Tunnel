package com.soildtunnel.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * Background noise: pulls small, real pages through the tunnel at a jittered
 * rate so an idle tunnel keeps carrying ordinary-looking HTTPS instead of
 * sitting silent between the user's own requests.
 *
 * Deliberately cheap — each fetch reads at most a few KB and the whole session
 * is capped, so turning it on can never turn into an unbounded data bill.
 */
object DecoyTraffic {

    private const val TAG = "decoy"
    private const val SESSION_BYTE_CAP = 24L * 1024 * 1024
    private const val PER_FETCH_CAP = 64 * 1024
    private const val QUIET_MS = 9_000L
    private const val JITTER_MS = 45_000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    private val SITES = listOf(
        "www.wikipedia.org" to "/wiki/Main_Page",
        "www.cloudflare.com" to "/",
        "example.com" to "/",
        "www.mozilla.org" to "/",
        "neverssl.com" to "/",
        "www.gnu.org" to "/",
    )

    suspend fun run(socksPort: Int = TunnelConfig.SOCKS_PORT) = withContext(Dispatchers.IO) {
        var spent = 0L
        while (spent < SESSION_BYTE_CAP) {
            currentCoroutineContext().ensureActive()
            val (host, path) = SITES.random()
            spent += runCatching { fetch(socksPort, host, path) }
                .onFailure { DiagnosticsLog.d(TAG, "decoy fetch $host failed: ${it.message}") }
                .getOrDefault(0L)
            delay(Random.nextLong(QUIET_MS, QUIET_MS + JITTER_MS))
        }
    }

    /** Returns how many payload bytes were actually read. */
    private fun fetch(socksPort: Int, host: String, path: String): Long {
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress(TunnelConfig.SOCKS_HOST, socksPort),
        )
        Socket(proxy).use { raw ->
            raw.connect(InetSocketAddress.createUnresolved(host, 443), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, host, 443, true) as SSLSocket
            try {
                ssl.startHandshake()
                val request = buildString {
                    append("GET ").append(path).append(" HTTP/1.1\r\n")
                    append("Host: ").append(host).append("\r\n")
                    append("Accept: text/html,*/*\r\n")
                    append("Connection: close\r\n\r\n")
                }
                ssl.getOutputStream().apply {
                    write(request.toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val input = ssl.getInputStream()
                val buf = ByteArray(4096)
                var total = 0L
                while (total < PER_FETCH_CAP) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                }
                return total
            } finally {
                runCatching { ssl.close() }
            }
        }
    }
}
