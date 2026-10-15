package com.soildtunnel.app.core

object PsiphonDefaults {
    const val SOCKS_PORT = 1820
    const val HTTP_PORT = 1821
    const val DATA_DIR = "psiphon-data"
    const val ESTABLISH_TIMEOUT_MS = 120_000L

    /**
     * Partner credentials issued by Psiphon Inc. for this distribution
     * channel. Empty until the project holds a partnership: the manager
     * refuses to start with a clear error instead of failing obscurely
     * inside the tunnel core.
     */
    const val PROPAGATION_CHANNEL_ID = ""
    const val SPONSOR_ID = ""
    const val REMOTE_SERVER_LIST_URLS_JSON = "[]"
    const val REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY = ""

    fun hasCredentials(): Boolean =
        PROPAGATION_CHANNEL_ID.isNotBlank() && SPONSOR_ID.isNotBlank()

    val EXIT_COUNTRIES = listOf(
        "US", "GB", "DE", "NL", "FR", "CA", "CH", "SE",
        "NO", "FI", "DK", "IE", "BE", "AT", "ES", "IT",
        "PL", "CZ", "RO", "SG", "JP", "AU", "BR", "IN",
        "MX", "TR",
    )
}
