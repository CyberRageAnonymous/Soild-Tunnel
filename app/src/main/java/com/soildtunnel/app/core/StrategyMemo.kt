package com.soildtunnel.app.core

import android.content.Context
import android.content.SharedPreferences
import com.soildtunnel.app.model.ConnectionProfile
import com.soildtunnel.app.model.EndpointMode
import com.soildtunnel.app.model.Noize
import com.soildtunnel.app.model.Protocol
import org.json.JSONObject

/**
 * Network-keyed strategy memory: when a ladder strategy wins on a network,
 * its recipe is pinned for that fingerprint for 24 hours, so reconnects on
 * the same network try the remembered winner first — no probing warm-up,
 * no wasted rungs. The key uses only signals available without special
 * permissions (operator name, cellular vs other transport, DPI class).
 */
object StrategyMemo {

    /** The ladder ingredients of a winning strategy, persisted as JSON. */
    data class Recipe(
        val protocol: Protocol,
        val noize: Noize,
        val masqueHttp2: Boolean,
        val fragment: Boolean,
        val ech: Boolean,
        /** The range the winning rung scanned, empty for engine-default. */
        val range: String,
        val wonAt: Long,
    )

    private const val PREFS_NAME = "strategy_memo"
    private const val TTL_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun keyOf(fp: NetworkFingerprint): String =
        "${fp.dpiClass.name}|${fp.operatorName}|${if (fp.iranCellular) "cell" else "net"}"

    /**
     * Maps the winning profile back to the recipe that built it. Only the
     * fields a ladder candidate would set are captured; a user-pinned
     * endpoint stays with the user, never with the memo.
     */
    fun recipeOf(won: ConnectionProfile): Recipe = Recipe(
        protocol = won.protocol,
        noize = won.noize,
        masqueHttp2 = won.masqueHttp2,
        fragment = won.fragment,
        ech = won.ech,
        range = if (won.endpointMode == EndpointMode.MANUAL_RANGE) won.manualRange else "",
        wonAt = System.currentTimeMillis(),
    )

    fun record(context: Context, fp: NetworkFingerprint, recipe: Recipe) {
        val value = JSONObject()
            .put("protocol", recipe.protocol.name)
            .put("noize", recipe.noize.name)
            .put("h2", recipe.masqueHttp2)
            .put("fragment", recipe.fragment)
            .put("ech", recipe.ech)
            .put("range", recipe.range)
            .put("wonAt", recipe.wonAt)
        prefs(context).edit().putString(keyOf(fp), value.toString()).apply()
    }

    fun recall(context: Context, fp: NetworkFingerprint): Recipe? {
        val raw = prefs(context).getString(keyOf(fp), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val wonAt = json.optLong("wonAt", 0L)
            if (System.currentTimeMillis() - wonAt > TTL_MS) {
                forget(context, fp)
                return null
            }
            Recipe(
                protocol = Protocol.valueOf(json.getString("protocol")),
                noize = Noize.valueOf(json.getString("noize")),
                masqueHttp2 = json.optBoolean("h2", false),
                fragment = json.optBoolean("fragment", false),
                ech = json.optBoolean("ech", false),
                range = json.optString("range", ""),
                wonAt = wonAt,
            )
        }.getOrNull()
    }

    fun forget(context: Context, fp: NetworkFingerprint) {
        prefs(context).edit().remove(keyOf(fp)).apply()
    }
}
