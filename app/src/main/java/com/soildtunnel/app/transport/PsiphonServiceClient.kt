package com.soildtunnel.app.transport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.soildtunnel.app.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

object PsiphonServiceClient {
    const val PLUGIN_PACKAGE = "com.soildtunnel.psiphon"
    const val PLUGIN_SERVICE = "com.soildtunnel.psiphon.PsiphonPluginService"

    const val MSG_START = 1
    const val MSG_STOP = 2
    const val MSG_STARTED = 3
    const val MSG_ERROR = 4
    const val MSG_LOG = 5
    const val KEY_REGION = "region"
    const val KEY_UPSTREAM = "upstream"
    const val KEY_PORT = "port"
    const val KEY_MESSAGE = "message"

    @Volatile private var peer: Messenger? = null
    @Volatile private var bound = false
    private var boundContext: Context? = null

    @Volatile private var pending: CompletableDeferred<Int>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            peer = Messenger(service)
            synchronized(this@PsiphonServiceClient) {
                (this@PsiphonServiceClient as java.lang.Object).notifyAll()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            peer = null
            bound = false
            pending?.completeExceptionally(IllegalStateException("psiphon plugin died"))
            pending = null
        }
    }

    private fun pluginIntent(): Intent =
        Intent().setComponent(ComponentName(PLUGIN_PACKAGE, PLUGIN_SERVICE))

    private fun ensureBound(context: Context) {
        if (bound && peer != null) return
        val app = context.applicationContext
        val ok = try {
            app.bindService(pluginIntent(), connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
            false
        }
        if (!ok) throw IllegalStateException(app.getString(R.string.psiphon_need_plugin))
        val deadline = System.currentTimeMillis() + 15_000
        synchronized(this) {
            while (peer == null && System.currentTimeMillis() < deadline) {
                (this as java.lang.Object).wait(500)
            }
        }
        if (peer == null) throw IllegalStateException(app.getString(R.string.psiphon_need_plugin))
        bound = true
        boundContext = app
    }

    suspend fun start(context: Context, region: String, upstream: String?, timeoutMs: Long = 190_000): Int {
        ensureBound(context)
        val result = CompletableDeferred<Int>()
        pending = result
        try {
            return startInner(context, region, upstream, result, timeoutMs)
        } finally {
            if (pending === result) pending = null
        }
    }

    private suspend fun startInner(
        context: Context,
        region: String,
        upstream: String?,
        result: CompletableDeferred<Int>,
        timeoutMs: Long,
    ): Int {
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                MSG_LOG -> {
                    com.soildtunnel.app.core.DiagnosticsLog.i(
                        "Psiphon",
                        msg.data.getString(KEY_MESSAGE).orEmpty(),
                    )
                    true
                }
                MSG_STARTED -> {
                    if (!result.isCompleted) result.complete(msg.data.getInt(KEY_PORT, -1))
                    true
                }
                MSG_ERROR -> {
                    if (!result.isCompleted) {
                        result.completeExceptionally(
                            IllegalStateException(msg.data.getString(KEY_MESSAGE) ?: "psiphon failed"),
                        )
                    }
                    true
                }
                else -> false
            }
        })
        val p = peer ?: throw IllegalStateException("Psiphon plugin not bound")
        p.send(Message.obtain(null, MSG_START).also {
            it.replyTo = reply
            it.setData(Bundle().apply {
                putString(KEY_REGION, region)
                if (!upstream.isNullOrBlank()) putString(KEY_UPSTREAM, upstream)
            })
        })
        return withTimeout(timeoutMs) { result.await().also { if (it <= 0) throw IllegalStateException("bad psiphon port") } }
    }

    fun stop(context: Context) {
        try { peer?.send(Message.obtain(null, MSG_STOP)) } catch (_: Exception) {}
        peer = null
        bound = false
        try {
            (boundContext ?: context.applicationContext).unbindService(connection)
        } catch (_: Exception) {}
        boundContext = null
    }

    fun isAlive(): Boolean = bound && peer != null
}
