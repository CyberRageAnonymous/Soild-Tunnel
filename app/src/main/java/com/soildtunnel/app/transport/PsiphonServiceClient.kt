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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

object PsiphonServiceClient {
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
            pending?.completeExceptionally(IllegalStateException("psiphon process died"))
            pending = null
        }
    }

    private fun ensureBound(context: Context) {
        if (bound && peer != null) return
        val intent = Intent(context.applicationContext, PsiphonService::class.java)
        context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        val deadline = System.currentTimeMillis() + 15_000
        synchronized(this) {
            while (peer == null && System.currentTimeMillis() < deadline) {
                (this as java.lang.Object).wait(500)
            }
        }
        if (peer == null) throw IllegalStateException("Psiphon service did not bind")
        bound = true
        boundContext = context.applicationContext
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
                PsiphonService.MSG_LOG -> {
                    com.soildtunnel.app.core.DiagnosticsLog.i(
                        "Psiphon",
                        msg.data.getString(PsiphonService.KEY_MESSAGE).orEmpty(),
                    )
                    true
                }
                PsiphonService.MSG_STARTED -> {
                    if (!result.isCompleted) result.complete(msg.data.getInt(PsiphonService.KEY_PORT, -1))
                    true
                }
                PsiphonService.MSG_ERROR -> {
                    if (!result.isCompleted) {
                        result.completeExceptionally(
                            IllegalStateException(msg.data.getString(PsiphonService.KEY_MESSAGE) ?: "psiphon failed"),
                        )
                    }
                    true
                }
                else -> false
            }
        })
        val p = peer ?: throw IllegalStateException("Psiphon service not bound")
        p.send(Message.obtain(null, PsiphonService.MSG_START).also {
            it.replyTo = reply
            it.setData(Bundle().apply {
                putString(PsiphonService.KEY_REGION, region)
                if (!upstream.isNullOrBlank()) putString(PsiphonService.KEY_UPSTREAM, upstream)
            })
        })
        return withTimeout(timeoutMs) { result.await().also { if (it <= 0) throw IllegalStateException("bad psiphon port") } }
    }

    fun stop(context: Context) {
        try { peer?.send(Message.obtain(null, PsiphonService.MSG_STOP)) } catch (_: Exception) {}
        peer = null
        bound = false
        try {
            (boundContext ?: context.applicationContext).unbindService(connection)
        } catch (_: Exception) {}
        boundContext = null
        try { context.applicationContext.stopService(Intent(context.applicationContext, PsiphonService::class.java)) } catch (_: Exception) {}
    }

    fun isAlive(): Boolean = bound && peer != null
}
