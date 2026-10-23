package dev.ribbitos.lite.net

import org.json.JSONException
import org.json.JSONObject

// Adapted from robotOS app/src/main/java/dev/r1ptt/net/Sse.kt (MIT, see NOTICE).

/** Minimal Server-Sent Events parser: feed it lines, get (event, data) pairs. */
class SseParser(private val onEvent: (event: String?, data: String) -> Unit) {
    private var event: String? = null
    private val data = StringBuilder()

    // Tracked separately from data's length: a bare `data` line still adds an (empty) line.
    private var hasData = false

    /** Feeds one line, without its line terminator (a trailing `\r` is tolerated). */
    fun line(raw: String) {
        val line = raw.removeSuffix("\r")
        when {
            line.isEmpty() -> dispatch()
            line.startsWith(":") -> Unit // comment / keepalive
            else -> field(line)
        }
    }

    /** Dispatches whatever is buffered, for a stream that ended without a blank line. */
    fun finish() = dispatch()

    private fun field(line: String) {
        val colon = line.indexOf(':')
        val name = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (name) {
            "event" -> event = value
            "data" -> data(value)
            else -> Unit // id, retry and unknown fields don't matter here
        }
    }

    private fun data(value: String) {
        // Some servers skip the blank line between events. A complete JSON object already
        // buffered means this line starts a new event.
        if (hasData && isComplete(data)) dispatch()
        if (hasData) data.append('\n')
        data.append(value)
        hasData = true
    }

    private fun dispatch() {
        if (hasData) onEvent(event, data.toString())
        event = null
        data.setLength(0)
        hasData = false
    }

    private fun isComplete(sb: StringBuilder): Boolean {
        val s = sb.trim().toString()
        return s == ChatChunk.DONE || (s.startsWith("{") && s.endsWith("}") && ChatChunk.json(s) != null)
    }
}

/** What one chat-completions stream event carried. */
sealed interface ChatChunk {
    /** A piece of the reply. */
    data class Text(val delta: String) : ChatChunk

    /** The gateway reported an error mid-stream. */
    data class Failed(val message: String) : ChatChunk

    /** The stream's end marker. */
    data object Done : ChatChunk

    /** Anything else: role announcements, empty deltas, tool chatter, garbage. */
    data object Other : ChatChunk

    companion object {
        const val DONE = "[DONE]"

        /** Classifies one event's data. */
        fun parse(data: String): ChatChunk {
            if (data.trim() == DONE) return Done
            val j = json(data) ?: return Other
            val error = j.optJSONObject("error")
            return if (error != null) Failed(error.optString("message").ifBlank { "Server error" }) else text(j)
        }

        /** The whole reply from a non-streamed chat-completions response. */
        fun fullMessage(body: String): String =
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")

        /** [s] as a JSON object, or null if it isn't one. */
        fun json(s: String): JSONObject? = try {
            JSONObject(s)
        } catch (e: JSONException) {
            null // Not JSON: the caller treats it as an ordinary non-JSON line.
        }

        private fun text(j: JSONObject): ChatChunk {
            val choice = j.optJSONArray("choices")?.optJSONObject(0)
            val delta = choice?.optJSONObject("delta") ?: choice?.optJSONObject("message")
            val content = delta?.opt("content")
            return if (content is String && content.isNotEmpty()) Text(content) else Other
        }
    }
}
