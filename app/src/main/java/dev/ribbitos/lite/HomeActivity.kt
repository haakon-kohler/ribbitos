package dev.ribbitos.lite

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import java.io.File

/** The home screen: one conversation, a text box, and the reply streaming in. */
class HomeActivity : Activity() {
    private val transcript by lazy { Transcript(File(filesDir, TRANSCRIPT_FILE)) }
    private val chat by lazy { ChatView(findViewById(R.id.messages), findViewById(R.id.scroll)) }
    private val hint by lazy { findViewById<TextView>(R.id.hint) }
    private val input by lazy { findViewById<EditText>(R.id.input) }
    private val send by lazy { findViewById<ImageButton>(R.id.send) }

    /** The turn in flight; null when idle. */
    private var turn: Turn? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_home)
        Screen.immersive(this)
        transcript.messages.forEach { chat.add(it) }

        // Wraps like a multi-line field, but the keyboard's action key sends.
        input.setRawInputType(TEXT_INPUT)
        input.filters = arrayOf(InputFilter.LengthFilter(Limits.MAX_INPUT_CHARS))
        input.setOnEditorActionListener { _, id, _ -> (id == EditorInfo.IME_ACTION_SEND).also { if (it) submit() } }
        send.setOnClickListener { if (turn != null) turn?.stop() else submit() }
        findViewById<View>(R.id.new_chat).setOnClickListener { newChat() }
        findViewById<View>(R.id.settings).setOnClickListener { openSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshHint()
    }

    override fun onDestroy() {
        turn?.stop()
        super.onDestroy()
    }

    /** The R1's scroll wheel sends DPAD up/down: it always scrolls the conversation. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val steps = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 1
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) chat.scrollBy(steps)
        return true
    }

    private fun submit() {
        val text = input.text.toString().trim()
        val settings = Settings.load(this)
        when {
            text.isEmpty() || turn != null -> return
            !settings.configured -> openSettings()
            else -> {
                val earlier = transcript.messages.toList()
                start(Turn(Wiring.backendFactory(settings), text, transcript.conversation, earlier), text)
            }
        }
    }

    private fun start(next: Turn, text: String) {
        input.text.clear()
        val sent = Transcript.Message(Transcript.USER, text)
        transcript.add(sent)
        chat.add(sent)
        hint.visibility = View.GONE
        val bubble = chat.add(Transcript.Message(Transcript.ASSISTANT, THINKING))
        turn = next
        setBusy(true)
        next.start(Wiring.executor, ::runOnUiThread, Shown(next, bubble))
    }

    private fun newChat() {
        turn?.stop()
        turn = null
        setBusy(false)
        transcript.reset()
        chat.clear()
        refreshHint()
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun setBusy(busy: Boolean) {
        send.setImageResource(if (busy) R.drawable.ic_stop else R.drawable.ic_send)
        send.contentDescription = getString(if (busy) R.string.stop else R.string.send)
    }

    private fun refreshHint() {
        hint.setText(if (Settings.load(this).configured) R.string.hint_empty else R.string.hint_unconfigured)
        hint.visibility = if (transcript.messages.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Shows one turn's reply in [bubble]; ignores the turn once it's no longer the current one. */
    private inner class Shown(private val owner: Turn, private val bubble: TextView) : Turn.Listener {
        override fun onPartial(text: String) {
            if (turn === owner) chat.stream(bubble, text)
        }

        override fun onFinished(reply: Transcript.Message) {
            if (turn !== owner) return
            chat.show(bubble, reply)
            transcript.add(reply)
            turn = null
            setBusy(false)
        }
    }

    private companion object {
        const val TRANSCRIPT_FILE = "transcript.json"
        const val THINKING = "…"
        const val TEXT_INPUT =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    }
}
