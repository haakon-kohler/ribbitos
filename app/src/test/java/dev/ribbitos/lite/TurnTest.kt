package dev.ribbitos.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TurnTest {
    private val partials = mutableListOf<String>()
    private var finished: Transcript.Message? = null
    private var posts = 0
    private val listener = object : Turn.Listener {
        override fun onPartial(text: String) {
            partials += text
        }

        override fun onFinished(reply: Transcript.Message) {
            finished = reply
        }
    }
    private val post: (Runnable) -> Unit = {
        posts++
        it.run()
    }

    private fun run(backend: Backend) = Turn(backend, "hi", "conv").start({ it.run() }, post, listener)

    @Test
    fun streamsPartialsThenFinishesWithTheReply() {
        run(
            FakeBackend { _, onDelta ->
                onDelta("Hel")
                onDelta("lo")
                "Hello"
            },
        )
        assertEquals(listOf("Hel", "Hello"), partials)
        assertEquals(Transcript.Message(Transcript.ASSISTANT, "Hello"), finished)
        assertEquals("every callback goes through post", 3, posts)
    }

    @Test
    fun sendsTheTextAndConversation() {
        val backend = FakeBackend()
        run(backend)
        assertEquals(listOf("hi" to "conv"), backend.sent)
    }

    @Test
    fun blankReplyIsShownAsNoReply() {
        run(FakeBackend { _, _ -> " " })
        assertEquals(Turn.NO_REPLY, finished?.text)
    }

    @Test
    fun failureBecomesAnErrorMessage() {
        run(FakeBackend { _, _ -> throw IOException("gateway down") })
        assertEquals(Transcript.Message(Transcript.ASSISTANT, "gateway down", error = true), finished)
    }

    @Test
    fun failureWithoutMessageIsGeneric() {
        run(FakeBackend { _, _ -> throw IOException() })
        assertEquals(Turn.FAILED, finished?.text)
        assertTrue(finished?.error == true)
    }

    @Test
    fun stopKeepsWhatArrivedAndIsNotAnError() {
        val executor = QueueExecutor()
        val backend = FakeBackend { _, onDelta ->
            onDelta("half")
            failIfCancelled()
            "never"
        }
        val turn = Turn(backend, "hi", "conv")
        turn.start(executor, post, listener)
        turn.stop()
        executor.runAll()
        assertTrue(backend.cancelled)
        assertEquals(Transcript.Message(Transcript.ASSISTANT, "half"), finished)
    }

    @Test
    fun stopBeforeAnythingArrivedSaysStopped() {
        val executor = QueueExecutor()
        val turn = Turn(FakeBackend { _, _ -> failIfCancelled().let { "never" } }, "hi", "conv")
        turn.start(executor, post, listener)
        turn.stop()
        executor.runAll()
        assertEquals(Transcript.Message(Transcript.ASSISTANT, Turn.STOPPED), finished)
        assertFalse(finished?.error == true)
    }

    @Test
    fun blankTextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Turn(FakeBackend(), " ", "conv") }
    }

    @Test
    fun blankConversationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Turn(FakeBackend(), "hi", "") }
    }
}
