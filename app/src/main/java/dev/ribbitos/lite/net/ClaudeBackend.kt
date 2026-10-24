package dev.ribbitos.lite.net

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.http.StreamResponse
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import dev.ribbitos.lite.Backend
import dev.ribbitos.lite.History
import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.Prompt
import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Style
import dev.ribbitos.lite.Transcript
import dev.ribbitos.lite.camera.Photo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * Talks to Anthropic's Claude API through the official Java SDK, streaming the reply. The API keeps
 * no state, so every request carries the conversation so far ([History.forTurn]).
 *
 * The history is sent as text only. Claude's thinking blocks from earlier turns aren't kept: that
 * keeps the transcript a plain list of messages, at the cost of Claude not seeing its own earlier
 * reasoning. Nothing is edited after the fact, so the history-editing check never applies.
 */
class ClaudeBackend(private val settings: Settings) : Backend {
    private val client: AnthropicClient

    @Volatile
    private var stream: StreamResponse<BetaRawMessageStreamEvent>? = null

    @Volatile
    private var cancelled = false

    init {
        require(settings.provider == Provider.CLAUDE) { "Not Claude settings: ${settings.provider}" }
        require(settings.configured) { "Claude needs a URL and an API key" }
        client = AnthropicOkHttpClient.builder()
            .apiKey(settings.token.trim())
            .baseUrl(settings.baseUrl.trim().trimEnd('/'))
            .maxRetries(1)
            .build()
    }

    override fun send(
        prompt: Prompt,
        history: List<Transcript.Message>,
        conversation: String,
        onDelta: (String) -> Unit,
    ): String {
        val params = params(settings, History.forTurn(history, prompt.text), prompt.photo)
        try {
            client.beta().messages().createStreaming(params).use {
                stream = it
                return read(it, onDelta)
            }
        } catch (e: AnthropicServiceException) {
            throw IOException(describe(e), e)
        } catch (e: AnthropicException) {
            throw IOException(if (cancelled) CANCELED else "Can't reach Claude: ${e.message}", e)
        } finally {
            stream = null
            client.close()
        }
    }

    override fun cancel() {
        cancelled = true
        stream?.close()
    }

    private fun read(response: StreamResponse<BetaRawMessageStreamEvent>, onDelta: (String) -> Unit): String {
        val reply = StringBuilder()
        var refused = false
        val events = response.stream().iterator()
        var seen = 0
        while (open(seen, reply) && events.hasNext()) {
            val event = events.next()
            seen++
            event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent {
                val piece = it.text().take(Limits.MAX_REPLY_CHARS - reply.length)
                reply.append(piece)
                if (piece.isNotEmpty()) onDelta(piece)
            }
            event.messageDelta().flatMap { it.delta().stopReason() }.ifPresent {
                refused = it == BetaStopReason.REFUSAL
            }
        }
        // Closing the stream from cancel() can end it quietly instead of failing the read.
        if (cancelled) throw IOException(CANCELED)
        if (refused && reply.isEmpty()) throw IOException(REFUSED)
        return reply.toString()
    }

    /** Whether to keep reading: not stopped, and within the event and reply caps. */
    private fun open(seen: Int, reply: StringBuilder) =
        !cancelled && seen < Limits.MAX_STREAM_LINES && reply.length < Limits.MAX_REPLY_CHARS

    companion object {
        /** Room for adaptive thinking plus a reply; the visible reply is still cut at [Limits.MAX_REPLY_CHARS]. */
        const val MAX_TOKENS = 16_000L
        const val REFUSED = "Claude declined to answer this."
        const val CANCELED = "Canceled"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        private const val ANTHROPIC_HOST = "api.anthropic.com"

        // Models that take server-side refusal fallbacks (`fallbacks: "default"`) on the Claude API.
        private val FALLBACK_MODELS = listOf("claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5")

        /** The streaming request: style prompt, low effort for quick chat replies, the conversation. */
        fun params(s: Settings, conversation: List<Transcript.Message>, photo: ByteArray? = null): MessageCreateParams {
            require(conversation.firstOrNull()?.role == Transcript.USER) { "The conversation must start with the user" }
            val builder = MessageCreateParams.builder()
                .model(s.model)
                .maxTokens(MAX_TOKENS)
                .system(Style.SYSTEM)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            conversation.dropLast(1).forEach {
                when (it.role) {
                    Transcript.USER -> builder.addUserMessage(it.text)
                    else -> builder.addAssistantMessage(it.text)
                }
            }
            val newest = conversation.last().text
            // The photo rides only with the newest message; earlier ones are history.
            if (photo == null) {
                builder.addUserMessage(newest)
            } else {
                builder.addUserMessageOfBetaContentBlockParams(withPhoto(newest, photo))
            }
            if (takesFallbacks(s)) {
                builder.addBeta(FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            return builder.build()
        }

        private fun withPhoto(text: String, photo: ByteArray): List<BetaContentBlockParam> = listOf(
            BetaContentBlockParam.ofImage(
                BetaImageBlockParam.builder()
                    .source(
                        BetaBase64ImageSource.builder()
                            .data(Photo.base64(photo))
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .build(),
                    )
                    .build(),
            ),
            BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(text).build()),
        )

        /** Refusal fallbacks only exist on Anthropic's own API, for some models. */
        fun takesFallbacks(s: Settings): Boolean =
            s.baseUrl.trim().toHttpUrlOrNull()?.host == ANTHROPIC_HOST &&
                FALLBACK_MODELS.any { s.model.trim().startsWith(it) }

        private fun describe(e: AnthropicServiceException): String = when (e) {
            is UnauthorizedException -> "Claude rejected the API key (HTTP 401)"
            is PermissionDeniedException -> "This key can't use that model (HTTP 403)"
            is NotFoundException -> "Not found (HTTP 404): check the model name"
            is RateLimitException -> "Rate limited by Claude; try again in a moment"
            else -> "Claude error (HTTP ${e.statusCode()}): ${e.message}"
        }
    }
}
