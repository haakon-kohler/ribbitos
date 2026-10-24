package dev.ribbitos.lite.voice

import android.content.Context
import dev.ribbitos.lite.Limits
import java.io.File
import java.io.IOException

/**
 * Whisper on the R1's own CPU (whisper.cpp). The model ships inside the APK (fetched and checked
 * against its SHA-256 at build time, see app/build.gradle.kts) and is copied to app storage once,
 * because whisper.cpp loads models from a file path.
 */
class WhisperTranscriber(ctx: Context) : Transcriber {
    private val handle: Long

    init {
        handle = WhisperNative.init(modelFile(ctx).path)
        if (handle == 0L) throw IOException("Couldn't load the speech model")
    }

    override fun transcribe(pcm: FloatArray): String {
        require(pcm.size <= Limits.MAX_RECORD_SECONDS * Pcm.SAMPLE_RATE) { "Recording too long" }
        val bytes = WhisperNative.transcribe(handle, pcm, THREADS) ?: throw IOException("Speech recognition failed")
        return Speech.clean(String(bytes, Charsets.UTF_8))
    }

    override fun close() = WhisperNative.free(handle)

    private companion object {
        const val ASSET = "whisper/model.bin"

        // All eight cores: 4.4 s against 6.5 s on four, measured on the R1 (docs/VOICE.md).
        const val THREADS = 8

        fun modelFile(ctx: Context): File {
            val size = ctx.assets.openFd(ASSET).use { it.length }
            val file = File(ctx.noBackupFilesDir, "whisper-model.bin")
            if (file.length() != size) copyModel(ctx, file)
            return file
        }

        fun copyModel(ctx: Context, file: File) {
            val tmp = File(file.path + ".tmp")
            ctx.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) throw IOException("Couldn't store the speech model")
        }
    }
}
