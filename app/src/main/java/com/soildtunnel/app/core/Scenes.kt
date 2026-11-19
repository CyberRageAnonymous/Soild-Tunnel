package com.soildtunnel.app.core

import com.soildtunnel.app.model.ChainMode
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.Noize
import com.soildtunnel.app.model.Protocol
import com.soildtunnel.app.model.ScanMode
import com.soildtunnel.app.model.TlsFp

/**
 * One-tap presets. A scene only rewrites the knobs that change how a
 * connection behaves — never the server, the account, the credentials or the
 * app lists — so applying one is always reversible by applying another.
 */
enum class Scene(val id: String) {

    /** Everything back to the way a fresh install behaves. */
    BALANCED("balanced") {
        override fun applyTo(p: ConnectionProfile) = p.copy(
            chainMode = ChainMode.OFF,
            tlsFp = TlsFp.OFF,
            noize = Noize.BALANCED,
            scanMode = ScanMode.BALANCED,
            fragment = false,
            ech = false,
            protocol = if (p.protocol == Protocol.TOR || p.protocol == Protocol.TUIC) p.protocol else Protocol.AUTO,
        )
    },

    /** For networks that inspect and reset handshakes. */
    STEALTH("stealth") {
        override fun applyTo(p: ConnectionProfile) = p.copy(
            chainMode = ChainMode.OFF,
            tlsFp = TlsFp.CHROME,
            noize = Noize.AGGRESSIVE,
            scanMode = ScanMode.STEALTH,
            fragment = true,
            ech = true,
            smartReconnect = true,
        )
    },

    /** Strip every extra layer off and search fast. */
    SPEED("speed") {
        override fun applyTo(p: ConnectionProfile) = p.copy(
            chainMode = ChainMode.OFF,
            tlsFp = TlsFp.OFF,
            noize = Noize.OFF,
            scanMode = ScanMode.TURBO,
            fragment = false,
            ech = false,
            quickReconnect = true,
        )
    },

    /** Tor entered through the engine, so the exit sits on top of the WARP hop. */
    DOUBLE_HOP("doublehop") {
        override fun applyTo(p: ConnectionProfile) = p.copy(
            protocol = Protocol.MASQUE,
            chainMode = ChainMode.TOR_OVER_WARP,
            tlsFp = TlsFp.CHROME,
            noize = Noize.BALANCED,
            scanMode = ScanMode.BALANCED,
            fragment = false,
            ech = false,
        )
    },

    /** Tor only, nothing else in the path. */
    TOR_ONLY("toronly") {
        override fun applyTo(p: ConnectionProfile) = p.copy(
            protocol = Protocol.TOR,
            chainMode = ChainMode.OFF,
            tlsFp = TlsFp.OFF,
            noize = Noize.OFF,
            fragment = false,
            ech = false,
        )
    };

    abstract fun applyTo(profile: ConnectionProfile): ConnectionProfile

    /** Applies the preset and remembers which one was last used. */
    fun apply(profile: ConnectionProfile): ConnectionProfile =
        applyTo(profile).copy(scene = id)

    companion object {
        fun byId(id: String?): Scene? = entries.firstOrNull { it.id == id }
    }
}
