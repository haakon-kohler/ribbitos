package dev.ribbitos.lite.net

import dev.ribbitos.lite.Backend
import dev.ribbitos.lite.History
import dev.ribbitos.lite.Prompt
import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Style
import dev.ribbitos.lite.Transcript
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to OpenAI's Chat Completions API, or to any server that speaks it (point the URL
 * elsewhere). The API keeps no state, so every request carries the conversation so far, shaped and
 * bounded by [History.forTurn].
 */
class OpenAiBackend(private val settings: Settings) : Backend {
    private val http = ChatCompletions(
        settings.baseUrl,
        settings.token,
        notFoundHint = "Not found (HTTP 404): check the URL and the model name",
    )

    init {
        require(settings.provider == Provider.OPENAI) { "Not OpenAI settings: ${settings.provider}" }
        require(settings.configured) { "OpenAI needs a URL and an API key" }
    }

    override fun send(
        prompt: Prompt,
        history: List<Transcript.Message>,
        conversation: String,
        onDelta: (String) -> Unit,
    ): String = http.stream(body(settings, History.forTurn(history, prompt.text), prompt.photo), onDelta)

    override fun cancel() = http.cancel()

    companion object {
        /** The request body: the style prompt, then the conversation ending in the new message. */
        fun body(s: Settings, conversation: List<Transcript.Message>, photo: ByteArray? = null): JSONObject {
            require(conversation.lastOrNull()?.role == Transcript.USER) { "The conversation must end with the user" }
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", Style.SYSTEM))
            conversation.dropLast(1).forEach { messages.put(JSONObject().put("role", it.role).put("content", it.text)) }
            // The photo rides only with the newest message; earlier ones are history.
            messages.put(ChatCompletions.userMessage(conversation.last().text, photo))
            return JSONObject().put("model", s.model).put("messages", messages)
        }
    }
}
