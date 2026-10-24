package dev.ribbitos.lite.voice

import java.io.IOException

/** Records speech for [Transcriber]: 16 kHz mono, at most [dev.ribbitos.lite.Limits.MAX_RECORD_SECONDS]. */
interface Recorder {
    /** Starts recording; throws [IOException] if the microphone can't be opened. */
    @Throws(IOException::class)
    fun start()

    /** Stops and returns what was recorded, as floats in -1..1. */
    fun stop(): FloatArray
}

/** Turns 16 kHz mono speech into text. */
interface Transcriber : AutoCloseable {
    @Throws(IOException::class)
    fun transcribe(pcm: FloatArray): String
}

/** Audio format helpers shared by the recorder and its tests. */
object Pcm {
    const val SAMPLE_RATE = 16_000
    private const val FULL_SCALE = 32_768f

    /** The first [count] 16-bit samples as floats in -1..1, the format Whisper reads. */
    fun toFloats(samples: ShortArray, count: Int): FloatArray {
        require(count in 0..samples.size) { "count $count outside 0..${samples.size}" }
        return FloatArray(count) { samples[it] / FULL_SCALE }
    }
}

/** Cleans Whisper's output for the message box. */
object Speech {
    // Whisper tags non-speech in square brackets: [BLANK_AUDIO], [MUSIC], [inaudible].
    private val TAG = Regex("""\[[^\]]*\]""")
    private val SPACES = Regex("""\s+""")

    /** [raw] without non-speech tags and with single spaces; blank if nothing was said. */
    fun clean(raw: String): String = raw.replace(TAG, " ").replace(SPACES, " ").trim()
}
