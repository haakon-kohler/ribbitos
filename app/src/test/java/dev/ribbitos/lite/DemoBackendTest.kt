package dev.ribbitos.lite

import dev.ribbitos.lite.net.OpenClawBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DemoBackendTest {
    private val fast = DemoBackend(wordDelayMs = 0)

    @Test
    fun streamsTheReplyWordByWord() {
        val deltas = mutableListOf<String>()
        val reply = fast.send("hi", "c") { deltas += it }
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
        val e = assertThrows(IOException::class.java) { fast.send("show an Error", "c") {} }
        assertEquals(DemoBackend.ERROR, e.message)
    }

    @Test
    fun cancelStopsTheStream() {
        fast.cancel()
        assertThrows(IOException::class.java) { fast.send("hi", "c") {} }
    }

    @Test
    fun interruptionIsAnIOExceptionAndKeepsTheFlag() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(IOException::class.java) { DemoBackend(wordDelayMs = 1).send("hi", "c") {} }
            assertTrue(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun blankMessageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { fast.send(" ", "c") {} }
    }

    @Test
    fun theDemoUrlSelectsTheDemo() {
        assertTrue(Settings("http://demo/v1").demo)
        assertTrue(Wiring.defaultBackend(Settings("http://demo/v1")) is DemoBackend)
    }

    @Test
    fun anyOtherUrlSelectsOpenClaw() {
        assertFalse(Settings("http://gw:18789/v1").demo)
        assertFalse(Settings("").demo)
        assertTrue(Wiring.defaultBackend(Settings("http://gw:18789/v1")) is OpenClawBackend)
    }

    private companion object {
        const val LONG_LINES = 5
    }
}
