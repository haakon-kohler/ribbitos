package dev.ribbitos.lite

/**
 * Hard upper bounds (Power of Ten rules 2 and 3, see docs/POWER_OF_TEN.md). Every loop and every
 * growing collection in ribbitOS stops at one of these.
 */
object Limits {
    /** Longest reply kept from one turn; the stream is cut off past it. */
    const val MAX_REPLY_CHARS = 16_000

    /** Most Server-Sent Events lines read from one reply stream. */
    const val MAX_STREAM_LINES = 50_000

    /** Messages kept on screen and on disk; the oldest go first. */
    const val MAX_MESSAGES = 200

    /** Longest message the user can type. */
    const val MAX_INPUT_CHARS = 4_000

    /** Largest transcript file read back at startup; anything bigger starts a fresh chat. */
    const val MAX_TRANSCRIPT_BYTES = 8_000_000L
}
