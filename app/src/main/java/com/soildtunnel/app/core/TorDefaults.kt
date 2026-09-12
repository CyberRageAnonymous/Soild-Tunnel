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

    // Snowflake entry. These are Tor's public defaults; if the front domain
    // ever stops working, only these three lines need updating.
    const val SNOWFLAKE_ICE = "stun:stun.stunprotocol.org:19302"
    const val SNOWFLAKE_BROKER = "https://snowflake-broker.torproject.net/"
    const val SNOWFLAKE_FRONT = "cdn.sstatic.net"

    /** The stock Snowflake bridge every Tor client ships with. */
    const val SNOWFLAKE_BRIDGE =
        "Bridge snowflake 192.0.2.3:1 2B280B23E1107BB62ABFC40DDCC8824814F80A72"

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
