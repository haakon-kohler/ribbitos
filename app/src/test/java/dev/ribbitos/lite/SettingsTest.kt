package dev.ribbitos.lite

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun defaultIsTheDemoAndItNeedsNothing() {
        val s = Settings.load(ctx)
        assertEquals(Settings(Provider.DEMO), s)
        assertTrue(s.configured)
    }

    @Test
    fun eachProviderStartsFromItsDefaults() {
        assertEquals(
            Settings(Provider.OPENAI, "https://api.openai.com/v1", "", "gpt-6.1-sol"),
            Settings.load(ctx, Provider.OPENAI)
        )
        assertEquals(
            Settings(Provider.CLAUDE, "https://api.anthropic.com", "", "claude-opus-5-5"),
            Settings.load(ctx, Provider.CLAUDE),
        )
        assertEquals(Settings(Provider.OPENCLAW, "", "", "openclaw/default"), Settings.load(ctx, Provider.OPENCLAW))
    }

    @Test
    fun saveTrimsAndMakesTheProviderActive() {
        Settings(Provider.OPENCLAW, " http://gw:18789/v1 ", " tok ", " openclaw/r1 ").save(ctx)
        assertEquals(Settings(Provider.OPENCLAW, "http://gw:18789/v1", "tok", "openclaw/r1"), Settings.load(ctx))
    }

    @Test
    fun providersKeepTheirOwnValues() {
        Settings(Provider.CLAUDE, token = "claude-key").save(ctx)
        Settings(Provider.OPENAI, token = "openai-key").save(ctx)
        assertEquals(Provider.OPENAI, Settings.load(ctx).provider)
        assertEquals("claude-key", Settings.load(ctx, Provider.CLAUDE).token)
        assertEquals("openai-key", Settings.load(ctx, Provider.OPENAI).token)
    }

    @Test
    fun blankFieldsFallBackToDefaults() {
        Settings(Provider.CLAUDE, " ", "k", " ").save(ctx)
        assertEquals(Settings(Provider.CLAUDE, token = "k"), Settings.load(ctx))
    }

    @Test
    fun whatEachProviderNeeds() {
        assertTrue(Settings(Provider.OPENCLAW, "http://192.168.1.20:18789/v1").configured)
        assertFalse(Settings(Provider.OPENCLAW, "gw:18789").configured)
        assertFalse(Settings(Provider.OPENCLAW, "ftp://gw/v1").configured)
        assertFalse(Settings(Provider.OPENAI).configured)
        assertTrue(Settings(Provider.OPENAI, token = "k").configured)
        assertFalse(Settings(Provider.CLAUDE, token = " ").configured)
        assertFalse(Settings(Provider.CLAUDE, "nope", "k").configured)
    }

    @Test
    fun unknownProviderIdsAreTheDemoOrNothing() {
        assertEquals(Provider.DEMO, Provider.of("nope"))
        assertNull(Provider.find("nope"))
        assertEquals(Provider.CLAUDE, Provider.of("claude"))
    }

    @Test
    fun toStringHidesTheToken() {
        assertFalse(Settings(Provider.CLAUDE, token = "secret").toString().contains("secret"))
        assertTrue(Settings(Provider.CLAUDE).toString().contains("token=,"))
    }

    @Test
    fun adbConfigSetsAProviderAndMakesItActive() {
        val intent = Intent(ACTION)
            .putExtra(Settings.PROVIDER, "claude")
            .putExtra(Settings.TOKEN, "k")
            .putExtra(Settings.MODEL, "claude-sonnet-5-5")
        ConfigReceiver().onReceive(ctx, intent)
        assertEquals(Settings(Provider.CLAUDE, token = "k", model = "claude-sonnet-5-5"), Settings.load(ctx))
    }

    @Test
    fun adbConfigWithoutAProviderEditsTheActiveOne() {
        Settings(Provider.OPENCLAW, "http://gw/v1", "tok").save(ctx)
        ConfigReceiver().onReceive(ctx, Intent(ACTION).putExtra(Settings.TOKEN, "new"))
        assertEquals(Settings(Provider.OPENCLAW, "http://gw/v1", "new"), Settings.load(ctx))
    }

    @Test
    fun adbConfigWithAnUnknownProviderChangesNothing() {
        Settings(Provider.OPENCLAW, "http://gw/v1", "tok").save(ctx)
        ConfigReceiver().onReceive(
            ctx,
            Intent(ACTION).putExtra(Settings.PROVIDER, "nope").putExtra(Settings.TOKEN, "x")
        )
        assertEquals(Settings(Provider.OPENCLAW, "http://gw/v1", "tok"), Settings.load(ctx))
    }

    @Test
    fun adbConfigSetsTheTextSize() {
        ConfigReceiver().onReceive(ctx, Intent(ACTION).putExtra(TextSize.KEY, "small"))
        assertEquals(TextSize.SMALL, TextSize.load(ctx))
    }

    @Test
    fun anUnknownTextSizeChangesNothing() {
        TextSize.LARGE.save(ctx)
        ConfigReceiver().onReceive(ctx, Intent(ACTION).putExtra(TextSize.KEY, "huge"))
        assertEquals(TextSize.LARGE, TextSize.load(ctx))
    }

    private companion object {
        const val ACTION = "dev.ribbitos.lite.CONFIG"
    }
}
