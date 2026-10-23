package dev.ribbitos.lite

import java.io.IOException
import java.util.concurrent.Executor

/**
 * One message and its reply. Runs [backend] on an [Executor] and reports back through a
 * [Listener], always via the supplied `post` (the UI thread in the app).
 */
class Turn(
    private val backend: Backend,
    private val text: String,
    private val conversation: String,
    private val history: List<Transcript.Message> = emptyList(),
) {
    /** Receives a turn's progress; both callbacks arrive through `post`. */
    interface Listener {
        /** The reply so far, growing as it streams in. */
        fun onPartial(text: String)

        /** The final message for the transcript: the reply, what arrived before a stop, or an error. */
        fun onFinished(reply: Transcript.Message)
    }

    @Volatile
    private var stopped = false

    init {
        require(text.isNotBlank()) { "A turn needs a message" }
        require(conversation.isNotBlank()) { "A turn needs a conversation id" }
    }

    /** Starts the turn; returns at once. */
    fun start(executor: Executor, post: (Runnable) -> Unit, listener: Listener) {
        executor.execute {
            val reply = run(post, listener)
            post(Runnable { listener.onFinished(reply) })
        }
    }

    /** Stops the turn; whatever already arrived is kept. */
    fun stop() {
        stopped = true
        backend.cancel()
    }

    private fun run(post: (Runnable) -> Unit, listener: Listener): Transcript.Message {
        val partial = StringBuilder()
        return try {
            val reply = backend.send(text, history, conversation) { delta ->
                partial.append(delta)
                val shown = partial.toString()
                post(Runnable { listener.onPartial(shown) })
            }
            Transcript.Message(Transcript.ASSISTANT, reply.ifBlank { NO_REPLY })
        } catch (e: IOException) {
            when {
                stopped -> Transcript.Message(Transcript.ASSISTANT, partial.toString().ifBlank { STOPPED })
                else -> Transcript.Message(Transcript.ASSISTANT, e.message ?: FAILED, error = true)
            }
        }
    }

    companion object {
        const val NO_REPLY = "(no reply)"
        const val STOPPED = "Stopped."
        const val FAILED = "Something went wrong"
    }
}
