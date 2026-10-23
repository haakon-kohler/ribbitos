package dev.ribbitos.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sets the gateway over adb, so nobody has to type a token on a 2.9-inch screen:
 *
 *   adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
 *     --es base_url http://192.168.1.20:18789/v1 --es token <token> --es agent openclaw/default
 *
 * Omitted extras keep their current value. The manifest restricts senders to DUMP holders (adb, root).
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = Settings.load(ctx)
        s.copy(
            baseUrl = intent.getStringExtra(Settings.BASE_URL) ?: s.baseUrl,
            token = intent.getStringExtra(Settings.TOKEN) ?: s.token,
            agent = intent.getStringExtra(Settings.AGENT) ?: s.agent,
        ).save(ctx)
    }
}
