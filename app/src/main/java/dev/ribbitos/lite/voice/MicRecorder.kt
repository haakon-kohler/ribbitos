package dev.ribbitos.lite.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.ribbitos.lite.Limits
import java.io.IOException

/** The R1's microphone through [AudioRecord], 16 kHz mono, stopping by itself at the length limit. */
class MicRecorder(private val ctx: Context) : Recorder {
    private val samples = ShortArray(Limits.MAX_RECORD_SECONDS * Pcm.SAMPLE_RATE)

    @Volatile
    private var count = 0

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var reader: Thread? = null

    override fun start() {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw IOException("No microphone permission")
        }
        val minBuffer = AudioRecord.getMinBufferSize(Pcm.SAMPLE_RATE, CHANNEL, ENCODING)
        val source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        val r = AudioRecord(source, Pcm.SAMPLE_RATE, CHANNEL, ENCODING, minBuffer * 2)
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IOException("Couldn't open the microphone")
        }
        count = 0
        running = true
        record = r
        r.startRecording()
        reader = Thread({ read(r) }, "ribbit-mic").apply { start() }
    }

    /** Reads until stopped or the buffer is full; bounded by the buffer size. */
    private fun read(r: AudioRecord) {
        while (running && count < samples.size) {
            val n = r.read(samples, count, minOf(CHUNK, samples.size - count))
            if (n <= 0) break
            count += n
        }
    }

    override fun stop(): FloatArray {
        running = false
        reader?.join(JOIN_TIMEOUT_MS)
        record?.run {
            stop()
            release()
        }
        record = null
        reader = null
        return Pcm.toFloats(samples, count)
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK = 1_600 // 0.1 s
        const val JOIN_TIMEOUT_MS = 1_000L
    }
}
