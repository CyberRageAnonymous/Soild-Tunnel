package com.soildtunnel.psiphon

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PsiphonPluginService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var transport: PsiphonTransport? = null
    @Volatile private var starterReply: Messenger? = null

    private fun sendLog(msg: String) {
        Log.i(TAG, msg)
        try {
            starterReply?.send(Message.obtain(null, MSG_LOG).also {
                it.setData(Bundle().apply { putString(KEY_MESSAGE, msg) })
            })
        } catch (_: Exception) {}
    }

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_START -> {
                val data = msg.data
                starterReply = msg.replyTo
                val region = data.getString(KEY_REGION).orEmpty()
                val upstream = data.getString(KEY_UPSTREAM)
                scope.launch {
                    try {
                        stopTransportQuietly()
                        sendLog("bridge=${bridgeReport()}")
                        val t = PsiphonTransport(this@PsiphonPluginService, region, upstream, ::sendLog)
                        transport = t
                        val port = t.start()
                        replyToSafe()?.send(Message.obtain(null, MSG_STARTED).also {
                            it.setData(Bundle().apply { putInt(KEY_PORT, port) })
                        })
                    } catch (t: Throwable) {
                        val detail = "${t.javaClass.name}: ${t.message}"
                        Log.w(TAG, "Psiphon start failed: $detail")
                        sendLog("FATAL: $detail")
                        stopTransportQuietly()
                        try {
                            replyToSafe()?.send(Message.obtain(null, MSG_ERROR).also { m ->
                                m.setData(Bundle().apply { putString(KEY_MESSAGE, detail) })
                            })
                        } catch (_: Exception) {}
                        throw t
                    }
                }
                true
            }
            MSG_STOP -> {
                scope.launch { stopTransportQuietly() }
                try { stopSelf() } catch (_: Exception) {}
                true
            }
            else -> false
        }
    }

    private fun replyToSafe(): Messenger? = starterReply

    private fun bridgeReport(): String {
        val loader = PsiphonPluginService::class.java.classLoader
        val go = runCatching { Class.forName("go.Seq", false, loader) }
        return "go.Seq dex-present=${go.isSuccess}, arch=${android.os.Build.SUPPORTED_ABIS.firstOrNull()}"
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        stopTransportQuietly()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopTransportQuietly() {
        try { transport?.stop() } catch (_: Exception) {}
        transport = null
        try { File(filesDir, "psiphon-front-stop").delete() } catch (_: Exception) {}
    }

    companion object {
        const val MSG_START = 1
        const val MSG_STOP = 2
        const val MSG_STARTED = 3
        const val MSG_ERROR = 4
        const val MSG_LOG = 5
        const val KEY_REGION = "region"
        const val KEY_UPSTREAM = "upstream"
        const val KEY_PORT = "port"
        const val KEY_MESSAGE = "message"
        private const val TAG = "Psiphon"
    }
}
