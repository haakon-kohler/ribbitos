package dev.ribbitos.lite

/**
 * Shapes the transcript for stateless chat APIs (OpenAI, Claude), which need the conversation sent
 * with every message. Error replies and blank lines are left out, consecutive messages from the same
 * side are merged so roles alternate, the list starts with the user, and it stays within
 * [Limits.MAX_HISTORY_MESSAGES] and [Limits.MAX_HISTORY_CHARS] (oldest dropped first).
 */
object History {
    /** [earlier] messages plus the new [text] from the user, ready to send. */
    fun forTurn(earlier: List<Transcript.Message>, text: String): List<Transcript.Message> {
        require(text.isNotBlank()) { "A turn needs a message" }
        val out = ArrayList<Transcript.Message>(Limits.MAX_HISTORY_MESSAGES + 1)
        val kept = earlier.takeLast(Limits.MAX_HISTORY_MESSAGES).filter { !it.error && it.text.isNotBlank() }
        kept.forEach { add(out, it) }
        add(out, Transcript.Message(Transcript.USER, text))
        return trim(out)
    }

    private fun add(out: MutableList<Transcript.Message>, m: Transcript.Message) {
        val last = out.lastOrNull()
        if (last != null && last.role == m.role) {
            out[out.lastIndex] = last.copy(text = last.text + "\n\n" + m.text)
        } else {
            out += m
        }
    }

    /** Drops the oldest messages until the rest fit, keeping the new message and a user-first order. */
    private fun trim(messages: List<Transcript.Message>): List<Transcript.Message> {
        var total = messages.sumOf { it.text.length }
        var from = 0
        while (from < messages.lastIndex && total > Limits.MAX_HISTORY_CHARS) {
            total -= messages[from].text.length
            from++
        }
        while (from < messages.lastIndex && messages[from].role != Transcript.USER) from++
        return messages.subList(from, messages.size)
    }
}
