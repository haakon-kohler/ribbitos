package dev.ribbitos.lite

import android.app.Activity
import android.view.WindowInsets
import android.view.WindowInsetsController

/** ribbitOS owns the whole screen: Android's status and navigation bars stay hidden. */
object Screen {
    /** Hides the system bars for [activity]; a swipe from an edge shows them for a moment. */
    fun immersive(activity: Activity) {
        activity.window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
