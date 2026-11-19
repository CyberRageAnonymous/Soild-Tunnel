package com.soildtunnel.app.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.soildtunnel.app.model.ChainMode
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.CoreLogLevel
import com.soildtunnel.app.model.ConnectionState
import com.soildtunnel.app.model.DnsMode
import com.soildtunnel.app.model.EndpointMode
import com.soildtunnel.app.model.GoolMode
import com.soildtunnel.app.model.IpVersion
import com.soildtunnel.app.model.Noize
import com.soildtunnel.app.model.Protocol
import com.soildtunnel.app.model.ScanMode
import com.soildtunnel.app.model.SoildMode
import com.soildtunnel.app.model.SplitMode
import com.soildtunnel.app.model.TeamAuth
import com.soildtunnel.app.model.TlsFp
import com.soildtunnel.app.model.TorTransport
import com.soildtunnel.app.vpn.SoildTunnelVpnService

/**
 * App-wide singleton that (a) publishes the live [ConnectionState] to the UI and
 * (b) sends connect/disconnect intents to [SoildTunnelVpnService].
 */
object SoildTunnelController {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Epoch millis of when the current session became Connected, or null. */
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    /** IP + country shown in the UI (exit server when connected, operator when not). */
    private val _ipInfo = MutableStateFlow<IpEndpoint?>(null)
    val ipInfo: StateFlow<IpEndpoint?> = _ipInfo.asStateFlow()

    /** True while an IP lookup is in flight (drives the “…” placeholder). */
    private val _ipLoading = MutableStateFlow(false)
    val ipLoading: StateFlow<Boolean> = _ipLoading.asStateFlow()

    /** Called by the service to broadcast state changes. */
    fun setState(newState: ConnectionState) {
        _state.value = newState
        when (newState) {
            is ConnectionState.Connected ->
                if (_connectedSince.value == null) _connectedSince.value = System.currentTimeMillis()
            is ConnectionState.Reconnecting -> {
                // Keep the running timer during a transient reconnect.
            }
            else -> _connectedSince.value = null
        }
    }

    fun setIpInfo(info: IpEndpoint?) {
        _ipInfo.value = info
    }

    /**
     * Sets the tunnel exit IP only when the badge doesn't already show a
     * tunnel IP for this session.
     */
    fun offerTunnelIpInfo(info: IpEndpoint) {
        if (_ipInfo.value?.viaTunnel == true) return
        _ipInfo.value = info
    }

    fun setIpLoading(loading: Boolean) {
        _ipLoading.value = loading
    }

