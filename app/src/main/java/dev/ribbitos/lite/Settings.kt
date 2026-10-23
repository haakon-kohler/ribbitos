package dev.ribbitos.lite

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Who answers. Each provider keeps its own URL, key and model, so switching back and forth loses nothing. */
enum class Provider(val id: String, val defaultUrl: String, val defaultModel: String) {
    /** Canned replies, no server: [DemoBackend]. */
    DEMO("demo", "", ""),

    /** A self-hosted OpenClaw gateway: [dev.ribbitos.lite.net.OpenClawBackend]. */
    OPENCLAW("openclaw", "", "openclaw/default"),

    /** OpenAI's API, or any OpenAI-compatible server: [dev.ribbitos.lite.net.OpenAiBackend]. */
    OPENAI("openai", "https://api.openai.com/v1", "gpt-6.1-sol"),

    /** Anthropic's Claude API: [dev.ribbitos.lite.net.ClaudeBackend]. */
    CLAUDE("claude", "https://api.anthropic.com", "claude-opus-5-5"),
    ;

    /** Whether this provider needs a URL / an API key before it can answer. */
    val needsUrl: Boolean get() = this != DEMO
    val needsKey: Boolean get() = this == OPENAI || this == CLAUDE

    companion object {
        /** The provider with [id], or null. */
        fun find(id: String?): Provider? = entries.firstOrNull { it.id == id }

        /** The provider with [id], or [DEMO] for anything unknown. */
        fun of(id: String?): Provider = find(id) ?: DEMO
    }
}

/** Where the R1 sends messages. Set on the device (SettingsActivity) or over adb (ConfigReceiver). */
data class Settings(
    val provider: Provider = Provider.DEMO,
    /** Base URL: the OpenClaw gateway's `/v1`, OpenAI's `/v1`, or Anthropic's API root. */
    val baseUrl: String = provider.defaultUrl,
    /** Bearer token or API key. */
    val token: String = "",
    /** Model, or for OpenClaw the agent: `openclaw/default` or `openclaw/<agentId>`. */
    val model: String = provider.defaultModel,
) {
    /** True for a usable http(s) [baseUrl]. */
    val urlValid: Boolean get() = baseUrl.trim().toHttpUrlOrNull() != null

    /** True once this provider has everything it needs to answer. */
    val configured: Boolean
        get() = (!provider.needsUrl || urlValid) && (!provider.needsKey || token.isNotBlank())

    /** Saves this provider's values and makes it the active one. Blank fields fall back to defaults. */
    fun save(ctx: Context) {
        prefs(ctx).edit()
            .putString(PROVIDER, provider.id)
            .putString(key(provider, BASE_URL), baseUrl.trim().ifBlank { provider.defaultUrl })
            .putString(key(provider, TOKEN), token.trim())
            .putString(key(provider, MODEL), model.trim().ifBlank { provider.defaultModel })
            .apply()
    }

    /** Keeps the token out of logs and crash reports. */
    override fun toString(): String {
        val shown = if (token.isBlank()) "" else "***"
        return "Settings(provider=${provider.id}, baseUrl=$baseUrl, token=$shown, model=$model)"
    }

    companion object {
        const val PROVIDER = "provider"
        const val BASE_URL = "base_url"
        const val TOKEN = "token"
        const val MODEL = "model"

        private fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

        private fun key(p: Provider, field: String) = "${p.id}.$field"

        /** The active provider's saved settings, or the demo. */
        fun load(ctx: Context): Settings = load(ctx, Provider.of(prefs(ctx).getString(PROVIDER, null)))

        /** [p]'s saved settings, or its defaults; doesn't change which provider is active. */
        fun load(ctx: Context, p: Provider): Settings = prefs(ctx).let {
            Settings(
                provider = p,
                baseUrl = it.getString(key(p, BASE_URL), null).orEmpty().ifBlank { p.defaultUrl },
                token = it.getString(key(p, TOKEN), null).orEmpty(),
                model = it.getString(key(p, MODEL), null).orEmpty().ifBlank { p.defaultModel },
            )
        }
    }
}
