package dev.ribbitos.lite.net

import dev.ribbitos.lite.Backend
import dev.ribbitos.lite.Prompt
import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Style
import dev.ribbitos.lite.Transcript
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to an OpenClaw gateway through its OpenAI-compatible `/v1/chat/completions` endpoint
 * (`gateway.http.endpoints.chatCompletions.enabled`). Only the new message is sent: the gateway
 * keeps the history, keyed off the `user` field, so each ribbitOS conversation is one OpenClaw
 * session. The gateway token is an operator credential; keep the gateway off the internet.
 */
class OpenClawBackend(private val settings: Settings) : Backend {
    private val http = ChatCompletions(
        settings.baseUrl,
        settings.token,
        notFoundHint = "No chat endpoint: enable gateway.http.endpoints.chatCompletions",
    )

    init {
        require(settings.provider == Provider.OPENCLAW) { "Not OpenClaw settings: ${settings.provider}" }
        require(settings.configured) { "No gateway URL set" }
    }

    override fun send(
        prompt: Prompt,
        history: List<Transcript.Message>,
        conversation: String,
        onDelta: (String) -> Unit,
    ): String = http.stream(body(settings, prompt, conversation), onDelta)

    override fun cancel() = http.cancel()

    companion object {
        /** The `user` value the gateway derives a session from. */
        fun sessionUser(conversation: String): String = "ribbitos-$conversation"

        /** The request body for one new message: the gateway already has the rest. */
        fun body(s: Settings, prompt: Prompt, conversation: String): JSONObject {
            require(conversation.isNotBlank()) { "No conversation id" }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", Style.SYSTEM))
                .put(ChatCompletions.userMessage(prompt.text, prompt.photo))
            return JSONObject()
                .put("model", s.model)
                .put("user", sessionUser(conversation))
                .put("messages", messages)
        }
    }
}
