package dev.ribbitos.lite

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun launch() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.field(id: Int) = findViewById<EditText>(id)

    private fun SettingsActivity.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun SettingsActivity.pick(id: Int) = findViewById<RadioGroup>(R.id.provider).check(id)

    private fun SettingsActivity.save() = findViewById<View>(R.id.save).performClick()

    @Test
    fun demoIsPickedAndNeedsNoFields() {
        val a = launch()
        assertEquals(R.id.provider_demo, a.findViewById<RadioGroup>(R.id.provider).checkedRadioButtonId)
        assertEquals(View.GONE, a.findViewById<View>(R.id.fields).visibility)
        assertEquals(ctx.getString(R.string.help_demo), a.text(R.id.help))
    }

    @Test
    fun showsTheActiveProvidersSavedSettings() {
        Settings(Provider.OPENCLAW, "http://gw/v1", "tok", "openclaw/r1").save(ctx)
        val a = launch()
        assertEquals(R.id.provider_openclaw, a.findViewById<RadioGroup>(R.id.provider).checkedRadioButtonId)
        assertEquals("http://gw/v1", a.field(R.id.base_url).text.toString())
        assertEquals("tok", a.field(R.id.token).text.toString())
        assertEquals("openclaw/r1", a.field(R.id.model).text.toString())
        assertEquals(ctx.getString(R.string.label_url_gateway), a.text(R.id.label_url))
        assertEquals(ctx.getString(R.string.label_agent), a.text(R.id.label_model))
    }

    @Test
    fun pickingAProviderShowsItsFieldsAndDefaults() {
        val a = launch()
        a.pick(R.id.provider_claude)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.fields).visibility)
        assertEquals("https://api.anthropic.com", a.field(R.id.base_url).text.toString())
        assertEquals("claude-opus-5-5", a.field(R.id.model).text.toString())
        assertEquals(ctx.getString(R.string.label_key), a.text(R.id.label_token))
        assertEquals(ctx.getString(R.string.help_claude), a.text(R.id.help))
    }

    @Test
    fun editsSurviveSwitchingBackAndForth() {
        val a = launch()
        a.pick(R.id.provider_openai)
        a.field(R.id.token).setText("typed")
        a.pick(R.id.provider_claude)
        assertEquals("", a.field(R.id.token).text.toString())
        a.pick(R.id.provider_openai)
        assertEquals("typed", a.field(R.id.token).text.toString())
    }

    @Test
    fun savingAProviderStoresItAndCloses() {
        val a = launch()
        a.pick(R.id.provider_claude)
        a.field(R.id.token).setText("k")
        a.save()
        assertEquals(Settings(Provider.CLAUDE, token = "k"), Settings.load(ctx))
        assertTrue(a.isFinishing)
    }

    @Test
    fun savingTheDemoNeedsNothing() {
        Settings(Provider.CLAUDE, token = "k").save(ctx)
        val a = launch()
        a.pick(R.id.provider_demo)
        a.save()
        assertEquals(Provider.DEMO, Settings.load(ctx).provider)
        assertTrue(a.isFinishing)
    }

    @Test
    fun aMissingKeyIsRefusedAndExplained() {
        val a = launch()
        a.pick(R.id.provider_openai)
        a.save()
        assertEquals(ctx.getString(R.string.key_missing), a.field(R.id.token).error?.toString())
        assertFalse(a.isFinishing)
        assertEquals(Provider.DEMO, Settings.load(ctx).provider)
    }

    @Test
    fun anInvalidUrlIsRefusedAndExplained() {
        val a = launch()
        a.pick(R.id.provider_openclaw)
        a.field(R.id.base_url).setText("gw:18789")
        a.save()
        assertEquals(ctx.getString(R.string.base_url_invalid), a.field(R.id.base_url).error?.toString())
        assertFalse(a.isFinishing)
    }

    @Test
    fun aBlankApiUrlFallsBackToTheDefault() {
        val a = launch()
        a.pick(R.id.provider_openai)
        a.field(R.id.base_url).setText(" ")
        a.field(R.id.token).setText("k")
        a.save()
        assertEquals("https://api.openai.com/v1", Settings.load(ctx).baseUrl)
    }

    @Test
    fun androidSettingsAreOneTapAway() {
        val a = launch()
        a.findViewById<View>(R.id.android_settings).performClick()
        assertEquals(
            android.provider.Settings.ACTION_SETTINGS,
            org.robolectric.Shadows.shadowOf(a).nextStartedActivity.action,
        )
    }

    @Test
    fun backLeavesWithoutSaving() {
        val a = launch()
        a.pick(R.id.provider_claude)
        a.field(R.id.token).setText("k")
        a.findViewById<View>(R.id.back).performClick()
        assertTrue(a.isFinishing)
        assertEquals(Provider.DEMO, Settings.load(ctx).provider)
        assertNull(Settings.load(ctx, Provider.CLAUDE).token.ifBlank { null })
    }
}
