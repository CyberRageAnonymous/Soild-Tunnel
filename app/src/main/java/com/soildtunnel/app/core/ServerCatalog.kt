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
        ServerNode("ps-us-01", "United States", "PS-US-01", "US", listOf("162.159.192.0/24"), "162.159.192.1"),
        ServerNode("ps-us-02", "United States 2", "PS-US-02", "US", listOf("162.159.197.0/24"), "162.159.197.1"),
        ServerNode("ps-ca-01", "Canada", "PS-CA-01", "CA", listOf("162.159.198.0/24"), "162.159.198.1"),
        ServerNode("ps-de-01", "Germany", "PS-DE-01", "DE", listOf("162.159.193.0/24"), "162.159.193.1"),
        ServerNode("ps-de-02", "Germany 2", "PS-DE-02", "DE", listOf("188.114.100.0/24"), "188.114.100.1"),
        ServerNode("ps-nl-01", "Netherlands", "PS-NL-01", "NL", listOf("162.159.195.0/24"), "162.159.195.1"),
        ServerNode("ps-uk-01", "United Kingdom", "PS-GB-01", "GB", listOf("162.159.196.0/24"), "162.159.196.1"),
        ServerNode("ps-fr-01", "France", "PS-FR-01", "FR", listOf("162.159.200.0/24"), "162.159.200.1"),
        ServerNode("ps-fr-02", "France 2", "PS-FR-02", "FR", listOf("188.114.101.0/24"), "188.114.101.1"),
        ServerNode("ps-tr-01", "Turkey", "PS-TR-01", "TR", listOf("162.159.204.0/24"), "162.159.204.1"),
        ServerNode("ps-pl-01", "Poland", "PS-PL-01", "PL", listOf("162.159.205.0/24"), "162.159.205.1"),
        ServerNode("ps-se-01", "Sweden", "PS-SE-01", "SE", listOf("188.114.98.0/24"), "188.114.98.1"),
        ServerNode("ps-se-02", "Sweden 2", "PS-SE-02", "SE", listOf("188.114.102.0/24"), "188.114.102.1"),
        ServerNode("ps-fi-01", "Finland", "PS-FI-01", "FI", listOf("188.114.99.0/24"), "188.114.99.1"),
        ServerNode("ps-ch-01", "Switzerland", "PS-CH-01", "CH", listOf("188.114.96.0/24"), "188.114.96.1"),
        ServerNode("ps-at-01", "Austria", "PS-AT-01", "AT", listOf("172.65.251.0/24"), "172.65.251.1"),
        ServerNode("ps-it-01", "Italy", "PS-IT-01", "IT", listOf("188.114.97.0/24"), "188.114.97.1"),
        ServerNode("ps-jp-01", "Japan", "PS-JP-01", "JP", listOf("162.159.201.0/24"), "162.159.201.1"),
        ServerNode("ps-sg-01", "Singapore", "PS-SG-01", "SG", listOf("162.159.202.0/24"), "162.159.202.1"),
        ServerNode("ps-au-01", "Australia", "PS-AU-01", "AU", listOf("162.159.203.0/24"), "162.159.203.1"),
        ServerNode("ps-br-01", "Brazil", "PS-BR-01", "BR", listOf("188.114.103.0/24"), "188.114.103.1"),
        ServerNode("ps-in-01", "India", "PS-IN-01", "IN", listOf("188.114.104.0/24"), "188.114.104.1"),
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
