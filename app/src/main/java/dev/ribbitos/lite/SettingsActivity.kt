package dev.ribbitos.lite

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView

/** Picks who answers and edits that provider's [Settings]. Unsaved edits survive switching providers. */
class SettingsActivity : Activity() {
    private val group by lazy { findViewById<RadioGroup>(R.id.provider) }
    private val fields by lazy { findViewById<View>(R.id.fields) }
    private val url by lazy { findViewById<EditText>(R.id.base_url) }
    private val token by lazy { findViewById<EditText>(R.id.token) }
    private val model by lazy { findViewById<EditText>(R.id.model) }
    private val labelUrl by lazy { findViewById<TextView>(R.id.label_url) }
    private val labelToken by lazy { findViewById<TextView>(R.id.label_token) }
    private val labelModel by lazy { findViewById<TextView>(R.id.label_model) }
    private val help by lazy { findViewById<TextView>(R.id.help) }

    /** Edits per provider, kept while switching; at most one per [Provider]. */
    private val drafts = HashMap<Provider, Settings>(Provider.entries.size)
    private var shown = Provider.DEMO

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_settings)
        Screen.immersive(this)
        shown = Settings.load(this).provider
        show(Settings.load(this, shown))
        group.check(BUTTONS.getValue(shown))
        group.setOnCheckedChangeListener { _, id -> switchTo(PROVIDERS[id] ?: shown) }
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.save).setOnClickListener { save() }
    }

    private fun switchTo(p: Provider) {
        if (p == shown) return
        drafts[shown] = current()
        shown = p
        show(drafts[p] ?: Settings.load(this, p))
    }

    private fun current() = Settings(shown, url.text.toString(), token.text.toString(), model.text.toString())

    private fun show(s: Settings) {
        val text = TEXT.getValue(s.provider)
        url.setText(s.baseUrl)
        token.setText(s.token)
        model.setText(s.model)
        url.error = null
        token.error = null
        labelUrl.setText(text.url)
        labelToken.setText(text.token)
        labelModel.setText(text.model)
        help.setText(text.help)
        fields.visibility = if (s.provider == Provider.DEMO) View.GONE else View.VISIBLE
    }

    private fun save() {
        val s = current().let { it.copy(baseUrl = it.baseUrl.ifBlank { it.provider.defaultUrl }) }
        when {
            s.provider.needsUrl && !s.urlValid -> url.error = getString(R.string.base_url_invalid)
            s.provider.needsKey && s.token.isBlank() -> token.error = getString(R.string.key_missing)
            else -> {
                s.save(this)
                finish()
            }
        }
    }

    /** The labels and help text each provider shows. */
    private class Text(val url: Int, val token: Int, val model: Int, val help: Int)

    private companion object {
        val BUTTONS = mapOf(
            Provider.DEMO to R.id.provider_demo,
            Provider.OPENCLAW to R.id.provider_openclaw,
            Provider.OPENAI to R.id.provider_openai,
            Provider.CLAUDE to R.id.provider_claude,
        )
        val PROVIDERS = BUTTONS.entries.associate { (p, id) -> id to p }
        val TEXT = mapOf(
            Provider.DEMO to api(R.string.help_demo),
            Provider.OPENCLAW to Text(
                url = R.string.label_url_gateway,
                token = R.string.label_token,
                model = R.string.label_agent,
                help = R.string.help_openclaw,
            ),
            Provider.OPENAI to api(R.string.help_openai),
            Provider.CLAUDE to api(R.string.help_claude),
        )

        /** The labels the API providers share, with their own [help]. */
        fun api(help: Int) = Text(R.string.label_url_api, R.string.label_key, R.string.label_model, help)
    }
}
