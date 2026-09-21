package com.soildtunnel.app.transport

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PsiphonService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var transport: PsiphonTransport? = null
    @Volatile private var frontPort: Int = -1

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_START -> {
                val data = msg.data
                val replyTo = msg.replyTo
                val region = data.getString(KEY_REGION).orEmpty()
                val upstream = data.getString(KEY_UPSTREAM)
                scope.launch {
                    try {
                        stopTransportQuietly()
                        val t = PsiphonTransport(this@PsiphonService, region, upstream)
                        transport = t
                        frontPort = t.start()
                        replyTo?.send(Message.obtain(null, MSG_STARTED).also {
                            it.setData(Bundle().apply { putInt(KEY_PORT, frontPort) })
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "Psiphon start failed: ${e.message}")
                        stopTransportQuietly()
                        try {
                            replyTo?.send(Message.obtain(null, MSG_ERROR).also {
                                it.setData(Bundle().apply { putString(KEY_MESSAGE, e.message ?: "start failed") })
                            })
                        } catch (_: Exception) {}
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
        frontPort = -1
    }

    companion object {
        const val MSG_START = 1
        const val MSG_STOP = 2
        const val MSG_STARTED = 3
        const val MSG_ERROR = 4
        const val KEY_REGION = "region"
        const val KEY_UPSTREAM = "upstream"
        const val KEY_PORT = "port"
        const val KEY_MESSAGE = "message"
        private const val TAG = "Psiphon"
    }
}
