package com.soildtunnel.app.core

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

object PsiphonManager {
    private const val TAG = "psiphon"
    @Volatile private var process: Process? = null
    @Volatile private var running = false

    fun isAlive(): Boolean = running && process?.isAlive == true

    suspend fun start(context: Context, region: String, upstream: String?): Int =
        withContext(Dispatchers.IO) {
            stop()
            if (!PsiphonDefaults.supported()) throw IllegalStateException("arm64 required")
            runCatching {
                context.assets.open("server_entries.txt").use { ins ->
                    File(context.filesDir, "server-list.txt").outputStream().use { out ->
                        ins.copyTo(out)
                    }
                }
            }
            val bin = File(context.applicationInfo.nativeLibraryDir, "libpsiphon.so")
            if (!bin.exists()) throw IllegalStateException("psiphon core missing")
            runCatching { bin.setExecutable(true) }
            val dir = File(context.filesDir, "psiphon-data").apply { mkdirs() }
            val wanted = region.trim().uppercase()
            val attempts = if (wanted.isEmpty()) listOf("") else listOf(wanted, "")
            var lastError: Exception? = null
            for ((index, egress) in attempts.withIndex()) {
                if (index > 0) {
                    DiagnosticsLog.w(TAG, "Retrying with automatic exit and fresh datastore.")
                    stopProcess()
                    File(dir, "store").deleteRecursively()
                    File(dir, "store").mkdirs()
                    awaitClosed("127.0.0.1", PsiphonDefaults.SOCKS_PORT, 5_000)
                }
                try {
                    return@withContext establish(context, dir, bin, egress, upstream)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    lastError = e
                    DiagnosticsLog.w(TAG, "Psiphon attempt failed: ${e.message}")
                    stopProcess()
                }
            }
            throw lastError ?: IllegalStateException("Psiphon did not establish")
        }

    private suspend fun establish(
        context: Context,
        dir: File,
        bin: File,
        egress: String,
        upstream: String?,
    ): Int = withContext(Dispatchers.IO) {
        File(dir, "store").apply { mkdirs() }
        File(dir, "config.json").writeText(buildConfig(context, egress, upstream))
        DiagnosticsLog.i(TAG, "Starting psiphon core (exit=${egress.ifEmpty { "auto" }}).")
        val proc = ProcessBuilder(bin.absolutePath, "-config", File(dir, "config.json").absolutePath,
            "-serverList", File(context.filesDir, "server-list.txt").absolutePath,
            "-dataRootDirectory", File(dir, "store").absolutePath,
            "-notices", File(dir, "notices.log").absolutePath)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = context.filesDir.absolutePath
                environment()["TMPDIR"] = context.cacheDir.absolutePath
            }
            .start()
        process = proc
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { DiagnosticsLog.d(TAG, it.take(300)) }
                }
            } catch (_: Exception) {
            }
        }, "psiphon-log").apply { isDaemon = true }.start()
        val noticesFile = File(dir, "notices.log")
        var noticeOffset = 0L
        running = true
        try {
            withTimeout(PsiphonDefaults.ESTABLISH_TIMEOUT_MS) {
                while (true) {
                    if (socksConnectWorks()) return@withTimeout
                    noticeOffset = tailNotices(noticesFile, noticeOffset)
                    kotlinx.coroutines.delay(2_000)
                }
            }
        } catch (e: TimeoutCancellationException) {
            tailNotices(noticesFile, 0L, 60)
            throw IllegalStateException("Psiphon found no usable server")
        }
        tailNotices(noticesFile, noticeOffset, 20)
        PsiphonDefaults.SOCKS_PORT
    }

    private fun tailNotices(file: File, offset: Long, maxLines: Int = Int.MAX_VALUE): Long {
        return try {
            if (!file.exists()) return offset
            val text = file.readText()
            if (text.length <= offset) return offset
            val fresh = text.substring(offset.coerceAtMost(text.length.toLong()).toInt())
            val lines = fresh.lines().filter { it.isNotBlank() }.takeLast(maxLines)
            lines.forEach { DiagnosticsLog.d(TAG, it.take(300)) }
            text.length.toLong()
        } catch (_: Exception) {
            offset
        }
    }

    private fun socksConnectWorks(): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", PsiphonDefaults.SOCKS_PORT), 5_000)
            s.soTimeout = 10_000
            val ins = s.getInputStream()
            val out = s.getOutputStream()
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            if (ins.read() != 0x05 || ins.read() != 0x00) return false
            out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0, 53))
            out.flush()
            if (ins.read() != 0x05 || ins.read() != 0x00) return false
            true
        }
    }.getOrDefault(false)

    private suspend fun awaitClosed(host: String, port: Int, timeoutMs: Long) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val closed = runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), 300); false }
            }.getOrDefault(true)
            if (closed) return
            kotlinx.coroutines.delay(300)
        }
    }

    private fun buildConfig(context: Context, egress: String, upstream: String?): String =
        JSONObject().apply {
            put("PropagationChannelId", "FFFFFFFFFFFFFFFF")
            put("SponsorId", "1111111111111111")
            if (egress.isNotEmpty()) put("EgressRegion", egress)
            put("EstablishTunnelTimeoutSeconds", 180)
            put("ClientVersion", "127")
            put("LocalSocksProxyPort", PsiphonDefaults.SOCKS_PORT)
            put("DisableLocalHTTPProxy", true)
            put("RemoteServerListSignaturePublicKey", REMOTE_SERVER_LIST_PUBLIC_KEY)
            put("ServerEntrySignaturePublicKey", "sHuUVTWaRyh5pZwy4UguSgkwmBe0EHtJJkoF5WrxmvA=")
            put("ExchangeObfuscationKey", "DpXzloJk1Hw6aSzmKKky0xcahsEHubch81Mi6K0XMlU=")
            put("EmitBytesTransferred", true)
            put("EmitDiagnosticNotices", true)
            put("DeviceRegion", "IR")
            put("ConnectionWorkerPoolSize", 12)
            upstream?.takeIf { it.isNotBlank() }?.let { put("UpstreamProxyUrl", it) }
        }.toString()

    fun stop() {
        running = false
        stopProcess()
    }

    private fun stopProcess() {
        val proc = process
        process = null
        runCatching {
            proc?.destroy()
            proc?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            proc?.destroyForcibly()
        }
    }

    const val REMOTE_SERVER_LIST_PUBLIC_KEY =
        "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="
}
