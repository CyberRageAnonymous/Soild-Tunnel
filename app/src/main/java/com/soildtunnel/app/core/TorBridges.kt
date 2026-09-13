package com.soildtunnel.app.core

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Official built-in Tor bridges (obfs4/snowflake/meek), the same set the
 * official Tor apps ship: high-capacity Tor-operated bridges instead of
 * short-lived bot handouts that die within hours on filtered networks.
 *
 * A copy ships in assets; a fresher copy is fetched in the background (same
 * public endpoint the official apps use) and cached for two days.
 */
object TorBridges {

    private const val ASSET = "tor/builtin-bridges.json"
    private const val CACHE = "builtin-bridges.json"
    private const val TTL_MS = 2 * 24 * 60 * 60 * 1000L

    private const val UPDATE_URL_1 = "https://bridges.torproject.org/moat/circumvention/builtin"
    private const val UPDATE_URL_2 = "https://tns1.bypasscensorship.org/moat/circumvention/builtin"

    /** Transport name -> raw bridge lines ("obfs4 1.2.3.4:443 ..."). */
    fun load(context: Context): Map<String, List<String>> {
        // Fresh cache wins, the shipped asset is the fallback. Either way a
        // connect never waits on the network here.
        val cached = runCatching {
            File(context.filesDir, CACHE).takeIf { it.exists() }?.readText()
        }.getOrNull()
        return parse(cached) ?: parse(asset(context)) ?: emptyMap()
    }

    /** Refreshes the cache when older than two days. Best effort, silent. */
    suspend fun refreshInBackground(context: Context) = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, CACHE)
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < TTL_MS) return@withContext
        val fresh = fetch(UPDATE_URL_1) ?: fetch(UPDATE_URL_2) ?: return@withContext
        if (parse(fresh).isNullOrEmpty()) return@withContext
        runCatching { cache.writeText(fresh) }
    }

    private fun asset(context: Context): String? = runCatching {
        context.assets.open(ASSET).bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun parse(raw: String?): Map<String, List<String>>? = runCatching {
        val json = JSONObject(raw ?: return@runCatching null)
        buildMap {
            for (key in listOf("obfs4", "snowflake", "webtunnel", "meek", "meek-azure", "dnstt")) {
                val arr = json.optJSONArray(key) ?: continue
                val lines = (0 until arr.length())
                    .map { arr.optString(it).trim() }
                    .filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) put(key, lines)
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun fetch(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "SoildTunnel")
        try {
            if (conn.responseCode != 200) return@runCatching null
            conn.inputStream.bufferedReader().use { it.readText() }.takeIf { it.length > 100 }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Pulls url=/fronts=/ice= out of snowflake bridge lines for the client. */
    fun snowflakeArgs(lines: List<String>): Map<String, String> {
        val line = lines.firstOrNull { it.contains("snowflake") } ?: return emptyMap()
        return line.split(Regex("\\s+")).mapNotNull { token ->
            val name = token.substringBefore("=").lowercase()
            if (name in setOf("url", "fronts", "ice") && "=" in token) {
                name to token.substringAfter("=")
            } else null
        }.toMap()
    }
}
