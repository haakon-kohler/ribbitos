package dev.ribbitos.lite

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** The on-screen keyboard. ribbitOS has no back button, so it puts the keyboard away itself. */
object Keyboard {
    /** Hides the keyboard showing for [field] and takes focus away from it. */
    fun hide(field: EditText) {
        // Android 14 on the R1 ignores the old call alone; the insets controller hides it for real.
        field.windowInsetsController?.hide(WindowInsets.Type.ime())
        field.context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
    }

    /** True when [e] lands on [view]. */
    fun inside(view: View, e: MotionEvent): Boolean {
        val r = Rect()
        return view.getGlobalVisibleRect(r) && r.contains(e.rawX.toInt(), e.rawY.toInt())
    }
}
