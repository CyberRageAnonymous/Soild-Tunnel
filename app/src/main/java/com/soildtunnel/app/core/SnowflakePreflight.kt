package com.soildtunnel.app.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Snowflake dies silently when any piece of its path is blocked (DNS, the
 * front domain, or UDP as a whole) — tor then sits at 10% forever with no
 * hint. This runs those pieces one by one BEFORE tor starts, so a failure
 * names its cause instead of hanging.
 */
object SnowflakePreflight {

    data class Result(val ok: Boolean, val detail: String)

    suspend fun run(): Result = withContext(Dispatchers.IO) {
        val brokerHost = hostOf(TorDefaults.SNOWFLAKE_BROKER)
            ?: return@withContext Result(false, "broker address is wrong (${TorDefaults.SNOWFLAKE_BROKER})")

        // 1. DNS has to resolve the broker, the front and the STUN servers.
        val frontIps = resolve(TorDefaults.SNOWFLAKE_FRONT)
            ?: return@withContext Result(false, "cannot resolve ${TorDefaults.SNOWFLAKE_FRONT} (DNS blocked?)")
        resolve(brokerHost)
            ?: return@withContext Result(false, "cannot resolve $brokerHost (DNS blocked?)")
        val stuns = TorDefaults.SNOWFLAKE_ICE.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val stun = stuns.firstOrNull { resolve(hostOf(it) ?: "") != null }
            ?: return@withContext Result(false, "no STUN server resolves (DNS blocked?)")

        // 2. The front's 443 must answer with the front's own certificate —
        // this is the exact domain-fronted path snowflake rendezvous uses.
        val frontOk = frontIps.any { frontHandshake(it, brokerHost) }
        if (!frontOk) {
            return@withContext Result(false, "front ${TorDefaults.SNOWFLAKE_FRONT} unreachable")
        }

        // 3. Snowflake media is UDP. No UDP, no snowflake — ever.
        if (!stunResponds(stun)) {
            return@withContext Result(false, "UDP seems blocked, and snowflake needs UDP")
        }

        Result(true, "dns, front and udp all reachable")
    }

    private fun hostOf(urlOrHost: String): String? {
        val v = urlOrHost.trim()
        if (v.isEmpty()) return null
        val noScheme = v.substringAfter("://", v)
        return noScheme.substringBefore("/").substringBefore("@").substringAfter("@").ifBlank { null }
    }

    private suspend fun resolve(host: String): List<InetAddress>? {
        if (host.isEmpty()) return null
        return withTimeoutOrNull(5000L) {
            runCatching { InetAddress.getAllByName(host).toList() }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
    }

    /** TLS to a front IP asking for the front name; any HTTP answer counts. */
    private fun frontHandshake(ip: InetAddress, brokerHost: String): Boolean = runCatching {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        factory.createSocket().use { plain ->
            plain.connect(InetSocketAddress(ip, 443), 5000)
            val ssl = factory.createSocket(
                plain, TorDefaults.SNOWFLAKE_FRONT, 443, true,
            ) as javax.net.ssl.SSLSocket
            val params = ssl.sslParameters
            params.serverNames = listOf(SNIHostName(TorDefaults.SNOWFLAKE_FRONT))
            ssl.sslParameters = params
            ssl.soTimeout = 8000
            ssl.startHandshake()
            val out = ssl.outputStream
            val ins = ssl.inputStream
            out.write("GET / HTTP/1.1\r\nHost: $brokerHost\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            val status = ByteArray(12)
            var read = 0
            while (read < status.size) {
                val n = ins.read(status, read, status.size - read)
                if (n <= 0) break
                read += n
            }
            // Any HTTP status line back means the fronted path works end to end.
            read >= 12 && status[0] == 'H'.code.toByte() && status[1] == 'T'.code.toByte()
        }
    }.getOrDefault(false)

    /** Minimal STUN binding request; a matching reply proves UDP flows. */
    private fun stunResponds(stun: String): Boolean = runCatching {
        // stun:host:port
        val rest = stun.removePrefix("stun:")
        val host = rest.substringBeforeLast(":")
        val port = rest.substringAfterLast(":").toIntOrNull() ?: return@runCatching false
        val addr = withTimeoutOrNull(5000L) {
            runCatching { InetAddress.getByName(host) }.getOrNull()
        } ?: return@runCatching false
        DatagramSocket().use { sock ->
            sock.soTimeout = 3000
            val req = ByteArray(20)
            req[0] = 0x00
            req[1] = 0x01
            req[4] = 0x21
            req[5] = 0x12
            req[6] = 0xA4.toByte()
            req[7] = 0x42
            for (i in 8 until 20) req[i] = (i * 37).toByte()
            sock.send(DatagramPacket(req, req.size, InetSocketAddress(addr, port)))
            val buf = ByteArray(64)
            val resp = DatagramPacket(buf, buf.size)
            sock.receive(resp)
            resp.length >= 20 && buf[0] == 0x01.toByte() && buf[1] == 0x01.toByte()
        }
    }.getOrDefault(false)
}
