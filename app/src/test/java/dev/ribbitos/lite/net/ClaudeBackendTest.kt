package dev.ribbitos.lite.net

import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Style
import dev.ribbitos.lite.Transcript
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The Claude backend against a fake Anthropic API speaking the real streaming event format. */
class ClaudeBackendTest {
    private val server = MockWebServer()
    private lateinit var settings: Settings

    @Before
    fun start() {
        server.start()
        settings = Settings(Provider.CLAUDE, server.url("/").toString(), "sk-ant-test", "claude-opus-5-5")
    }

    @After
    fun stop() = server.shutdown()

    private fun user(t: String) = Transcript.Message(Transcript.USER, t)

    private fun bot(t: String) = Transcript.Message(Transcript.ASSISTANT, t)

    private fun event(type: String, data: String) = "event: $type\ndata: $data\n\n"

    private fun stream(vararg texts: String, stopReason: String = "end_turn"): MockResponse {
        val body = StringBuilder(event("message_start", MESSAGE_START))
        body.append(event("content_block_start", BLOCK_START))
        texts.forEach { body.append(event("content_block_delta", textDelta(it))) }
        body.append(event("content_block_stop", BLOCK_STOP))
            .append(event("message_delta", messageDelta(stopReason)))
            .append(event("message_stop", MESSAGE_STOP))
        return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body.toString())
    }

    private fun textDelta(t: String) =
        """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$t"}}"""

    private fun messageDelta(stop: String) =
        """{"type":"message_delta","delta":{"stop_reason":"$stop","stop_sequence":null},"usage":{"output_tokens":5}}"""

    private fun send(
        text: String = "hi",
        history: List<Transcript.Message> = emptyList(),
        deltas: MutableList<String> = mutableListOf(),
    ) = ClaudeBackend(settings).send(text, history, "conv") { deltas += it }

    @Test
    fun streamsTheReply() {
        server.enqueue(stream("Bon", "jour"))
        val deltas = mutableListOf<String>()
        assertEquals("Bonjour", send(deltas = deltas))
        assertEquals(listOf("Bon", "jour"), deltas)
    }

    @Test
    fun sendsTheKeyModelStyleAndConversation() {
        server.enqueue(stream("ok"))
        send("c", listOf(user("a"), bot("b")))
        val req = server.takeRequest(1, TimeUnit.SECONDS) ?: error("no request")
        assertEquals("/v1/messages", req.path?.substringBefore('?'))
        assertEquals("sk-ant-test", req.getHeader("x-api-key"))
        assertTrue(req.getHeader("anthropic-version").orEmpty().isNotBlank())
        val body = JSONObject(req.body.readUtf8())
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals(ClaudeBackend.MAX_TOKENS, body.getLong("max_tokens"))
        assertTrue(body.getBoolean("stream"))
        assertEquals(Style.SYSTEM, body.getString("system"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        val m = body.getJSONArray("messages")
        assertEquals(
            listOf("user", "assistant", "user"),
            (0 until m.length()).map { m.getJSONObject(it).getString("role") }
        )
        assertFalse("fallbacks only go to Anthropic's own API", body.has("fallbacks"))
    }

    @Test
    fun refusalFallbacksAreOnForAnthropicsApiAndModelsThatTakeThem() {
        val api = Settings(Provider.CLAUDE, token = "k")
        assertTrue(ClaudeBackend.takesFallbacks(api))
        assertTrue(ClaudeBackend.takesFallbacks(api.copy(model = "claude-sonnet-5-5")))
        assertFalse(ClaudeBackend.takesFallbacks(api.copy(model = "claude-haiku-5-5")))
        assertFalse(ClaudeBackend.takesFallbacks(api.copy(baseUrl = "https://proxy.example")))
        val params = ClaudeBackend.params(api, listOf(user("hi")))
        assertEquals("default", params._additionalBodyProperties()["fallbacks"]?.asString()?.orElse(null))
    }

    @Test
    fun aRefusalWithNothingSaidIsAnError() {
        server.enqueue(stream(stopReason = "refusal"))
        assertEquals(ClaudeBackend.REFUSED, assertThrows(IOException::class.java) { send() }.message)
    }

    @Test
    fun aRefusalAfterSomeTextKeepsTheText() {
        server.enqueue(stream("partial", stopReason = "refusal"))
        assertEquals("partial", send())
    }

    @Test
    fun badKeyIsExplained() = assertHttpError(401, "Claude rejected the API key (HTTP 401)")

    @Test
    fun forbiddenIsExplained() = assertHttpError(403, "This key can't use that model (HTTP 403)")

    @Test
    fun unknownModelIsExplained() = assertHttpError(404, "Not found (HTTP 404): check the model name")

    @Test
    fun rateLimitIsExplained() = assertHttpError(429, "Rate limited by Claude; try again in a moment")

    @Test
    fun otherErrorsCarryTheStatus() {
        server.enqueue(error(400, "invalid_request_error", "messages: bad"))
        assertTrue(
            assertThrows(IOException::class.java) { send() }.message.orEmpty().startsWith("Claude error (HTTP 400)")
        )
    }

    private fun error(code: Int, type: String, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"type":"error","error":{"type":"$type","message":"$message"}}""")

    private fun assertHttpError(code: Int, message: String) {
        // Retries (the SDK retries 429 and 5xx once) see the same answer.
        repeat(2) { server.enqueue(error(code, "error", "nope")) }
        assertEquals(message, assertThrows(IOException::class.java) { send() }.message)
    }

    @Test
    fun droppedConnectionIsAnIOException() {
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        assertTrue(assertThrows(IOException::class.java) { send() }.message.orEmpty().startsWith("Can't reach Claude"))
    }

    @Test
    fun cancelFromAnotherThreadStopsTheStream() {
        server.enqueue(stream("slow", "er").throttleBody(64, 1, TimeUnit.SECONDS))
        val backend = ClaudeBackend(settings)
        val canceller = Thread {
            Thread.sleep(CANCEL_AFTER_MS)
            backend.cancel()
        }
        canceller.start()
        assertThrows(IOException::class.java) { backend.send("hi", emptyList(), "c") {} }
        canceller.join()
    }

    @Test
    fun cancelBeforeSendIsHarmless() = ClaudeBackend(settings).cancel()

    @Test
    fun replyIsCutAtTheCap() {
        server.enqueue(
            stream("x".repeat(Limits.MAX_REPLY_CHARS / 2 + 1), "y".repeat(Limits.MAX_REPLY_CHARS / 2 + 1), "z")
        )
        assertEquals(Limits.MAX_REPLY_CHARS, send().length)
    }

    @Test
    fun aKeyIsRequired() {
        assertThrows(IllegalArgumentException::class.java) { ClaudeBackend(settings.copy(token = " ")) }
        assertThrows(IllegalArgumentException::class.java) { ClaudeBackend(settings.copy(provider = Provider.OPENAI)) }
    }

    @Test
    fun theConversationMustStartWithTheUser() {
        assertThrows(IllegalArgumentException::class.java) { ClaudeBackend.params(settings, listOf(bot("x"))) }
    }

    private companion object {
        const val CANCEL_AFTER_MS = 300L
        const val MESSAGE_START = """{"type":"message_start","message":{"id":"msg_1","type":"message",""" +
            """"role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,""" +
            """"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":1}}}"""
        const val BLOCK_START = """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""
        const val BLOCK_STOP = """{"type":"content_block_stop","index":0}"""
        const val MESSAGE_STOP = """{"type":"message_stop"}"""
    }
}
