package dev.ribbitos.lite

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.ribbitos.lite.camera.CameraActivity
import dev.ribbitos.lite.camera.Photo
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeActivityTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val savedFactory = Wiring.backendFactory
    private val savedExecutor = Wiring.executor
    private val savedWallpaper = LockWallpaper.setter
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
        // Robolectric 4.17's WallpaperManager can't load on SDK 34; LockWallpaperTest covers it.
        LockWallpaper.setter = { _, _ -> }
        Wiring.backendFactory = { backend }
        Wiring.executor = Executor { it.run() }
    }

    @After
    fun unwire() {
        LockWallpaper.setter = savedWallpaper
        Wiring.backendFactory = savedFactory
        Wiring.executor = savedExecutor
    }

    private fun launch(configured: Boolean = true) {
        val settings = if (configured) Settings(Provider.OPENCLAW, "http://gw/v1", "tok") else Settings(Provider.CLAUDE)
        settings.save(ctx)
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
    fun eachTurnGetsTheConversationSoFar() {
        launch()
        send("hi")
        send("again")
        assertEquals(emptyList<Transcript.Message>(), backend.histories.first())
        assertEquals(
            listOf(Transcript.Message(Transcript.USER, "hi"), Transcript.Message(Transcript.ASSISTANT, "Hello")),
            backend.histories.last(),
        )
    }

    @Test
    fun theDefaultIsTheDemo() {
        Wiring.backendFactory = Wiring::defaultBackend
        Wiring.executor = Executor { it.run() }
        controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertEquals(ctx.getString(R.string.hint_empty), hint.text.toString())
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
    fun tappingTheConversationPutsTheKeyboardAway() {
        launch()
        val imm = shadowOf(activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
        val field = activity.findViewById<EditText>(R.id.input)
        field.requestFocus()
        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .showSoftInput(activity.findViewById<EditText>(R.id.input), 0)
        val now = android.os.SystemClock.uptimeMillis()
        activity.dispatchTouchEvent(
            android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        )
        assertFalse(imm.isSoftInputVisible)
        assertFalse(field.hasFocus())
    }

    @Test
    fun tappingTheMessageRowKeepsTheKeyboard() {
        launch()
        val imm = shadowOf(activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .showSoftInput(activity.findViewById<EditText>(R.id.input), 0)
        val r = android.graphics.Rect()
        activity.findViewById<View>(R.id.composer).getGlobalVisibleRect(r)
        val now = android.os.SystemClock.uptimeMillis()
        val e = android.view.MotionEvent.obtain(
            now,
            now,
            android.view.MotionEvent.ACTION_DOWN,
            r.exactCenterX(),
            r.exactCenterY(),
            0
        )
        activity.dispatchTouchEvent(e)
        assertTrue(imm.isSoftInputVisible)
    }

    @Test
    fun theKeyboardShowsNoMicKeyOfItsOwn() {
        launch()
        // ribbitOS's own mic does voice typing; the AOSP keyboard hides its key for these options.
        val options = activity.findViewById<EditText>(R.id.input).privateImeOptions.orEmpty()
        assertTrue(options.contains("com.android.inputmethod.latin.noMicrophoneKey"))
        assertTrue(options.split(',').contains("nm"))
    }

    @Test
    fun theWheelPutsTheKeyboardAway() {
        launch()
        val imm = shadowOf(activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .showSoftInput(activity.findViewById<EditText>(R.id.input), 0)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(imm.isSoftInputVisible)
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
    fun cameraButtonOpensTheCamera() {
        launch()
        activity.findViewById<View>(R.id.camera).performClick()
        assertEquals(CameraActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    private val inbox: File get() = File(ctx.cacheDir, CameraActivity.RESULT_FILE)
    private val thumb: View get() = activity.findViewById(R.id.photo)

    /** What the camera leaves behind: a real JPEG. */
    private fun takePhoto(): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, Photo.QUALITY, out)
        return out.toByteArray().also { inbox.writeBytes(it) }
    }

    @Test
    fun aPhotoFromTheCameraGoesWithTheNextMessage() {
        val jpeg = takePhoto()
        launch()
        assertEquals(View.VISIBLE, thumb.visibility)
        assertFalse("collected once", inbox.exists())
        send("what is it")
        assertArrayEquals(jpeg, backend.photos.single())
        assertEquals("\uD83D\uDCF7 what is it", texts.first())
        assertEquals(View.GONE, thumb.visibility)
        send("and now")
        assertNull("the photo went with one message only", backend.photos.last())
    }

    @Test
    fun aPhotoAloneAsksWhatItShows() {
        takePhoto()
        launch()
        send("")
        assertEquals(ctx.getString(R.string.photo_prompt), backend.sent.single().first)
    }

    @Test
    fun tappingThePhotoDropsIt() {
        takePhoto()
        launch()
        thumb.performClick()
        assertEquals(View.GONE, thumb.visibility)
        send("hi")
        assertNull(backend.photos.single())
    }

    @Test
    fun anEmptyPhotoIsIgnored() {
        inbox.writeBytes(ByteArray(0))
        launch()
        assertEquals(View.GONE, thumb.visibility)
        assertFalse(inbox.exists())
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
