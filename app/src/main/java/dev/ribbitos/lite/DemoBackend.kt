package dev.ribbitos.lite

import java.io.IOException

/**
 * A backend that needs no server: it streams a canned reply word by word. It is the default
 * provider, so anyone can try ribbitOS before running an agent. Also the smallest complete example
 * of a [Backend].
 *
 * Say something with "long" in it for a reply that needs scrolling, or "error" to see a failure.
 */
class DemoBackend(private val wordDelayMs: Long = WORD_DELAY_MS) : Backend {
    @Volatile
    private var cancelled = false

    override fun send(
        prompt: Prompt,
        history: List<Transcript.Message>,
        conversation: String,
        onDelta: (String) -> Unit,
    ): String {
        val text = prompt.text
        if (text.contains("error", ignoreCase = true)) throw IOException(ERROR)
        val seen = prompt.photo?.let { "I got your photo (${it.size / BYTES_PER_KB} KB). " }.orEmpty()
        val words = (seen + replyTo(text)).split(' ')
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
        const val ERROR = "Demo error: this is how a failed reply looks."
        private const val WORD_DELAY_MS = 60L
        private const val BYTES_PER_KB = 1024

        /** The canned reply for [text]. */
        fun replyTo(text: String): String = if (text.contains("long", ignoreCase = true)) {
            LONG
        } else {
            "This is ribbitOS demo mode, so no agent is listening. You said: \"${text.trim()}\". " +
                "Pick OpenClaw, OpenAI or Claude in settings to talk to a real model."
        }

        private val LONG = List(LONG_PARAGRAPHS) { n ->
            "Paragraph ${n + 1}. The R1's screen is small, so long replies scroll: turn the wheel or " +
                "swipe. Each reply streams in as it is written, and the stop button ends it early."
        }.joinToString("\n\n")

        private const val LONG_PARAGRAPHS = 6
    }
}
