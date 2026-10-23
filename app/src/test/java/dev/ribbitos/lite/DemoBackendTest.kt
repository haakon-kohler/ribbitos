package dev.ribbitos.lite

import dev.ribbitos.lite.net.ClaudeBackend
import dev.ribbitos.lite.net.OpenAiBackend
import dev.ribbitos.lite.net.OpenClawBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DemoBackendTest {
    private val fast = DemoBackend(wordDelayMs = 0)

    @Test
    fun streamsTheReplyWordByWord() {
        val deltas = mutableListOf<String>()
        val reply = fast.send("hi", emptyList(), "c") { deltas += it }
        assertEquals(DemoBackend.replyTo("hi"), reply)
        assertEquals(reply, deltas.joinToString(""))
        assertTrue(deltas.size > 1)
    }

    @Test
    fun echoesTheMessage() = assertTrue(DemoBackend.replyTo("  hello  ").contains("\"hello\""))

    @Test
    fun longAsksForAScrollingReply() {
        val reply = DemoBackend.replyTo("something LONG please")
        assertTrue(reply.lines().size > LONG_LINES)
    }

    @Test
    fun errorShowsAFailure() {
        val e = assertThrows(IOException::class.java) { fast.send("show an Error", emptyList(), "c") {} }
        assertEquals(DemoBackend.ERROR, e.message)
    }

    @Test
    fun cancelStopsTheStream() {
        fast.cancel()
        assertThrows(IOException::class.java) { fast.send("hi", emptyList(), "c") {} }
    }

    @Test
    fun interruptionIsAnIOExceptionAndKeepsTheFlag() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(IOException::class.java) { DemoBackend(wordDelayMs = 1).send("hi", emptyList(), "c") {} }
            assertTrue(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun blankMessageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { fast.send(" ", emptyList(), "c") {} }
    }

    @Test
    fun eachProviderGetsItsBackend() {
        assertTrue(Wiring.defaultBackend(Settings(Provider.DEMO)) is DemoBackend)
        assertTrue(Wiring.defaultBackend(Settings(Provider.OPENCLAW, "http://gw:18789/v1")) is OpenClawBackend)
        assertTrue(Wiring.defaultBackend(Settings(Provider.OPENAI, token = "k")) is OpenAiBackend)
        assertTrue(Wiring.defaultBackend(Settings(Provider.CLAUDE, token = "k")) is ClaudeBackend)
    }

    private companion object {
        const val LONG_LINES = 5
    }
}
