package com.soildtunnel.app.core

import com.soildtunnel.app.model.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * A handful of checks that answer one question: is anything still reaching
 * the network without going through the tunnel?
 *
 * Every probe runs from this process, which Android keeps outside its own
 * VPN, so a "direct" socket here is a genuine bypass — exactly the path a
 * leak would take.
 */
object LeakLab {

    private const val TAG = "leaklab"
    private const val TIMEOUT_MS = 8_000

    enum class Status { PASS, WARN, FAIL }

    /**
     * [id] picks the wording in the UI, [args] fills its placeholders. The
     * strings stay in resources so both languages read the same.
     */
    data class Check(
        val id: String,
        val status: Status,
        val args: List<String> = emptyList(),
    )

    suspend fun run(profile: ConnectionProfile, socksPort: Int): List<Check> = withContext(Dispatchers.IO) {
        coroutineScope {
            val exit = async { exitIpCheck(socksPort) }
            val remoteDns = async { remoteDnsCheck(socksPort) }
            val hijack = async { hijackCheck(socksPort) }
            val v6 = async { ipv6Check(profile) }
            listOf(exit.await(), remoteDns.await(), hijack.await(), v6.await())
        }
    }

    /** The IP a site sees must differ from the one the operator sees. */
    private fun exitIpCheck(socksPort: Int): Check {
        val direct = NetProbe.fetchIpInfoDirect(TIMEOUT_MS)
        val tunnelled = NetProbe.fetchIpInfoViaSocks(TunnelConfig.SOCKS_HOST, socksPort, TIMEOUT_MS)
        return when {
            direct == null || tunnelled == null -> Check("exit_unknown", Status.WARN)
            direct.ip == tunnelled.ip ->
                Check("exit_same", Status.FAIL, listOf(tunnelled.ip))
            else ->
                Check("exit_ok", Status.PASS, listOf(tunnelled.ip, direct.ip))
        }
    }

    /** Names have to be resolved by the tunnel, not by the local resolver. */
    private fun remoteDnsCheck(socksPort: Int): Check {
        val ok = runCatching {
            socksConnect(TunnelConfig.SOCKS_HOST, socksPort, "www.wikipedia.org", 80, TIMEOUT_MS)
        }.getOrElse {
            DiagnosticsLog.d(TAG, "remote dns check failed: ${it.message}")
            false
        }
        return Check(if (ok) "dns_ok" else "dns_fail", if (ok) Status.PASS else Status.FAIL)
    }

    /**
     * A name that does not exist must not resolve. Some networks answer every
     * lookup with a redirect page instead, which is how search terms end up
     * in someone else's logs.
     */
    private fun hijackCheck(socksPort: Int): Check {
        val bogus = "soildtunnel-${Random.nextInt(100000, 999999)}.example"
        val resolved = runCatching {
            socksConnect(TunnelConfig.SOCKS_HOST, socksPort, bogus, 80, TIMEOUT_MS)
        }.getOrDefault(false)
        return if (resolved) {
            Check("hijack_warn", Status.WARN, listOf(bogus))
        } else {
            Check("hijack_ok", Status.PASS)
        }
    }

    /**
     * With leak protection off and an IPv6 uplink, apps inside the TUN have no
     * v6 route to fall back on and their v6 traffic can take the physical path.
     */
    private fun ipv6Check(profile: ConnectionProfile): Check {
        val reachable = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress("2606:4700:4700::1111", 443), TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)

        val wantsV6 = profile.ipVersion != com.soildtunnel.app.model.IpVersion.V4
        return when {
            !reachable -> Check("v6_none", Status.PASS)
            !wantsV6 -> Check("v6_v4", Status.PASS)
            profile.ipv6LeakProtection -> Check("v6_ok", Status.PASS)
            else -> Check("v6_risk", Status.FAIL)
        }
    }

    /**
     * Minimal SOCKS5 client: greeting, optional no-auth, then CONNECT with the
     * destination sent as a DOMAIN so the tunnel does the resolving.
     */
    private fun socksConnect(
        host: String,
        port: Int,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int,
    ): Boolean {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            val out = s.getOutputStream()
            val input = s.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            if (input.read() != 0x05 || input.read() != 0x00) return false

            val name = targetHost.toByteArray(StandardCharsets.US_ASCII)
            val req = ByteArray(7 + name.size)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = name.size.toByte()
            System.arraycopy(name, 0, req, 5, name.size)
            req[req.size - 2] = (targetPort shr 8).toByte()
            req[req.size - 1] = targetPort.toByte()
            out.write(req)
            out.flush()

            val head = ByteArray(4)
            if (!readFully(input, head) || head[1] != 0x00) return false
            val skip = when (head[3].toInt() and 0xFF) {
                0x01 -> 4
                0x03 -> (input.read().coerceAtLeast(0)) + 2
                0x04 -> 16
                else -> return false
            }
            val tail = ByteArray(skip)
            return readFully(input, tail)
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = try {
                input.read(buf, off, buf.size - off)
            } catch (e: IOException) {
                return false
            }
            if (n < 0) return false
            off += n
        }
        return true
    }
}
