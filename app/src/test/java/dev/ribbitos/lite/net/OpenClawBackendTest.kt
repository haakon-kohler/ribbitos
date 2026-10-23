package dev.ribbitos.lite.net

import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.Provider
import dev.ribbitos.lite.Settings
import dev.ribbitos.lite.Transcript
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OpenClaw's backend, and the chat-completions client it shares with OpenAI. */
class OpenClawBackendTest {
    private val server = MockWebServer()
    private lateinit var settings: Settings

    @Before
    fun start() {
        server.start()
        settings = Settings(Provider.OPENCLAW, server.url("/v1").toString(), "t0k", "openclaw/r1")
    }

    @After
    fun stop() = server.shutdown()

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "data: $it\n\n" })

    private fun delta(text: String) = """{"choices":[{"delta":{"content":"$text"}}]}"""

    private val earlier = listOf(
        Transcript.Message(Transcript.USER, "old"),
        Transcript.Message(Transcript.ASSISTANT, "reply"),
    )

    private fun send(text: String = "hi", deltas: MutableList<String> = mutableListOf()) =
        OpenClawBackend(settings).send(text, earlier, "conv-1") { deltas += it }

    @Test
    fun streamsDeltasInOrderAndReturnsTheWholeReply() {
        server.enqueue(sse(delta("Hel"), delta("lo"), "[DONE]"))
        val deltas = mutableListOf<String>()
        assertEquals("Hello", send(deltas = deltas))
        assertEquals(listOf("Hel", "lo"), deltas)
    }

    @Test
    fun sendsTokenAgentSessionAndOnlyTheNewMessage() {
        server.enqueue(sse("[DONE]"))
        send("what's up")
        val req = server.takeRequest(1, TimeUnit.SECONDS) ?: error("no request")
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer t0k", req.getHeader("Authorization"))
        assertEquals("text/event-stream", req.getHeader("Accept"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("openclaw/r1", body.getString("model"))
        assertEquals(OpenClawBackend.sessionUser("conv-1"), body.getString("user"))
        assertTrue(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertEquals("the gateway keeps the history, so it isn't sent", 2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("what's up", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun noTokenMeansNoAuthorizationHeader() {
        val req = ChatCompletions("http://gw/v1", "", "").request(JSONObject())
        assertNull(req.header("Authorization"))
    }

    @Test
    fun trailingSlashInBaseUrlIsHandled() {
        val req = ChatCompletions("http://gw:18789/v1/", "", "").request(JSONObject())
        assertEquals("http://gw:18789/v1/chat/completions", req.url.toString())
    }

    @Test
    fun nonStreamedJsonReplyIsDeliveredWhole() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"Whole"}}]}"""),
        )
        val deltas = mutableListOf<String>()
        assertEquals("Whole", send(deltas = deltas))
        assertEquals(listOf("Whole"), deltas)
    }

    @Test
    fun malformedJsonReplyIsAnIOException() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"nope":1}"""))
        assertEquals("Malformed reply from the server", assertThrows(IOException::class.java) { send() }.message)
    }

    @Test
    fun unauthorizedNamesTheKey() = assertHttpError(401, "", "The server rejected the key (HTTP 401)")

    @Test
    fun forbiddenNamesTheKey() = assertHttpError(403, "", "The server rejected the key (HTTP 403)")

    @Test
    fun notFoundPointsAtTheEndpointSetting() =
        assertHttpError(404, "", "No chat endpoint: enable gateway.http.endpoints.chatCompletions")

    @Test
    fun serverErrorUsesTheErrorMessage() =
        assertHttpError(500, """{"error":{"message":"agent crashed"}}""", "agent crashed")

    @Test
    fun nonJsonErrorBodyFallsBackToTheStatus() = assertHttpError(502, "<html>bad gateway</html>", "HTTP 502")

    @Test
    fun blankErrorMessageFallsBackToTheStatus() =
        assertHttpError(500, """{"error":{"message":""}}""", "HTTP 500")

    private fun assertHttpError(code: Int, body: String, message: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        assertEquals(message, assertThrows(IOException::class.java) { send() }.message)
    }

    @Test
    fun errorMidStreamFailsAfterDeliveringWhatCameFirst() {
        server.enqueue(sse(delta("partial"), """{"error":{"message":"tool failed"}}"""))
        val deltas = mutableListOf<String>()
        assertEquals("tool failed", assertThrows(IOException::class.java) { send(deltas = deltas) }.message)
        assertEquals(listOf("partial"), deltas)
    }

    @Test
    fun streamWithoutDoneMarkerReturnsWhatArrived() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: ${delta("end")}"))
        assertEquals("end", send())
    }

    @Test
    fun droppedConnectionIsAnIOException() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThrows(IOException::class.java) { send() }
    }

    @Test
    fun cancelFromAnotherThreadAbortsTheStream() {
        server.enqueue(sse(delta("slow"), "[DONE]").throttleBody(1, 1, TimeUnit.SECONDS))
        val backend = OpenClawBackend(settings)
        val canceller = Thread {
            Thread.sleep(CANCEL_AFTER_MS)
            backend.cancel()
        }
        canceller.start()
        assertThrows(IOException::class.java) { backend.send("hi", emptyList(), "c") {} }
        canceller.join()
    }

    @Test
    fun cancelBeforeSendIsHarmless() = OpenClawBackend(settings).cancel()

    @Test
    fun unconfiguredOrForeignSettingsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OpenClawBackend(Settings(Provider.OPENCLAW)) }
        assertThrows(IllegalArgumentException::class.java) { OpenClawBackend(settings.copy(provider = Provider.DEMO)) }
    }

    @Test
    fun blankMessageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { send("  ") }
    }

    @Test
    fun blankConversationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { OpenClawBackend.body(settings, "hi", "") }
    }

    @Test
    fun replyIsCutAtTheCapAndReadingStops() {
        val chunk = "x".repeat(Limits.MAX_REPLY_CHARS / 2 + 1)
        val stream = Buffer().writeUtf8("data: ${delta(chunk)}\n\n".repeat(3) + "data: [DONE]\n\n")
        val deltas = mutableListOf<String>()
        val reply = ChatCompletions.readStream(stream) { deltas += it }
        assertEquals(Limits.MAX_REPLY_CHARS, reply.length)
        assertEquals(reply, deltas.joinToString(""))
        assertFalse("reading stopped before the end", stream.exhausted())
    }

    @Test
    fun streamIsCutAtTheLineCap() {
        val stream = Buffer()
            .writeUtf8(": keepalive\n".repeat(Limits.MAX_STREAM_LINES))
            .writeUtf8("data: ${delta("late")}\n\n")
        assertEquals("", ChatCompletions.readStream(stream) {})
        assertFalse(stream.exhausted())
    }

    private companion object {
        const val CANCEL_AFTER_MS = 200L
    }
}
