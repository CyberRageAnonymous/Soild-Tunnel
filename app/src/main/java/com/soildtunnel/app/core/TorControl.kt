package com.soildtunnel.app.core

import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tiny Tor control-port client. Speaks just enough of the protocol for what
 * the app needs: cookie auth, bootstrap progress, exit-country switches and
 * new-identity signals. No extra dependency for four commands.
 */
class TorControl(
    private val host: String = "127.0.0.1",
    private val port: Int = TorDefaults.CONTROL_PORT,
) : Closeable {
    private val socket = Socket()
    private val input by lazy { socket.getInputStream().bufferedReader(Charsets.US_ASCII) }
    private val output by lazy { socket.getOutputStream() }

    fun connect(timeoutMs: Int = 5000) {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = 10000
    }

    /** Cookie auth: the cookie file lives in tor's data dir, same app sandbox. */
    fun authenticate(cookie: File): Boolean {
        if (!cookie.exists()) return false
        val hex = cookie.readBytes().joinToString("") { "%02x".format(it) }
        return singleLine("AUTHENTICATE $hex").startsWith("250")
    }

    /** Bootstrap percent + one-line summary, or null when tor won't say yet. */
    fun bootstrap(): Pair<Int, String>? {
        val flat = command("GETINFO status/bootstrap-phase").joinToString(" ")
        val progress = Regex("PROGRESS=(\\d+)").find(flat)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val summary = Regex("SUMMARY=\"([^\"]*)\"").find(flat)
            ?.groupValues?.get(1).orEmpty()
        return progress to summary
    }

    /**
     * Pins the exit country ([countryCode], blank = back to automatic) and
     * cuts new circuits so the switch actually takes effect. Returns true
     * when tor accepted the whole thing. Takes a few seconds on the network
     * side — tor still has to build the new circuits.
     */
    fun setExit(countryCode: String): Boolean {
        val cc = countryCode.trim().uppercase()
        val ok = if (cc.matches(Regex("^[A-Z]{2}$"))) {
            singleLine("SETCONF ExitNodes={$cc}").startsWith("250") &&
                singleLine("SETCONF StrictNodes=1").startsWith("250")
        } else {
            singleLine("SETCONF ExitNodes").startsWith("250") &&
                singleLine("SETCONF StrictNodes").startsWith("250")
        }
        if (!ok) return false
        return singleLine("SIGNAL NEWNYM").startsWith("250")
    }

    private fun singleLine(cmd: String): String = command(cmd).lastOrNull().orEmpty()

    private fun command(cmd: String): List<String> {
        output.write((cmd + "\r\n").toByteArray(Charsets.US_ASCII))
        output.flush()
        val lines = mutableListOf<String>()
        while (true) {
            val line = input.readLine() ?: break
            // "250+..." opens a data block that runs until a lone dot.
            if (line.length >= 4 && line[3] == '+' && line.take(3).all { it.isDigit() }) {
                while (true) {
                    val data = input.readLine() ?: break
                    if (data == ".") break
                    lines += data
                }
                continue
            }
            lines += line
            // "250 ..." (with a space) is the final line of the reply.
            if (line.length >= 4 && line[3] == ' ' && line.take(3).all { it.isDigit() }) break
        }
        return lines
    }

    override fun close() {
        runCatching { socket.close() }
    }
}
