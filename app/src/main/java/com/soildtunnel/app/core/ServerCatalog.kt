package com.soildtunnel.app.core

import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.EndpointMode

/** One edge node for the server list. */
data class ServerNode(
    val id: String,
    /** Display name shown in the picker (country label). */
    val name: String,
    /** Short console-style code, e.g. "DE-01". */
    val code: String,
    /** ISO 3166-1 alpha-2 country code for the flag emoji. */
    val countryCode: String,
    /** What gets written into ConnectionProfile.manualRange on selection. */
    val cidrs: List<String>,
    /** Representative IP for the live TCP latency measurement. */
    val probeHost: String,
) {
    /** The exact profile value this node serialises to. */
    val rangeSpec: String get() = cidrs.joinToString(", ")
}

/** Built-in server list, in display order. */
object ServerCatalog {

    /** Pinned port for latency probes. 443 is served by every listed edge. */
    private const val PROBE_PORT = 443

    val AUTO_ID = "__auto__"

    /** "Auto" is not a node; it clears any pin and lets the engine scan everything. */
    val auto: ServerNode = ServerNode(
        id = AUTO_ID,
        name = "Auto",
        code = "AUTO",
        countryCode = "",
        cidrs = emptyList(),
        probeHost = "1.1.1.1",
    )

    val nodes: List<ServerNode> = listOf(
        ServerNode("de-01", "Germany", "DE-01", "DE", listOf("162.159.192.0/24"), "162.159.192.1"),
        ServerNode("nl-01", "Netherlands", "NL-01", "NL", listOf("162.159.193.0/24"), "162.159.193.1"),
        ServerNode("fr-01", "France", "FR-01", "FR", listOf("162.159.195.0/24"), "162.159.195.1"),
        ServerNode("uk-01", "United Kingdom", "GB-01", "GB", listOf("162.159.196.0/24"), "162.159.196.1"),
        ServerNode("tr-01", "Turkey", "TR-01", "TR", listOf("162.159.204.0/24"), "162.159.204.1"),
        ServerNode("at-01", "Austria", "AT-01", "AT", listOf("172.65.251.0/24"), "172.65.251.1"),
        ServerNode("ch-01", "Switzerland", "CH-01", "CH", listOf("188.114.96.0/24"), "188.114.96.1"),
        ServerNode("it-01", "Italy", "IT-01", "IT", listOf("188.114.97.0/24"), "188.114.97.1"),
        ServerNode("se-01", "Sweden", "SE-01", "SE", listOf("188.114.98.0/24"), "188.114.98.1"),
        ServerNode("fi-01", "Finland", "FI-01", "FI", listOf("188.114.99.0/24"), "188.114.99.1"),
        ServerNode("us-01", "United States", "US-01", "US", listOf("8.6.112.0/24"), "8.6.112.1"),
    )

    val psiphonNodes: List<ServerNode> = listOf(
        ServerNode("ps-us-01", "United States (Psiphon)", "PS-US-01", "US", listOf("198.98.54.0/24"), "198.98.54.1"),
        ServerNode("ps-us-02", "United States 2 (Psiphon)", "PS-US-02", "US", listOf("174.136.107.0/24"), "174.136.107.1"),
        ServerNode("ps-ca-01", "Canada (Psiphon)", "PS-CA-01", "CA", listOf("184.147.18.0/24"), "184.147.18.1"),
        ServerNode("ps-de-01", "Germany (Psiphon)", "PS-DE-01", "DE", listOf("149.56.108.0/24"), "149.56.108.1"),
        ServerNode("ps-nl-01", "Netherlands (Psiphon)", "PS-NL-01", "NL", listOf("178.162.193.0/24"), "178.162.193.1"),
        ServerNode("ps-uk-01", "United Kingdom (Psiphon)", "PS-GB-01", "GB", listOf("185.220.101.0/24"), "185.220.101.1"),
        ServerNode("ps-fr-01", "France (Psiphon)", "PS-FR-01", "FR", listOf("51.15.76.0/24"), "51.15.76.1"),
        ServerNode("ps-tr-01", "Turkey (Psiphon)", "PS-TR-01", "TR", listOf("78.46.84.0/24"), "78.46.84.1"),
        ServerNode("ps-jp-01", "Japan (Psiphon)", "PS-JP-01", "JP", listOf("153.122.78.0/24"), "153.122.78.1"),
        ServerNode("ps-sg-01", "Singapore (Psiphon)", "PS-SG-01", "SG", listOf("139.162.45.0/24"), "139.162.45.1"),
        ServerNode("ps-se-01", "Sweden (Psiphon)", "PS-SE-01", "SE", listOf("185.246.188.0/24"), "185.246.188.1"),
        ServerNode("ps-ch-01", "Switzerland (Psiphon)", "PS-CH-01", "CH", listOf("185.56.80.0/24"), "185.56.80.1"),
    )

    /** Everything the picker shows, in display order. */
    val all: List<ServerNode> = listOf(auto) + nodes

    fun allFor(profile: ConnectionProfile): List<ServerNode> {
        val usePsiphon = profile.networkBackend == com.soildtunnel.app.model.NetworkBackend.SOILDTUNNEL_PSIPHON
                && profile.protocol != com.soildtunnel.app.model.Protocol.TOR
        return listOf(auto) + if (usePsiphon) psiphonNodes else nodes
    }

    fun byId(id: String): ServerNode? = (listOf(auto) + nodes + psiphonNodes).firstOrNull { it.id == id }

    fun probePort(): Int = PROBE_PORT

    /**
     * Resolves the node currently encoded in [profile]:
     *  - EndpointMode.AUTO                     → [auto]
     *  - EndpointMode.MANUAL_RANGE matching    → the matching node
     *  - anything else (pinned peer, foreign
     *    range typed in Advanced settings)     → null (the UI shows "custom")
     */
    fun selectedIn(profile: ConnectionProfile): ServerNode? = when (profile.endpointMode) {
        EndpointMode.AUTO -> auto
        EndpointMode.MANUAL_RANGE -> {
            val raw = profile.manualRange.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            (nodes + psiphonNodes).firstOrNull { node -> node.cidrs == raw }
        }
        else -> null
    }

    /** Applies [node] to [profile], producing the profile that pins it. */
    fun applyTo(profile: ConnectionProfile, node: ServerNode): ConnectionProfile =
        if (node.id == AUTO_ID) {
            profile.copy(
                endpointMode = EndpointMode.AUTO,
                manualRange = "",
            )
        } else {
            profile.copy(
                endpointMode = EndpointMode.MANUAL_RANGE,
                manualRange = node.rangeSpec,
            )
        }
}
