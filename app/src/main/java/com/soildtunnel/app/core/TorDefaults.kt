package com.soildtunnel.app.core

/**
 * Fixed addresses and bundled values for Tor mode. The ports only exist
 * inside this phone (tor listens on loopback), so they never clash with
 * anything on the network — they just have to differ from the WARP
 * engine's own 127.0.0.1:1819.
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
