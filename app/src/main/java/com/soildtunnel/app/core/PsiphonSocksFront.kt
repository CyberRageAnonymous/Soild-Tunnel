package com.soildtunnel.app.core

import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PsiphonSocksFront {
    private var server: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var psiphonPort: Int = PsiphonDefaults.SOCKS_PORT

    fun start(frontPort: Int, psiphonPort: Int) {
        this.psiphonPort = psiphonPort
        server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", frontPort))
        }
        running.set(true)
        thread(isDaemon = true, name = "psiphon-front") {
            while (running.get()) {
                try {
                    val client = server?.accept() ?: break
                    thread(isDaemon = true) { handle(client) }
                } catch (_: Exception) { if (!running.get()) break }
            }
        }
    }

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 30000
            val input = client.getInputStream()
            val out = client.getOutputStream()
            if (input.read() != 0x05) { client.close(); return }
            input.read()
            input.read()
            out.write(byteArrayOf(0x05, 0x00))
            out.flush()
            val cmd = input.read()
            input.read()
            val atyp = input.read()
            val host: String
            var port = 0
            when (atyp) {
                0x01 -> {
                    val ip = ByteArray(4)
                    input.read(ip)
                    host = ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
                    port = (input.read() shl 8) or input.read()
                }
                0x03 -> {
                    val len = input.read()
                    val hb = ByteArray(len)
                    input.read(hb)
                    host = String(hb)
                    port = (input.read() shl 8) or input.read()
                }
                0x04 -> {
                    val ip = ByteArray(16)
                    input.read(ip)
                    host = InetAddress.getByAddress(ip).hostAddress ?: return
                    port = (input.read() shl 8) or input.read()
                }
                else -> { client.close(); return }
            }
            if (cmd == 0x03) {
                serveUdpAssociate(client, out)
                return
            }
            upstream = openPsiphonStream(host, port, atyp) ?: throw Exception("psiphon refused")
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
            val uIn = upstream.getInputStream()
            val uOut = upstream.getOutputStream()
            val t1 = thread(isDaemon = true) { copy(input, uOut) }
            val t2 = thread(isDaemon = true) { copy(uIn, out) }
            t1.join()
            t2.join()
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun openPsiphonStream(host: String, port: Int, atyp: Int): Socket? = runCatching {
        val s = Socket()
        s.connect(InetSocketAddress("127.0.0.1", psiphonPort), 10000)
        s.soTimeout = 30000
        val ins = s.getInputStream()
        val out = s.getOutputStream()
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        if (ins.read() != 0x05 || ins.read() != 0x00) throw Exception("no auth")
        val req = when (atyp) {
            0x01 -> byteArrayOf(0x05, 0x01, 0x00, 0x01) +
                host.split(".").map { it.toInt().toByte() }.toByteArray() +
                byteArrayOf((port shr 8).toByte(), port.toByte())
            0x03 -> byteArrayOf(0x05, 0x01, 0x00, 0x03, host.length.toByte()) +
                host.toByteArray() +
                byteArrayOf((port shr 8).toByte(), port.toByte())
            else -> byteArrayOf(0x05, 0x01, 0x00, 0x04) +
                InetAddress.getByName(host).address +
                byteArrayOf((port shr 8).toByte(), port.toByte())
        }
        out.write(req)
        out.flush()
        if (ins.read() != 0x05 || ins.read() != 0x00) throw Exception("refused")
        ins.read()
        when (ins.read()) {
            0x01 -> { ins.read(ByteArray(4)); ins.read(); ins.read() }
            0x03 -> { val l = ins.read(); ins.read(ByteArray(l)); ins.read(); ins.read() }
            0x04 -> { ins.read(ByteArray(16)); ins.read(); ins.read() }
            else -> throw Exception("bad reply")
        }
        s
    }.getOrNull()

    private fun serveUdpAssociate(client: Socket, out: OutputStream) {
        val relay = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        relay.soTimeout = 30000
        try {
            val port = relay.localPort
            out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()))
            out.flush()
            val buf = ByteArray(65535)
            while (running.get() && !client.isClosed) {
                val pkt = try {
                    DatagramPacket(buf, buf.size).also { relay.receive(it) }
                } catch (_: Exception) { break }
                val data = pkt.data.copyOf(pkt.length)
                if (data.size < 10 || data[2] != 0.toByte()) continue
                var off = 4
                var dstPort = 0
                when (data[3].toInt() and 0xFF) {
                    0x01 -> { if (data.size < 10) continue; off = 8 }
                    0x03 -> { off += 1 + (data[off].toInt() and 0xFF) }
                    0x04 -> { if (data.size < 22) continue; off = 20 }
                    else -> continue
                }
                if (data.size < off + 2) continue
                dstPort = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
                off += 2
                if (dstPort != 53) continue
                val answer = dnsOverTcp(data.copyOfRange(off, data.size)) ?: continue
                val resp = data.copyOfRange(0, off) + answer
                try { relay.send(DatagramPacket(resp, resp.size, pkt.socketAddress)) } catch (_: Exception) { break }
            }
        } catch (_: Exception) {
        } finally {
            try { relay.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun dnsOverTcp(query: ByteArray): ByteArray? = runCatching {
        val s = Socket()
        s.connect(InetSocketAddress("127.0.0.1", psiphonPort), 10000)
        s.soTimeout = 15000
        val ins = s.getInputStream()
        val out = s.getOutputStream()
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        if (ins.read() != 0x05 || ins.read() != 0x00) throw Exception("no auth")
        out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0, 53))
        out.flush()
        if (ins.read() != 0x05 || ins.read() != 0x00) throw Exception("dns refused")
        ins.read()
        when (ins.read()) {
            0x01 -> { ins.read(ByteArray(4)); ins.read(); ins.read() }
            0x03 -> { val l = ins.read(); ins.read(ByteArray(l)); ins.read(); ins.read() }
            0x04 -> { ins.read(ByteArray(16)); ins.read(); ins.read() }
        }
        out.write(byteArrayOf((query.size shr 8).toByte(), query.size.toByte()))
        out.write(query)
        out.flush()
        val len = (ins.read() shl 8) or ins.read()
        if (len <= 0 || len > 65535) throw Exception("bad len")
        val ans = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = ins.read(ans, got, len - got)
            if (n <= 0) throw Exception("short")
            got += n
        }
        s.close()
        ans
    }.getOrNull()

    private fun copy(input: InputStream, out: OutputStream) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                out.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        server = null
    }
}
