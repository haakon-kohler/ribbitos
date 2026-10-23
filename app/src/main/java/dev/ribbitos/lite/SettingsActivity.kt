package dev.ribbitos.lite

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText

/** Edits the gateway [Settings] on the device. */
class SettingsActivity : Activity() {
    private val url by lazy { findViewById<EditText>(R.id.base_url) }
    private val token by lazy { findViewById<EditText>(R.id.token) }
    private val agent by lazy { findViewById<EditText>(R.id.agent) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_settings)
        Settings.load(this).let {
            url.setText(it.baseUrl)
            token.setText(it.token)
            agent.setText(it.agent)
        }
        findViewById<View>(R.id.save).setOnClickListener { save() }
    }

    private fun save() {
        val s = Settings(url.text.toString(), token.text.toString(), agent.text.toString())
        if (!s.configured) {
            url.error = getString(R.string.base_url_invalid)
            return
        }
        s.save(this)
        finish()
    }
}
