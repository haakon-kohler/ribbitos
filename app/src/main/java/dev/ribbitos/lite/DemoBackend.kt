package dev.ribbitos.lite

import java.io.IOException

/**
 * A backend that needs no server: it streams a canned reply word by word. Selected by setting the
 * gateway URL to `http://demo/v1`, so anyone can try ribbitOS before running an agent. Also the
 * smallest complete example of a [Backend].
 *
 * Say something with "long" in it for a reply that needs scrolling, or "error" to see a failure.
 */
class DemoBackend(private val wordDelayMs: Long = WORD_DELAY_MS) : Backend {
    @Volatile
    private var cancelled = false

    override fun send(text: String, conversation: String, onDelta: (String) -> Unit): String {
        require(text.isNotBlank()) { "Empty message" }
        if (text.contains("error", ignoreCase = true)) throw IOException(ERROR)
        val words = replyTo(text).split(' ')
        val reply = StringBuilder()
        for (i in words.indices) {
            pause()
            val piece = if (i == 0) words[i] else " ${words[i]}"
            reply.append(piece)
            onDelta(piece)
        }
        return reply.toString()
    }

    override fun cancel() {
        cancelled = true
    }

    private fun pause() {
        if (cancelled) throw IOException("Canceled")
        try {
            Thread.sleep(wordDelayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted", e)
        }
    }

    companion object {
        /** The gateway URL host that selects the demo. */
        const val HOST = "demo"
        const val ERROR = "Demo error: this is how a failed reply looks."
        private const val WORD_DELAY_MS = 60L

        /** The canned reply for [text]. */
        fun replyTo(text: String): String = if (text.contains("long", ignoreCase = true)) {
            LONG
        } else {
            "This is ribbitOS demo mode, so no agent is listening. You said: \"${text.trim()}\". " +
                "Set a real OpenClaw gateway in settings to talk to your agent."
        }

        private val LONG = List(LONG_PARAGRAPHS) { n ->
            "Paragraph ${n + 1}. The R1's screen is small, so long replies scroll: turn the wheel or " +
                "swipe. Each reply streams in as it is written, and the stop button ends it early."
        }.joinToString("\n\n")

        private const val LONG_PARAGRAPHS = 6
    }
}
