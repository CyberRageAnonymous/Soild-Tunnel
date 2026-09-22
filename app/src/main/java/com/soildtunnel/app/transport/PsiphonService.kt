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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PsiphonService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var transport: PsiphonTransport? = null
    @Volatile private var frontPort: Int = -1
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
                val replyTo = msg.replyTo
                starterReply = replyTo
                val region = data.getString(KEY_REGION).orEmpty()
                val upstream = data.getString(KEY_UPSTREAM)
                scope.launch {
                    try {
                        stopTransportQuietly()
                        sendLog("native libs: " + nativeLibReport())
                        sendLog("bridge classes: " + bridgeClassReport())
                        try {
                            System.loadLibrary("psiphoncore")
                        } catch (e: UnsatisfiedLinkError) {
                            throw IllegalStateException("psiphon native lib missing: ${e.message}")
                        }
                        sendLog("native lib loaded")
                        val t = PsiphonTransport(this@PsiphonService, region, upstream, ::sendLog)
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

    private fun nativeLibReport(): String = runCatching {
        val dir = File(applicationInfo.nativeLibraryDir)
        dir.listFiles()
            ?.filter { it.name.contains("psiphon") || it.name.contains("gojni") }
            ?.joinToString(", ") { "${it.name}(${(it.length() / 1024 / 1024)}MB)" }
            ?: "empty"
    }.getOrDefault("unknown")

    private fun bridgeClassReport(): String {
        val pgBare = runCatching { Class.forName("pg.Seq", false, PsiphonService::class.java.classLoader); "present" }.getOrDefault("absent")
        val go = runCatching { Class.forName("go.Seq"); "go.Seq present" }.getOrDefault("go.Seq absent")
        val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val libDir = applicationInfo.nativeLibraryDir
        return "pg.Seq class=$pgBare, $go, arch=$arch, libDir=$libDir"
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
