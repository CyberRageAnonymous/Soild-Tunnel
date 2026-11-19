package com.soildtunnel.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Interactive DPI check. The app sits outside its own TUN, so these probes see
 * exactly what the operator sees: a plain TCP connection, then a real TLS
 * handshake carrying a given SNI.
 *
 * A handshake that completes means the middlebox let that name through; a
 * reset or a stall while the plain connect succeeded is the classic SNI
 * filtering signature. Everything runs direct, never through the tunnel.
 */
object DpiProbe {

    private const val TAG = "dpiprobe"
    private const val TIMEOUT_MS = 6_000

    enum class Verdict { OPEN, RESET, TIMEOUT, REFUSED, UNREACHABLE }

    data class Sample(
        val label: String,
        val sni: String,
        val verdict: Verdict,
        val ms: Long,
    )

    data class Report(
        val samples: List<Sample>,
        val udpDnsOk: Boolean,
        val dpi: DpiClass,
        val elapsedMs: Long,
    )

    // The first entry is the control: a name that answers almost everywhere, so
    // a failure on everything else can be told apart from a broken uplink.
    private val TARGETS = listOf(
        "control" to "www.cloudflare.com",
        "microsoft" to "www.microsoft.com",
        "apple" to "www.apple.com",
        "wikipedia" to "www.wikipedia.org",
        "github" to "github.com",
    )

    suspend fun run(): Report = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val udp = async { udpDnsOk() }
        val probes = TARGETS.map { (label, sni) -> async { tlsProbe(label, sni) } }
        val samples = probes.awaitAll()
        val udpOk = udp.await()
        Report(samples, udpOk, classify(samples, udpOk), System.currentTimeMillis() - started)
    }

    private fun classify(samples: List<Sample>, udpOk: Boolean): DpiClass {
        val control = samples.firstOrNull()
        if (control == null || control.verdict != Verdict.OPEN) return DpiClass.HOSTILE
        val broken = samples.drop(1).filter { it.verdict == Verdict.RESET || it.verdict == Verdict.TIMEOUT }
        if (broken.isEmpty()) return if (udpOk) DpiClass.OPEN else DpiClass.UDP_THROTTLED
        return if (udpOk) DpiClass.SNI_FILTERING else DpiClass.HOSTILE
    }

    private fun tlsProbe(label: String, sni: String): Sample {
        val start = System.nanoTime()
        var verdict = Verdict.RESET
        try {
            Socket().use { raw ->
                raw.connect(InetSocketAddress(sni, 443), TIMEOUT_MS)
                raw.soTimeout = TIMEOUT_MS
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, sni, 443, true) as SSLSocket
                try {
                    ssl.startHandshake()
                    verdict = Verdict.OPEN
                } finally {
                    runCatching { ssl.close() }
                }
            }
        } catch (e: UnknownHostException) {
            verdict = Verdict.UNREACHABLE
        } catch (e: SocketTimeoutException) {
            verdict = Verdict.TIMEOUT
        } catch (e: ConnectException) {
            verdict = Verdict.REFUSED
        } catch (e: SSLException) {
            verdict = Verdict.RESET
        } catch (e: Exception) {
            verdict = Verdict.RESET
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        DiagnosticsLog.d(TAG, "$label ($sni) → $verdict in $ms ms")
        return Sample(label, sni, verdict, ms)
    }

    /** True when a real A query over UDP/53 still gets an answer here. */
    private fun udpDnsOk(): Boolean = runCatching {
        DatagramSocket().use { sock ->
            sock.soTimeout = TIMEOUT_MS
            val query = byteArrayOf(
                0x4D, 0x21,
                0x01, 0x00,
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
                'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
                3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
                0,
                0x00, 0x01,
                0x00, 0x01,
            )
            sock.send(DatagramPacket(query, query.size, InetAddress.getByName("1.1.1.1"), 53))
            val buf = ByteArray(512)
            sock.receive(DatagramPacket(buf, buf.size))
            buf[0] == 0x4D.toByte() && buf[1] == 0x21.toByte()
        }
    }.getOrElse {
        DiagnosticsLog.d(TAG, "udp53 probe → no answer (${it.message})")
        false
    }
}
