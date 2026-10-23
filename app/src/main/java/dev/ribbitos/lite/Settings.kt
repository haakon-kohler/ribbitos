package dev.ribbitos.lite

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where the R1 sends messages. Set on the device (SettingsActivity) or over adb (ConfigReceiver). */
data class Settings(
    /** The gateway's OpenAI-compatible base URL, e.g. `http://192.168.1.20:18789/v1`. */
    val baseUrl: String = "",
    /** Bearer token; for OpenClaw, `gateway.auth.token`. */
    val token: String = "",
    /** Which agent answers: `openclaw/default` or `openclaw/<agentId>`. */
    val agent: String = DEFAULT_AGENT,
) {
    /** True once [baseUrl] is a usable http(s) URL. */
    val configured: Boolean get() = baseUrl.trim().toHttpUrlOrNull() != null

    /** Saves trimmed values; a blank agent falls back to [DEFAULT_AGENT]. */
    fun save(ctx: Context) {
        prefs(ctx).edit()
            .putString(BASE_URL, baseUrl.trim())
            .putString(TOKEN, token.trim())
            .putString(AGENT, agent.trim().ifBlank { DEFAULT_AGENT })
            .apply()
    }

    /** Keeps the token out of logs and crash reports. */
    override fun toString(): String {
        val shown = if (token.isBlank()) "" else "***"
        return "Settings(baseUrl=$baseUrl, token=$shown, agent=$agent)"
    }

    companion object {
        const val BASE_URL = "base_url"
        const val TOKEN = "token"
        const val AGENT = "agent"
        const val DEFAULT_AGENT = "openclaw/default"

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

        /** The saved settings, or defaults. */
        fun load(ctx: Context): Settings = prefs(ctx).let {
            Settings(
                baseUrl = it.getString(BASE_URL, "").orEmpty(),
                token = it.getString(TOKEN, "").orEmpty(),
                agent = it.getString(AGENT, DEFAULT_AGENT).orEmpty().ifBlank { DEFAULT_AGENT },
            )
        }
    }
}
