package com.soildtunnel.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.soildtunnel.app.model.ConnectionProfile

/**
 * Per-network app firewall. The user picks separate app lists for Wi-Fi,
 * mobile data and roaming; this decides which list is live right now.
 *
 * The verdict is recomputed on demand instead of being cached, because the
 * uplink can change while the tunnel is already up (Wi-Fi handover, roaming).
 */
object NetworkPolicy {

    /** Uplink the phone is currently riding. */
    enum class Kind { WIFI, MOBILE, ROAMING, OTHER }

    fun currentKind(context: Context): Kind = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching Kind.OTHER
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return@runCatching Kind.OTHER
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Kind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Kind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                    Kind.MOBILE
                } else {
                    Kind.ROAMING
                }
            else -> Kind.OTHER
        }
    }.getOrDefault(Kind.OTHER)

    /**
     * Apps that should be dark on the uplink we are on. Empty when the
     * firewall is off, which is the default.
     */
    fun blockedHere(context: Context, profile: ConnectionProfile): Set<String> {
        if (!profile.netPolicy) return emptySet()
        val raw = when (currentKind(context)) {
            Kind.WIFI -> profile.netWifiBlock
            Kind.MOBILE -> profile.netMobileBlock
            Kind.ROAMING -> profile.netRoamingBlock
            Kind.OTHER -> return emptySet()
        }
        return raw.filter { it.isNotBlank() && it != context.packageName }.toSet()
    }
}
