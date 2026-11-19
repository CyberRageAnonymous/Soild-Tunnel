package com.soildtunnel.app.core

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Local listener that answers every request with a plain static website.
 *
 * Off unless the user picks a port. When it is on, anything that knocks on
 * that port — a LAN scanner, an automated probe — gets a normal looking
 * web server instead of a dead port or a proxy that would identify the app.
 *
 * It only ever serves bytes from [PAGE]; there is no routing to the tunnel
 * and nothing it returns depends on what the client asked for.
 */
object CoverServer {

    private const val TAG = "cover"
    private const val READ_TIMEOUT_MS = 3_000
    private const val MAX_REQUEST = 8 * 1024

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    var port: Int = 0
        private set

    val running: Boolean get() = server != null

    /** Binds the port. Returns false when it is already up or cannot bind. */
    fun start(wanted: Int): Boolean {
        if (wanted <= 0 || wanted > 65535) return false
        if (server != null && port == wanted) return true
        stop()
        return runCatching {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(wanted))
            server = s
            port = wanted
            Thread(::serve, "cover-server").apply { isDaemon = true }.start()
            DiagnosticsLog.i(TAG, "Cover listener up on :$wanted")
            true
        }.getOrElse {
            DiagnosticsLog.w(TAG, "Cover listener could not bind :$wanted — ${it.message}")
            false
        }
    }

    fun stop() {
        val s = server ?: return
        server = null
        port = 0
        runCatching { s.close() }
        DiagnosticsLog.i(TAG, "Cover listener stopped")
    }

    private fun serve() {
        val s = server ?: return
        while (!s.isClosed) {
            val client = runCatching { s.accept() }.getOrNull() ?: break
            runCatching { answer(client) }
            runCatching { client.close() }
        }
    }

    private fun answer(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val input = client.getInputStream()
        drain(input)
        val out = client.getOutputStream()
        out.write(RESPONSE)
        out.flush()
    }

    /** Reads the request line and headers, then stops — nothing is parsed. */
    private fun drain(input: InputStream) {
        var step = 0
        var read = 0
        while (read < MAX_REQUEST) {
            val b = input.read()
            if (b < 0) return
            read++
            step = when {
                b == '\r'.code && step == 0 -> 1
                b == '\n'.code && step == 1 -> 2
                b == '\r'.code && step == 2 -> 3
                b == '\n'.code && step == 3 -> return
                else -> 0
            }
        }
    }

    private val HEAD = (
        "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Connection: close\r\n"
        ).toByteArray(Charsets.US_ASCII)

    private val PAGE = (
        "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>Workspace</title></head>\n" +
            "<body style=\"font-family:system-ui,sans-serif;margin:0;padding:3rem 1.5rem;" +
            "background:#f6f7f9;color:#1c1e21\">\n" +
            "<main style=\"max-width:42rem;margin:0 auto\">\n" +
            "<h1 style=\"font-size:1.4rem;margin:0 0 .5rem\">Service status</h1>\n" +
            "<p style=\"line-height:1.6;margin:0 0 1rem\">All systems operational. " +
            "This endpoint serves static content only.</p>\n" +
            "<p style=\"line-height:1.6;margin:0;color:#5a6068\">Last checked: just now.</p>\n" +
            "</main></body>\n</html>\n"
        ).toByteArray(Charsets.UTF_8)

    private val RESPONSE = run {
        val len = "Content-Length: ${PAGE.size}\r\n\r\n".toByteArray(Charsets.US_ASCII)
        ByteArray(HEAD.size + PAGE.size + len.size).also {
            System.arraycopy(HEAD, 0, it, 0, HEAD.size)
            System.arraycopy(PAGE, 0, it, HEAD.size, PAGE.size)
            System.arraycopy(len, 0, it, HEAD.size + PAGE.size, len.size)
        }
    }
}
