package com.soildtunnel.app.core

/**
 * Preferred loopback addresses for Tor mode. They are a STARTING POINT,
 * not a contract: tor usually binds them, but when something else on the
 * phone already holds one (Orbot uses the very same trio) [TorManager]
 * moves that listener to a free port instead of failing the whole connect.
 * They also stay clear of the WARP engine's own 127.0.0.1:1819.
 */
object TorDefaults {
    /** Tor's SOCKS listener: everything (TCP) goes through here. */
    const val SOCKS_PORT = 9050

    /** Tor's DNS resolver: UDP DNS from the TUN is forwarded here over TCP. */
    const val DNS_PORT = 9053

    /** Control port: exit-country switches and bootstrap progress. */
    const val CONTROL_PORT = 9051

    /**
     * Countries offered in the exit picker. Kept to places that reliably run
     * exit relays — picking a country with no exits would build circuits
     * forever and never connect.
     */
    val EXIT_COUNTRIES = listOf(
        "DE", "NL", "US", "SE", "CH", "FR", "GB", "CA",
        "AT", "FI", "RO", "IS", "NO", "CZ", "LU", "EE",
        "IE", "DK", "PL", "BG", "SG", "JP", "AU", "MX",
    )
}
