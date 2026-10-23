package dev.ribbitos.lite

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaultsAreUnconfigured() {
        val s = Settings.load(ctx)
        assertEquals(Settings(), s)
        assertFalse(s.configured)
        assertEquals(Settings.DEFAULT_AGENT, s.agent)
    }

    @Test
    fun saveTrimsAndRoundTrips() {
        Settings(" http://gw:18789/v1 ", " tok ", " openclaw/r1 ").save(ctx)
        assertEquals(Settings("http://gw:18789/v1", "tok", "openclaw/r1"), Settings.load(ctx))
    }

    @Test
    fun blankAgentFallsBackToTheDefault() {
        Settings("http://gw/v1", "", " ").save(ctx)
        assertEquals(Settings.DEFAULT_AGENT, Settings.load(ctx).agent)
    }

    @Test
    fun onlyHttpUrlsCountAsConfigured() {
        assertTrue(Settings("http://192.168.1.20:18789/v1").configured)
        assertTrue(Settings("https://gw.example/v1").configured)
        assertFalse(Settings("gw:18789").configured)
        assertFalse(Settings("ftp://gw/v1").configured)
        assertFalse(Settings("   ").configured)
    }

    @Test
    fun toStringHidesTheToken() {
        assertFalse(Settings("http://gw/v1", "secret").toString().contains("secret"))
        assertTrue(Settings("http://gw/v1", "").toString().contains("token=,"))
    }

    @Test
    fun adbConfigSetsEverything() {
        val intent = Intent(ACTION)
            .putExtra(Settings.BASE_URL, "http://gw/v1")
            .putExtra(Settings.TOKEN, "tok")
            .putExtra(Settings.AGENT, "openclaw/r1")
        ConfigReceiver().onReceive(ctx, intent)
        assertEquals(Settings("http://gw/v1", "tok", "openclaw/r1"), Settings.load(ctx))
    }

    @Test
    fun adbConfigKeepsWhatItOmits() {
        Settings("http://gw/v1", "tok", "openclaw/r1").save(ctx)
        ConfigReceiver().onReceive(ctx, Intent(ACTION).putExtra(Settings.TOKEN, "new"))
        assertEquals(Settings("http://gw/v1", "new", "openclaw/r1"), Settings.load(ctx))
    }

    private companion object {
        const val ACTION = "dev.ribbitos.lite.CONFIG"
    }
}
