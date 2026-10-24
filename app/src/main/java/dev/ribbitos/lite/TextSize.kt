package dev.ribbitos.lite

import android.content.Context

/** How big the conversation's text is. Set in settings, or over adb (ConfigReceiver). */
enum class TextSize(val id: String, val chatSp: Float, val inputSp: Float) {
    SMALL("small", chatSp = 15f, inputSp = 14f),
    MEDIUM("medium", chatSp = 17f, inputSp = 15f),
    LARGE("large", chatSp = 21f, inputSp = 18f),
    ;

    fun save(ctx: Context) {
        Settings.prefs(ctx).edit().putString(KEY, id).apply()
    }

    companion object {
        const val KEY = "text_size"

        /** The size with [id], or null. */
        fun find(id: String?): TextSize? = entries.firstOrNull { it.id == id }

        fun load(ctx: Context): TextSize = find(Settings.prefs(ctx).getString(KEY, null)) ?: MEDIUM
    }
}
