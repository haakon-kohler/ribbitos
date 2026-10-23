package dev.ribbitos.lite

import android.content.Context
import android.view.View
import android.widget.EditText
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

    @Test
    fun showsTheSavedSettings() {
        Settings("http://gw/v1", "tok", "openclaw/r1").save(ctx)
        val a = launch()
        assertEquals("http://gw/v1", a.field(R.id.base_url).text.toString())
        assertEquals("tok", a.field(R.id.token).text.toString())
        assertEquals("openclaw/r1", a.field(R.id.agent).text.toString())
    }

    @Test
    fun savingAValidUrlStoresAndCloses() {
        val a = launch()
        a.field(R.id.base_url).setText("http://gw:18789/v1")
        a.field(R.id.token).setText("tok")
        a.findViewById<View>(R.id.save).performClick()
        assertEquals(Settings("http://gw:18789/v1", "tok", Settings.DEFAULT_AGENT), Settings.load(ctx))
        assertTrue(a.isFinishing)
    }

    @Test
    fun anInvalidUrlIsRefusedAndExplained() {
        val a = launch()
        a.field(R.id.base_url).setText("gw:18789")
        a.findViewById<View>(R.id.save).performClick()
        assertEquals(ctx.getString(R.string.base_url_invalid), a.field(R.id.base_url).error?.toString())
        assertFalse(a.isFinishing)
        assertFalse(Settings.load(ctx).configured)
        assertNull(Settings.load(ctx).baseUrl.ifBlank { null })
    }
}
