package com.soildtunnel.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext

/**
 * Download speed through the local SOCKS5 tunnel. Fetches a fixed-size test
 * file from Cloudflare's public speed endpoint and measures real throughput.
 */
object SpeedTest {
    data class Result(val mbps: Double, val handshakeMs: Long, val bytes: Long)

    private const val HOST = "speed.cloudflare.com"
    private const val PORT = 443
    private const val MAX_BYTES = 25_000_000L
    private const val MAX_SECONDS = 12.0
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    suspend fun run(onProgress: (mbps: Double) -> Unit = {}): Result =
        withContext(Dispatchers.IO) {
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(TunnelConfig.SOCKS_HOST, TunnelConfig.SOCKS_PORT),
            )
            Socket(proxy).use { raw ->
                // Unresolved so the hostname is resolved BY the tunnel, never by
                // the local resolver where it could be poisoned.
                raw.connect(InetSocketAddress.createUnresolved(HOST, PORT), CONNECT_TIMEOUT_MS)
                raw.soTimeout = READ_TIMEOUT_MS
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, HOST, PORT, true) as SSLSocket
                ssl.soTimeout = READ_TIMEOUT_MS
                val handshakeStart = System.nanoTime()
                ssl.startHandshake()
                val handshakeMs = (System.nanoTime() - handshakeStart) / 1_000_000

                val request = buildString {
                    append("GET /__down?bytes=").append(MAX_BYTES)
                    append("&r=").append(System.nanoTime())
                    append(" HTTP/1.1\r\n")
                    append("Host: ").append(HOST).append("\r\n")
                    append("User-Agent: curl/8.5.0\r\n")
                    append("Accept: */*\r\n")
                    append("Connection: close\r\n\r\n")
                }
                ssl.getOutputStream().apply {
                    write(request.toByteArray(Charsets.US_ASCII))
                    flush()
                }

                val input = ssl.getInputStream()
                val status = readStatusAndSkipHeaders(input)
                if (status != 200) throw IllegalStateException("http $status")

                var bytes = 0L
                val buf = ByteArray(64 * 1024)
                val started = System.nanoTime()
                val deadline = started + (MAX_SECONDS * 1_000_000_000).toLong()
                var lastEmit = started
                while (bytes < MAX_BYTES && System.nanoTime() < deadline) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    bytes += n
                    val now = System.nanoTime()
                    if (now - lastEmit >= 500_000_000L) {
                        lastEmit = now
                        onProgress(mbps(bytes, now - started))
                    }
                }
                if (bytes == 0L) throw IllegalStateException("no data")
                Result(mbps(bytes, System.nanoTime() - started), handshakeMs, bytes)
            }
        }

    private fun readStatusAndSkipHeaders(input: InputStream): Int {
        val statusLine = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IllegalStateException("stream ended in status line")
            if (b == '\r'.code) {
                input.read()
                break
            }
            statusLine.append(b.toChar())
            if (statusLine.length > 64) throw IllegalStateException("bad status line")
        }
        val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
            ?: throw IllegalStateException("bad status line")

        // Consume headers up to the blank line.
        var blank = 0
        while (blank < 2) {
            val b = input.read()
            if (b < 0) throw IllegalStateException("stream ended in headers")
            if (b == '\n'.code) blank++ else if (b != '\r'.code) blank = 0
        }
        return code
    }

    private fun mbps(bytes: Long, nanos: Long): Double {
        if (nanos <= 0) return 0.0
        return bytes * 8.0 / (nanos / 1_000_000_000.0) / 1_000_000.0
    }
}
