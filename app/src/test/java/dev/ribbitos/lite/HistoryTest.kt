package dev.ribbitos.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun user(t: String) = Transcript.Message(Transcript.USER, t)

    private fun bot(t: String) = Transcript.Message(Transcript.ASSISTANT, t)

    @Test
    fun aFirstMessageIsJustThatMessage() = assertEquals(listOf(user("hi")), History.forTurn(emptyList(), "hi"))

    @Test
    fun earlierTurnsComeFirst() =
        assertEquals(listOf(user("a"), bot("b"), user("c")), History.forTurn(listOf(user("a"), bot("b")), "c"))

    @Test
    fun errorRepliesAreLeftOutAndTheUserMessagesMerge() {
        val earlier = listOf(user("a"), Transcript.Message(Transcript.ASSISTANT, "boom", error = true))
        assertEquals(listOf(user("a\n\nb")), History.forTurn(earlier, "b"))
    }

    @Test
    fun blankMessagesAreLeftOut() =
        assertEquals(
            listOf(user("a"), bot("b"), user("c")),
            History.forTurn(listOf(user("a"), bot(" "), bot("b")), "c")
        )

    @Test
    fun itAlwaysStartsWithTheUser() =
        assertEquals(
            listOf(user("a"), bot("b"), user("c")),
            History.forTurn(listOf(bot("hello"), user("a"), bot("b")), "c")
        )

    @Test
    fun onlyTheNewestMessagesGo() {
        val earlier = List(Limits.MAX_HISTORY_MESSAGES + EXTRA) { if (it % 2 == 0) user("u$it") else bot("b$it") }
        val sent = History.forTurn(earlier, "new")
        assertTrue(sent.size <= Limits.MAX_HISTORY_MESSAGES + 1)
        assertEquals(Transcript.USER, sent.first().role)
        assertEquals(user("new"), sent.last())
    }

    @Test
    fun oldMessagesGoFirstWhenTheTextIsTooLong() {
        val big = "x".repeat(Limits.MAX_HISTORY_CHARS / 2)
        val sent = History.forTurn(listOf(user(big), bot(big), user("q"), bot("a")), "new")
        assertEquals(listOf(user("q"), bot("a"), user("new")), sent)
    }

    @Test
    fun theNewMessageAlwaysGoesEvenIfHuge() {
        val huge = "x".repeat(Limits.MAX_HISTORY_CHARS + 1)
        assertEquals(listOf(user(huge)), History.forTurn(listOf(user("a"), bot("b")), huge))
    }

    @Test
    fun blankNewMessageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { History.forTurn(emptyList(), " ") }
    }

    private companion object {
        // Even, so the earlier messages end with a reply and the new message stands alone.
        const val EXTRA = 8
    }
}
