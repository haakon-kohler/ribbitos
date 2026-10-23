package dev.ribbitos.lite

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeActivityTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val savedFactory = Wiring.backendFactory
    private val savedExecutor = Wiring.executor
    private var backend = FakeBackend { _, onDelta ->
        onDelta("Hel")
        onDelta("lo")
        "Hello"
    }
    private lateinit var controller: ActivityController<HomeActivity>

    private val activity: HomeActivity get() = controller.get()
    private val bubbles: List<TextView>
        get() = activity.findViewById<LinearLayout>(R.id.messages).let { col ->
            (0 until col.childCount).map { col.getChildAt(it) as TextView }
        }
    private val texts: List<String> get() = bubbles.map { it.text.toString() }
    private val hint: TextView get() = activity.findViewById(R.id.hint)
    private val sendButton: View get() = activity.findViewById(R.id.send)

    @Before
    fun wire() {
        Wiring.backendFactory = { backend }
        Wiring.executor = Executor { it.run() }
    }

    @After
    fun unwire() {
        Wiring.backendFactory = savedFactory
        Wiring.executor = savedExecutor
    }

    private fun launch(configured: Boolean = true) {
        if (configured) Settings("http://gw/v1", "tok").save(ctx)
        controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
    }

    private fun type(text: String) = activity.findViewById<EditText>(R.id.input).setText(text)

    private fun send(text: String) {
        type(text)
        sendButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun unconfiguredShowsHowToConnect() {
        launch(configured = false)
        assertEquals(ctx.getString(R.string.hint_unconfigured), hint.text.toString())
        assertEquals(View.VISIBLE, hint.visibility)
    }

    @Test
    fun sendingWhileUnconfiguredOpensSettingsInstead() {
        launch(configured = false)
        send("hi")
        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue(texts.isEmpty())
        assertTrue(backend.sent.isEmpty())
    }

    @Test
    fun emptyConfiguredScreenInvitesAMessage() {
        launch()
        assertEquals(ctx.getString(R.string.hint_empty), hint.text.toString())
    }

    @Test
    fun aMessageAndItsStreamedReplyAppear() {
        launch()
        send("hi")
        assertEquals(listOf("hi", "Hello"), texts)
        assertEquals(View.GONE, hint.visibility)
        assertEquals("", activity.findViewById<EditText>(R.id.input).text.toString())
        assertEquals(ctx.getString(R.string.send), sendButton.contentDescription)
    }

    @Test
    fun blankInputSendsNothing() {
        launch()
        send("   ")
        assertTrue(texts.isEmpty())
        assertTrue(backend.sent.isEmpty())
    }

    @Test
    fun keyboardSendActionSends() {
        launch()
        type("hi")
        activity.findViewById<EditText>(R.id.input).onEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals(listOf("hi", "Hello"), texts)
    }

    @Test
    fun conversationSurvivesARestart() {
        launch()
        send("hi")
        val conversation = backend.sent.single().second
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertEquals(listOf("hi", "Hello"), texts)
        send("again")
        assertEquals(conversation, backend.sent.last().second)
    }

    @Test
    fun failureIsShownInTheErrorColor() {
        backend = FakeBackend { _, _ -> throw java.io.IOException("gateway down") }
        launch()
        send("hi")
        assertEquals("gateway down", texts.last())
        assertEquals(ctx.getColor(R.color.error), bubbles.last().currentTextColor)
    }

    @Test
    fun sendBecomesStopWhileReplying() {
        val queue = QueueExecutor()
        Wiring.executor = queue
        launch()
        send("hi")
        assertEquals(ctx.getString(R.string.stop), sendButton.contentDescription)
        queue.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ctx.getString(R.string.send), sendButton.contentDescription)
    }

    @Test
    fun stopCancelsTheReply() {
        val queue = QueueExecutor()
        Wiring.executor = queue
        backend = FakeBackend { _, _ -> failIfCancelled().let { "never" } }
        launch()
        send("hi")
        sendButton.performClick()
        queue.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(backend.cancelled)
        assertEquals(listOf("hi", Turn.STOPPED), texts)
    }

    @Test
    fun newChatClearsTheScreenAndStartsANewConversation() {
        launch()
        send("hi")
        val first = backend.sent.single().second
        activity.findViewById<View>(R.id.new_chat).performClick()
        assertTrue(texts.isEmpty())
        assertEquals(View.VISIBLE, hint.visibility)
        send("hello again")
        assertNotEquals(first, backend.sent.last().second)
    }

    @Test
    fun aReplyToAnAbandonedChatIsDropped() {
        val queue = QueueExecutor()
        Wiring.executor = queue
        launch()
        send("hi")
        activity.findViewById<View>(R.id.new_chat).performClick()
        queue.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(texts.isEmpty())
        assertEquals(ctx.getString(R.string.send), sendButton.contentDescription)
    }

    @Test
    fun oneTurnAtATime() {
        val queue = QueueExecutor()
        Wiring.executor = queue
        launch()
        send("first")
        type("second")
        activity.findViewById<EditText>(R.id.input).onEditorAction(EditorInfo.IME_ACTION_SEND)
        assertEquals(1, queue.pending)
    }

    @Test
    fun scrollWheelScrollsTheConversation() {
        launch()
        assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
    }

    @Test
    fun otherKeysAreLeftToAndroid() {
        launch()
        assertFalse(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12)))
    }

    @Test
    fun settingsButtonOpensSettings() {
        launch()
        activity.findViewById<View>(R.id.settings).performClick()
        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun inputIsCappedAtTheLimit() {
        launch()
        type("x".repeat(Limits.MAX_INPUT_CHARS + 1))
        assertEquals(Limits.MAX_INPUT_CHARS, activity.findViewById<EditText>(R.id.input).text.length)
    }

    @Test
    fun destroyingMidReplyStopsIt() {
        val queue = QueueExecutor()
        Wiring.executor = queue
        launch()
        send("hi")
        controller.pause().stop().destroy()
        assertTrue(backend.cancelled)
    }
}
