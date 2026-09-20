package com.soildtunnel.app.transport

import android.content.Context
import com.soildtunnel.app.core.TunnelConfig
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.NetworkBackend

interface ExternalTransport {
    suspend fun start(): Int
    fun isAlive(): Boolean
    fun stop()
}

object ExternalTransportFactory {
    fun create(context: Context, profile: ConnectionProfile): ExternalTransport? {
        if (profile.networkBackend != NetworkBackend.SOILDTUNNEL_PSIPHON) return null
        if (profile.protocol == com.soildtunnel.app.model.Protocol.TOR) return null
        val upstreamPort = TunnelConfig.SOCKS_PORT
        val upstream = "socks5://${TunnelConfig.SOCKS_HOST}:$upstreamPort"
        return PsiphonTransport(context, profile.psiphonExitRegion, upstream)
    }
}
