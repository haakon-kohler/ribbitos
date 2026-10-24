package dev.ribbitos.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Sets the provider and its settings over adb, so nobody has to type a key on a 2.9-inch screen:
 *
 *   adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
 *     --es provider claude --es token <api key> [--es base_url <url>] [--es model <model>]
 *     [--es text_size small|medium|large]
 *
 * `provider` (demo, openclaw, openai, claude) picks which provider the other extras apply to and
 * makes it the active one; without it they apply to the active provider. Omitted extras keep their
 * saved values. The manifest restricts senders to DUMP holders (adb shell, root).
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        intent.getStringExtra(TextSize.KEY)?.let { id ->
            val size = TextSize.find(id)
            if (size == null) Log.w(TAG, "Unknown text size \"$id\"") else size.save(ctx)
        }
        val requested = intent.getStringExtra(Settings.PROVIDER)
        val provider = if (requested == null) Settings.load(ctx).provider else Provider.find(requested)
        if (provider == null) {
            Log.w(TAG, "Unknown provider \"$requested\"; settings unchanged")
            return
        }
        val s = Settings.load(ctx, provider)
        s.copy(
            baseUrl = intent.getStringExtra(Settings.BASE_URL) ?: s.baseUrl,
            token = intent.getStringExtra(Settings.TOKEN) ?: s.token,
            model = intent.getStringExtra(Settings.MODEL) ?: s.model,
        ).save(ctx)
    }

    private companion object {
        const val TAG = "ribbitos"
    }
}