    /**
     * Returns a consent Intent if the user must still grant VPN permission,
     * or null if permission was already granted.
     */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    fun connect(context: Context, profile: ConnectionProfile) {
        val intent = Intent(context, SoildTunnelVpnService::class.java).apply {
            action = SoildTunnelVpnService.ACTION_CONNECT
            putExtra(SoildTunnelVpnService.EXTRA_PROFILE, ProfileCodec.encode(profile))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, SoildTunnelVpnService::class.java).apply {
            action = SoildTunnelVpnService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

/**
 * Serialises a [ConnectionProfile] into a compact `key=value` list (one pair per
 * line) for Intent transport. This format is forward/backward tolerant: unknown
 * keys are ignored and missing keys fall back to the model defaults, so old and
 * new builds can decode each other's payloads without crashing.
 */
object ProfileCodec {
    fun encode(p: ConnectionProfile): String = buildList {
        add("protocol=${p.protocol.name}")
        add("gool=${p.goolMode.name}")
        add("soild=${p.soildMode.name}")
        add("soildHunt=${p.soildHunt}")
        add("scan=${p.scanMode.name}")
        add("ip=${p.ipVersion.name}")
        add("quick=${p.quickReconnect}")
        add("h2=${p.masqueHttp2}")
        add("share=${p.lanShare}")
        add("noize=${p.noize.name}")
        add("endpoint=${p.endpointMode.name}")
        add("peer=${p.manualPeer}")
        add("range=${p.manualRange}")
        add("keepalive=${p.keepalive}")
        add("fragment=${p.fragment}")
        add("ech=${p.ech}")
        add("mtu=${p.mtu}")
        add("proxy=${p.proxyMode}")
        add("split=${p.splitMode.name}")
        add("splitApps=${p.splitApps.joinToString(",")}")
        // Added later (feature parity)
        add("kill=${p.killSwitch}")
        add("strictKill=${p.strictKillSwitch}")
        add("v6leak=${p.ipv6LeakProtection}")
        add("smartRe=${p.smartReconnect}")
        add("reLimit=${p.reconnectRetryLimit}")
        add("fSize=${p.fragmentSize}")
        add("fDelay=${p.fragmentDelay}")
        add("noDataCheck=${p.noDataCheck}")
        add("tlsGroups=${p.tlsGroups}")
        add("valSecs=${p.validateSecs}")
        add("recSecs=${p.reconnectSecs}")
        add("noProfRetry=${p.noProfileRetry}")
        add("coreLog=${p.coreLogLevel.name}")
        add("blockedApps=${p.blockedApps.joinToString(",")}")
        add("torTransport=${p.torTransport.name}")
        add("torBridges=${esc(p.torBridges)}")
        add("torExit=${p.torExitCountry}")
        // Everything below used to be missing entirely, so the setting was
        // saved in the UI and then quietly dropped before the engine saw it.
        add("dnsServers=${esc(p.dnsServers)}")
        add("dnsMode=${p.dnsMode.name}")
        add("encDns=${esc(p.encryptedDnsEndpoint)}")
        add("dnsAdBlock=${p.dnsAdBlock}")
        add("dnsMalware=${p.dnsMalwareBlock}")
        add("team=${esc(p.team)}")
        add("teamAuth=${p.teamAuth.name}")
        add("accessId=${esc(p.accessClientId)}")
        add("accessEmail=${esc(p.accessEmail)}")
        add("gateway=${p.gateway}")
        add("routeBlock=${esc(p.routeBlock)}")
        add("routeDirect=${esc(p.routeDirect)}")
        add("upstream=${esc(p.upstreamProxy)}")
        add("customSni=${esc(p.customSni)}")
        add("routeSniff=${p.routeSniff}")
        add("routeSniffMs=${p.routeSniffMs}")
        add("reprovision=${p.autoReprovision}")
        add("tuicServer=${esc(p.tuicServer)}")
        add("tuicSni=${esc(p.tuicSni)}")
        add("tuicCc=${esc(p.tuicCc)}")
        add("tuicInsecure=${p.tuicInsecure}")
        add("chain=${p.chainMode.name}")
        add("tlsFp=${p.tlsFp.name}")
        add("decoy=${p.decoyTraffic}")
        add("coverPort=${p.coverPort}")
        add("netPolicy=${p.netPolicy}")
        add("netWifi=${p.netWifiBlock.joinToString(",")}")
        add("netMobile=${p.netMobileBlock.joinToString(",")}")
        add("netRoaming=${p.netRoamingBlock.joinToString(",")}")
        add("scene=${p.scene}")
    }.joinToString("\n")

    fun decode(raw: String?): ConnectionProfile {
        if (raw.isNullOrBlank()) return ConnectionProfile()
        // Backward compatibility: the 1.0/1.1 codec used a single pipe-delimited
        // line with no keys. Detect and decode that legacy shape.
        if (!raw.contains('=') && raw.contains('|')) return decodeLegacy(raw)

        val map = raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
        val d = ConnectionProfile()
        return runCatching {
            ConnectionProfile(
                protocol = map["protocol"]?.let { enumOr<Protocol>(it) } ?: d.protocol,
                goolMode = map["gool"]?.let { enumOr<GoolMode>(it) } ?: d.goolMode,
                soildMode = map["soild"]?.let { enumOr<SoildMode>(it) } ?: d.soildMode,
                soildHunt = map["soildHunt"]?.toBooleanStrictOrNull() ?: d.soildHunt,
                scanMode = map["scan"]?.let { enumOr<ScanMode>(it) } ?: d.scanMode,
                ipVersion = map["ip"]?.let { enumOr<IpVersion>(it) } ?: d.ipVersion,
                quickReconnect = map["quick"]?.toBooleanStrictOrNull() ?: d.quickReconnect,
                masqueHttp2 = map["h2"]?.toBooleanStrictOrNull() ?: d.masqueHttp2,
                lanShare = map["share"]?.toBooleanStrictOrNull() ?: d.lanShare,
                noize = map["noize"]?.let { enumOr<Noize>(it) } ?: d.noize,
                endpointMode = map["endpoint"]?.let { enumOr<EndpointMode>(it) } ?: d.endpointMode,
                manualPeer = map["peer"] ?: d.manualPeer,
                manualRange = map["range"] ?: d.manualRange,
                keepalive = map["keepalive"]?.toIntOrNull() ?: d.keepalive,
                fragment = map["fragment"]?.toBooleanStrictOrNull() ?: d.fragment,
                ech = map["ech"]?.toBooleanStrictOrNull() ?: d.ech,
                mtu = map["mtu"]?.toIntOrNull() ?: d.mtu,
                proxyMode = map["proxy"]?.toBooleanStrictOrNull() ?: d.proxyMode,
                splitMode = map["split"]?.let { enumOr<SplitMode>(it) } ?: d.splitMode,
                splitApps = map["splitApps"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.splitApps,
                killSwitch = map["kill"]?.toBooleanStrictOrNull() ?: d.killSwitch,
                strictKillSwitch = map["strictKill"]?.toBooleanStrictOrNull() ?: d.strictKillSwitch,
                ipv6LeakProtection = map["v6leak"]?.toBooleanStrictOrNull() ?: d.ipv6LeakProtection,
                smartReconnect = map["smartRe"]?.toBooleanStrictOrNull() ?: d.smartReconnect,
                reconnectRetryLimit = map["reLimit"]?.toIntOrNull() ?: d.reconnectRetryLimit,
                fragmentSize = map["fSize"] ?: d.fragmentSize,
                fragmentDelay = map["fDelay"] ?: d.fragmentDelay,
                noDataCheck = map["noDataCheck"]?.toBooleanStrictOrNull() ?: d.noDataCheck,
                tlsGroups = map["tlsGroups"] ?: d.tlsGroups,
                validateSecs = map["valSecs"]?.toIntOrNull() ?: d.validateSecs,
                reconnectSecs = map["recSecs"]?.toIntOrNull() ?: d.reconnectSecs,
                noProfileRetry = map["noProfRetry"]?.toBooleanStrictOrNull() ?: d.noProfileRetry,
                coreLogLevel = map["coreLog"]?.let { enumOr<CoreLogLevel>(it) } ?: d.coreLogLevel,
                blockedApps = map["blockedApps"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.blockedApps,
                torTransport = map["torTransport"]?.let { enumOr<TorTransport>(it) } ?: d.torTransport,
                torBridges = map["torBridges"]?.let { unesc(it) } ?: d.torBridges,
                torExitCountry = map["torExit"]?.trim()?.take(2)?.uppercase() ?: d.torExitCountry,
                dnsServers = map["dnsServers"]?.let { unesc(it) } ?: d.dnsServers,
                dnsMode = map["dnsMode"]?.let { enumOr<DnsMode>(it) } ?: d.dnsMode,
                encryptedDnsEndpoint = map["encDns"]?.let { unesc(it) } ?: d.encryptedDnsEndpoint,
                dnsAdBlock = map["dnsAdBlock"]?.toBooleanStrictOrNull() ?: d.dnsAdBlock,
                dnsMalwareBlock = map["dnsMalware"]?.toBooleanStrictOrNull() ?: d.dnsMalwareBlock,
                team = map["team"]?.let { unesc(it) } ?: d.team,
                teamAuth = map["teamAuth"]?.let { enumOr<TeamAuth>(it) } ?: d.teamAuth,
                accessClientId = map["accessId"]?.let { unesc(it) } ?: d.accessClientId,
                accessEmail = map["accessEmail"]?.let { unesc(it) } ?: d.accessEmail,
                gateway = map["gateway"]?.toBooleanStrictOrNull() ?: d.gateway,
                routeBlock = map["routeBlock"]?.let { unesc(it) } ?: d.routeBlock,
                routeDirect = map["routeDirect"]?.let { unesc(it) } ?: d.routeDirect,
                upstreamProxy = map["upstream"]?.let { unesc(it) } ?: d.upstreamProxy,
                customSni = map["customSni"]?.let { unesc(it) } ?: d.customSni,
                routeSniff = map["routeSniff"]?.toBooleanStrictOrNull() ?: d.routeSniff,
                routeSniffMs = map["routeSniffMs"]?.toIntOrNull() ?: d.routeSniffMs,
                autoReprovision = map["reprovision"]?.toBooleanStrictOrNull() ?: d.autoReprovision,
                tuicServer = map["tuicServer"]?.let { unesc(it) } ?: d.tuicServer,
                tuicSni = map["tuicSni"]?.let { unesc(it) } ?: d.tuicSni,
                tuicCc = map["tuicCc"]?.let { unesc(it) } ?: d.tuicCc,
                tuicInsecure = map["tuicInsecure"]?.toBooleanStrictOrNull() ?: d.tuicInsecure,
                chainMode = map["chain"]?.let { enumOr<ChainMode>(it) } ?: d.chainMode,
                tlsFp = map["tlsFp"]?.let { enumOr<TlsFp>(it) } ?: d.tlsFp,
                decoyTraffic = map["decoy"]?.toBooleanStrictOrNull() ?: d.decoyTraffic,
                coverPort = map["coverPort"]?.toIntOrNull() ?: d.coverPort,
                netPolicy = map["netPolicy"]?.toBooleanStrictOrNull() ?: d.netPolicy,
                netWifiBlock = map["netWifi"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.netWifiBlock,
                netMobileBlock = map["netMobile"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.netMobileBlock,
                netRoamingBlock = map["netRoaming"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.netRoamingBlock,
                scene = map["scene"] ?: d.scene,
            )
        }.getOrDefault(d)
    }

    private fun decodeLegacy(raw: String): ConnectionProfile {
        val parts = raw.split("|")
        if (parts.size < 5) return ConnectionProfile()
        return runCatching {
            ConnectionProfile(
                protocol = Protocol.valueOf(parts[0]),
                scanMode = ScanMode.valueOf(parts[1]),
                ipVersion = IpVersion.valueOf(parts[2]),
                quickReconnect = parts[3].toBoolean(),
                masqueHttp2 = parts[4].toBoolean(),
                lanShare = parts.getOrNull(5)?.toBoolean() ?: false,
            )
        }.getOrDefault(ConnectionProfile())
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()

    /** One payload value per line, so a pasted rule list has to be flattened. */
    private fun esc(value: String): String = buildString {
        for (c in value) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }

    private fun unesc(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                if (next == 'n') { append('\n'); i += 2; continue }
                if (next == '\\') { append('\\'); i += 2; continue }
            }
            append(c)
            i++
        }
    }
}
