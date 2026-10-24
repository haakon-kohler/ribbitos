package dev.ribbitos.lite.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.ribbitos.lite.R
import dev.ribbitos.lite.Wiring
import java.io.IOException

/**
 * The mic button: hold to talk, let go to transcribe on the R1; the text lands in [input] to edit
 * and send. Recording and transcribing go through [Wiring], so tests swap in fakes.
 */
class VoiceInput(
    private val activity: Activity,
    private val input: EditText,
    private val mic: View,
    private val post: (Runnable) -> Unit,
) {
    /** Non-null while recording. */
    private var recorder: Recorder? = null

    /** Loaded on first use (the model takes a moment), then kept; used on the worker thread. */
    @Volatile
    private var transcriber: Transcriber? = null
    private var busy = false

    fun attach() = mic.setOnTouchListener { v, e ->
        if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
        onTouch(e)
    }

    /** Stops recording and frees the model. */
    fun close() {
        recorder?.stop()
        recorder = null
        transcriber?.close()
    }

    private fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
            else -> return false
        }
        return true
    }

    private fun press() {
        if (busy || recorder != null) return
        if (activity.checkSelfPermission(MIC) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(MIC), PERMISSION_REQUEST)
            return
        }
        val r = Wiring.recorderFactory(activity)
        try {
            r.start()
        } catch (e: IOException) {
            show(e.message)
            return
        }
        recorder = r
        state(R.string.listening, listening = true)
    }

    private fun release() {
        val pcm = recorder?.stop() ?: return
        recorder = null
        if (pcm.size < MIN_SAMPLES) {
            state(R.string.input_hint, listening = false)
            return
        }
        busy = true
        state(R.string.transcribing, listening = false)
        Wiring.executor.execute {
            val heard = transcribe(pcm)
            post(Runnable { finish(heard) })
        }
    }

    private fun transcribe(pcm: FloatArray): Result<String> = try {
        val t = transcriber ?: Wiring.transcriberFactory(activity).also { transcriber = it }
        Result.success(t.transcribe(pcm))
    } catch (e: IOException) {
        Result.failure(e)
    }

    private fun finish(heard: Result<String>) {
        busy = false
        state(R.string.input_hint, listening = false)
        val text = heard.getOrElse {
            show(it.message)
            return
        }
        if (text.isBlank()) {
            input.setHint(R.string.not_heard)
            return
        }
        val joined = if (input.text.isBlank()) text else "${input.text.trimEnd()} $text"
        input.setText(joined)
        input.setSelection(input.text.length)
    }

    private fun state(hint: Int, listening: Boolean) {
        input.setHint(hint)
        mic.isActivated = listening
    }

    private fun show(message: String?) {
        input.hint = message ?: activity.getString(R.string.not_heard)
    }

    private companion object {
        const val MIC = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_REQUEST = 1

        // Under a quarter second is a tap, not speech.
        const val MIN_SAMPLES = Pcm.SAMPLE_RATE / 4
    }
}
