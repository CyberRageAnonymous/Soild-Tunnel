package com.soildtunnel.app.core

object PsiphonDefaults {
    const val SOCKS_PORT = 1827
    const val FRONT_PORT = 1825
    const val ESTABLISH_TIMEOUT_MS = 190_000L

    fun supported(): Boolean =
        android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")
}
