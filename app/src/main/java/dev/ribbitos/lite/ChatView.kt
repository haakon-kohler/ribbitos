package dev.ribbitos.lite

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Draws the conversation: one [TextView] per message inside a scrolling column. */
class ChatView(private val column: LinearLayout, private val scroll: ScrollView) {
    private val density = column.resources.displayMetrics.density

    /** Appends a bubble for [m] and returns it, so a streaming reply can keep updating it. */
    fun add(m: Transcript.Message): TextView {
        val user = m.role == Transcript.USER
        val view = TextView(column.context).apply {
            textSize = TEXT_SP
            setPadding(dp(PAD_H), dp(PAD_V), dp(PAD_H), dp(PAD_V))
            setTextIsSelectable(true)
            if (user) maxWidth = (column.resources.displayMetrics.widthPixels * USER_WIDTH).toInt()
        }
        show(view, m)
        val width = if (user) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT
        column.addView(
            view,
            LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (user) Gravity.END else Gravity.START
                topMargin = dp(GAP)
            },
        )
        scrollToEnd()
        return view
    }

    /** Renders [m] into an existing bubble. */
    fun show(view: TextView, m: Transcript.Message) {
        view.text = m.text
        view.setTextColor(column.context.getColor(if (m.error) R.color.error else R.color.fg))
        if (m.role == Transcript.USER) view.setBackgroundResource(R.drawable.bubble_user)
    }

    /** Updates a streaming reply's text. */
    fun stream(view: TextView, text: String) {
        view.text = text
        scrollToEnd()
    }

    /** Removes every bubble. */
    fun clear() = column.removeAllViews()

    /** Scrolls by [steps] wheel notches; negative is up. */
    fun scrollBy(steps: Int) = scroll.smoothScrollBy(0, steps * dp(WHEEL_STEP))

    private fun scrollToEnd() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private companion object {
        const val TEXT_SP = 15f
        const val PAD_H = 10
        const val PAD_V = 6
        const val GAP = 6
        const val WHEEL_STEP = 48
        const val USER_WIDTH = 0.85
    }
}
