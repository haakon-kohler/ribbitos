package dev.ribbitos.lite.net

import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Style
import dev.ribbitos.lite.Transcript
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiBackendTest {
    private val server = MockWebServer()
    private lateinit var settings: Settings

    @Before
    fun start() {
        server.start()
        settings = Settings(Provider.OPENAI, server.url("/v1").toString(), "sk-test", "gpt-6.1-sol")
    }

    @After
    fun stop() = server.shutdown()

    private fun user(t: String) = Transcript.Message(Transcript.USER, t)

    private fun bot(t: String) = Transcript.Message(Transcript.ASSISTANT, t)

    private fun reply(vararg parts: String): MockResponse {
        val deltas = parts.joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" }
        return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(deltas + "data: [DONE]\n\n")
    }

    @Test
    fun streamsTheReply() {
        server.enqueue(reply("Bon", "jour"))
        val deltas = mutableListOf<String>()
        assertEquals("Bonjour", OpenAiBackend(settings).send("hi", emptyList(), "c") { deltas += it })
        assertEquals(listOf("Bon", "jour"), deltas)
    }

    @Test
    fun sendsTheKeyModelStyleAndTheConversation() {
        server.enqueue(reply("ok"))
        val earlier = listOf(user("a"), bot("b"), Transcript.Message(Transcript.ASSISTANT, "boom", error = true))
        OpenAiBackend(settings).send("c", earlier, "conv") {}
        val req = server.takeRequest(1, TimeUnit.SECONDS) ?: error("no request")
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("gpt-6.1-sol", body.getString("model"))
        assertFalse("the session id is OpenClaw's business", body.has("user"))
        val m = body.getJSONArray("messages")
        val sent = (0 until m.length()).map {
            val o = m.getJSONObject(it)
            o.getString("role") to o.getString("content")
        }
        assertEquals(listOf("system" to Style.SYSTEM, "user" to "a", "assistant" to "b", "user" to "c"), sent)
    }

    @Test
    fun notFoundSuggestsCheckingTheModel() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"no such model"}}"""))
        val e = assertThrows(IOException::class.java) { OpenAiBackend(settings).send("hi", emptyList(), "c") {} }
        assertEquals("Not found (HTTP 404): check the URL and the model name", e.message)
    }

    @Test
    fun rateLimitUsesTheServersMessage() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Rate limit reached"}}"""))
        val e = assertThrows(IOException::class.java) { OpenAiBackend(settings).send("hi", emptyList(), "c") {} }
        assertEquals("Rate limit reached", e.message)
    }

    @Test
    fun cancelBeforeSendIsHarmless() = OpenAiBackend(settings).cancel()

    @Test
    fun aKeyIsRequired() {
        assertThrows(IllegalArgumentException::class.java) { OpenAiBackend(settings.copy(token = "")) }
        assertThrows(IllegalArgumentException::class.java) { OpenAiBackend(settings.copy(provider = Provider.CLAUDE)) }
    }

    @Test
    fun theConversationMustEndWithTheUser() {
        assertThrows(IllegalArgumentException::class.java) { OpenAiBackend.body(settings, listOf(bot("x"))) }
    }
}
