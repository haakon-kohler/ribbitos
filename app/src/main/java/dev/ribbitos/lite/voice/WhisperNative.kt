package dev.ribbitos.lite.voice

/** The JNI functions in app/src/main/cpp/ribbit_whisper.c (whisper.cpp). Only loads on a device. */
object WhisperNative {
    init {
        System.loadLibrary("ribbit_whisper")
    }

    /** Loads a ggml model file; 0 if it can't be loaded. */
    @JvmStatic
    external fun init(modelPath: String): Long

    /** Transcribes 16 kHz mono PCM; the text as UTF-8, or null on failure. */
    @JvmStatic
    external fun transcribe(handle: Long, pcm: FloatArray, threads: Int): ByteArray?

    @JvmStatic
    external fun free(handle: Long)
}
