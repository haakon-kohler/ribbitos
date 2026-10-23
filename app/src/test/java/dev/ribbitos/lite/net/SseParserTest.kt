package dev.ribbitos.lite.net

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    private val events = mutableListOf<Pair<String?, String>>()
    private val parser = SseParser { event, data -> events += event to data }

    private fun feed(vararg lines: String) = lines.forEach(parser::line)

    @Test
    fun blankLineDispatchesTheBufferedEvent() {
        feed("data: one", "")
        assertEquals(listOf(null to "one"), events)
    }

    @Test
    fun carriageReturnsAreTolerated() {
        feed("data: one\r", "\r")
        assertEquals(listOf(null to "one"), events)
    }

    @Test
    fun commentsAndKeepalivesAreIgnored() {
        feed(": keepalive", "data: one", ": another", "")
        assertEquals(listOf(null to "one"), events)
    }

    @Test
    fun eventNameIsReportedAndThenReset() {
        feed("event: progress", "data: a", "", "data: b", "")
        assertEquals(listOf("progress" to "a", null to "b"), events)
    }

    @Test
    fun multiLineDataIsJoinedWithNewlines() {
        feed("data: first", "data: second", "")
        assertEquals(listOf(null to "first\nsecond"), events)
    }

    @Test
    fun valueWithoutSpaceAfterColonIsKept() {
        feed("data:tight", "")
        assertEquals(listOf(null to "tight"), events)
    }

    @Test
    fun fieldWithoutColonHasEmptyValue() {
        feed("data", "data: x", "")
        assertEquals(listOf(null to "\nx"), events)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        feed("id: 7", "retry: 1000", "data: x", "")
        assertEquals(listOf(null to "x"), events)
    }

    @Test
    fun completeJsonObjectsSplitWithoutBlankLines() {
        feed("data: {\"a\":1}", "data: {\"b\":2}", "data: [DONE]")
        parser.finish()
        assertEquals(listOf(null to "{\"a\":1}", null to "{\"b\":2}", null to "[DONE]"), events)
    }

    @Test
    fun incompleteJsonKeepsBuffering() {
        feed("data: {\"a\":", "data: 1}", "")
        assertEquals(listOf(null to "{\"a\":\n1}"), events)
    }

    @Test
    fun finishFlushesATrailingEvent() {
        feed("data: tail")
        parser.finish()
        assertEquals(listOf(null to "tail"), events)
    }

    @Test
    fun emptyBlocksDispatchNothing() {
        feed("", "", ": only a comment", "")
        parser.finish()
        assertEquals(emptyList<Pair<String?, String>>(), events)
    }
}
