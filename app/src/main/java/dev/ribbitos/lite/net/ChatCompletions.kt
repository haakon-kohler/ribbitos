package dev.ribbitos.lite.net

import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.camera.Photo
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
 * One streamed `POST {baseUrl}/chat/completions`: the OpenAI-compatible call that both OpenClaw's
 * gateway and OpenAI speak. Backends build the body; this sends it and reads the reply, within
 * [Limits.MAX_REPLY_CHARS] and [Limits.MAX_STREAM_LINES].
 */
class ChatCompletions(
    private val baseUrl: String,
    private val key: String,
    /** What a 404 most likely means for this server. */
    private val notFoundHint: String,
) {
    @Volatile
    private var call: Call? = null

    /** Sends [body] (with `stream: true` set here) and streams the reply text through [onDelta]. */
    fun stream(body: JSONObject, onDelta: (String) -> Unit): String {
        val c = client.newCall(request(body))
        call = c
        try {
            c.execute().use { return read(it, onDelta) }
        } catch (e: JSONException) {
            throw IOException("Malformed reply from the server", e)
        }
    }

    /** Aborts the call in flight, if any. */
    fun cancel() {
        call?.cancel()
    }

    /** The HTTP request for [body]. */
    fun request(body: JSONObject): Request = Request.Builder()
        .url(baseUrl.trim().trimEnd('/') + "/chat/completions")
        .apply { if (key.isNotBlank()) header("Authorization", "Bearer ${key.trim()}") }
        .header("Accept", "text/event-stream")
        .post(body.put("stream", true).toString().toRequestBody(JSON))
        .build()

    private fun read(resp: Response, onDelta: (String) -> Unit): String {
        if (!resp.isSuccessful) throw IOException(errorMessage(resp.code, resp.body?.string().orEmpty()))
        val body = resp.body ?: throw IOException("Empty response")
        if (resp.header("Content-Type").orEmpty().contains("application/json")) {
            // The server ignored stream=true.
            return ChatChunk.fullMessage(body.string()).take(Limits.MAX_REPLY_CHARS).also(onDelta)
        }
        return readStream(body.source(), onDelta)
    }

    private fun errorMessage(code: Int, body: String): String = when (code) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> "The server rejected the key (HTTP $code)"
        HTTP_NOT_FOUND -> notFoundHint
        else -> errorDetail(body) ?: "HTTP $code"
    }

    companion object {
        /** A user message: plain text, or text plus an `image_url` part for a photo. */
        fun userMessage(text: String, photo: ByteArray?): JSONObject {
            val content: Any = if (photo == null) {
                text
            } else {
                val image = JSONObject().put("url", Photo.dataUri(photo))
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", text))
                    .put(JSONObject().put("type", "image_url").put("image_url", image))
            }
            return JSONObject().put("role", "user").put("content", content)
        }

        private val JSON = "application/json".toMediaType()
        private const val CONNECT_TIMEOUT_S = 10L

        // Agent turns can run tools for minutes; the stream stays open meanwhile.
        private const val READ_TIMEOUT_S = 300L
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404

        private val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

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
