package dev.ribbitos.lite.net

import dev.ribbitos.lite.Backend
import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.Settings
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to an OpenClaw gateway through its OpenAI-compatible `/v1/chat/completions` endpoint
 * (`gateway.http.endpoints.chatCompletions.enabled`). Only the new message is sent: the gateway
 * keeps the history, keyed off the `user` field, so each ribbitOS conversation is one OpenClaw
 * session. The gateway token is an operator credential; keep the gateway off the internet.
 */
class OpenClawBackend(private val settings: Settings) : Backend {
    @Volatile
    private var call: Call? = null

    init {
        require(settings.configured) { "No gateway URL set" }
    }

    override fun send(text: String, conversation: String, onDelta: (String) -> Unit): String {
        require(text.isNotBlank()) { "Empty message" }
        val c = client.newCall(request(settings, text, conversation))
        call = c
        try {
            c.execute().use { return read(it, onDelta) }
        } catch (e: JSONException) {
            throw IOException("Malformed reply from the gateway", e)
        }
    }

    override fun cancel() {
        call?.cancel()
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val CONNECT_TIMEOUT_S = 10L

        // Agent turns can run tools for minutes; the stream stays open meanwhile.
        private const val READ_TIMEOUT_S = 300L

        private val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /** Asks for replies that fit a 2.9-inch screen; the agent's own persona still applies. */
        const val STYLE = "The user is on a tiny 2.9-inch screen. Reply briefly in plain text, no tables."

        /** The `user` value the gateway derives a session from. */
        fun sessionUser(conversation: String): String = "ribbitos-$conversation"

        /** Builds the chat-completions request for one new message. */
        fun request(s: Settings, text: String, conversation: String): Request {
            require(conversation.isNotBlank()) { "No conversation id" }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", STYLE))
                .put(JSONObject().put("role", "user").put("content", text))
            val body = JSONObject()
                .put("model", s.agent)
                .put("stream", true)
                .put("user", sessionUser(conversation))
                .put("messages", messages)
            return Request.Builder()
                .url(s.baseUrl.trimEnd('/') + "/chat/completions")
                .apply { if (s.token.isNotBlank()) header("Authorization", "Bearer ${s.token}") }
                .header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(JSON))
                .build()
        }

        private fun read(resp: Response, onDelta: (String) -> Unit): String {
            if (!resp.isSuccessful) throw IOException(errorMessage(resp.code, resp.body?.string().orEmpty()))
            val body = resp.body ?: throw IOException("Empty response")
            if (resp.header("Content-Type").orEmpty().contains("application/json")) {
                // The gateway ignored stream=true.
                return ChatChunk.fullMessage(body.string()).take(Limits.MAX_REPLY_CHARS).also(onDelta)
            }
            return readStream(body.source(), onDelta)
        }

        /**
         * Reads a reply stream up to `[DONE]`, the end of the stream, [Limits.MAX_REPLY_CHARS] of
         * text or [Limits.MAX_STREAM_LINES] lines, whichever comes first.
         */
        fun readStream(source: BufferedSource, onDelta: (String) -> Unit): String {
            val reply = Reply(onDelta)
            val parser = SseParser { _, data -> reply.accept(ChatChunk.parse(data)) }
            var lines = 0
            while (!reply.done && lines < Limits.MAX_STREAM_LINES) {
                parser.line(source.readUtf8Line() ?: break)
                lines++
            }
            if (!reply.done) parser.finish()
            return reply.text
        }

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404

        private fun errorMessage(code: Int, body: String): String = when (code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> "Gateway rejected the token (HTTP $code)"
            HTTP_NOT_FOUND -> "No chat endpoint: enable gateway.http.endpoints.chatCompletions"
            else -> errorDetail(body) ?: "HTTP $code"
        }

        /** The OpenAI-style `error.message`, if the body has one. */
        private fun errorDetail(body: String): String? = try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null // Not every error body is JSON; the status code still explains the failure.
        }
    }

    /** Collects one streamed reply, up to [Limits.MAX_REPLY_CHARS]. */
    private class Reply(private val onDelta: (String) -> Unit) {
        private val sb = StringBuilder()

        /** True after `[DONE]` or once the reply cap is reached. */
        var done = false
            private set

        val text: String get() = sb.toString()

        fun accept(chunk: ChatChunk) {
            when (chunk) {
                is ChatChunk.Text -> append(chunk.delta)
                is ChatChunk.Failed -> throw IOException(chunk.message)
                ChatChunk.Done -> done = true
                ChatChunk.Other -> Unit
            }
        }

        private fun append(delta: String) {
            val piece = delta.take(Limits.MAX_REPLY_CHARS - sb.length)
            if (piece.isNotEmpty()) {
                sb.append(piece)
                onDelta(piece)
            }
            done = sb.length >= Limits.MAX_REPLY_CHARS
        }
    }
}
