package dev.ribbitos.lite.voice

import android.Manifest
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageButton
import dev.ribbitos.lite.HomeActivity
import dev.ribbitos.lite.R
import dev.ribbitos.lite.Wiring
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.IOException
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceInputTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val saved = Triple(Wiring.executor, Wiring.recorderFactory, Wiring.transcriberFactory)
    private var recorded = FloatArray(Pcm.SAMPLE_RATE)
    private var startFails = false
    private var heard: () -> String = { "hello there" }
    private var started = 0
    private var transcribed = 0
    private var closed = 0
    private lateinit var controller: ActivityController<HomeActivity>

    private val input: EditText get() = controller.get().findViewById(R.id.input)
    private val mic: ImageButton get() = controller.get().findViewById(R.id.mic)

    @Before
    fun wire() {
        Wiring.executor = Executor { it.run() }
        Wiring.recorderFactory = {
            object : Recorder {
                override fun start() {
                    if (startFails) throw IOException("Couldn't open the microphone")
                    started++
                }

                override fun stop() = recorded
            }
        }
        Wiring.transcriberFactory = {
            object : Transcriber {
                override fun transcribe(pcm: FloatArray): String {
                    transcribed++
                    return heard()
                }

                override fun close() {
                    closed++
                }
            }
        }
    }

    @After
    fun unwire() {
        Wiring.executor = saved.first
        Wiring.recorderFactory = saved.second
        Wiring.transcriberFactory = saved.third
    }

    private fun launch(granted: Boolean = true) {
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
    }

    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        mic.dispatchTouchEvent(MotionEvent.obtain(now, now, action, 0f, 0f, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun hold() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
    }

    @Test
    fun holdingRecordsAndTheTextLandsInTheBox() {
        launch()
        touch(MotionEvent.ACTION_DOWN)
        assertTrue("the mic lights up while listening", mic.isActivated)
        assertEquals(app.getString(R.string.listening), input.hint.toString())
        touch(MotionEvent.ACTION_UP)
        assertEquals("hello there", input.text.toString())
        assertFalse(mic.isActivated)
        assertEquals(app.getString(R.string.input_hint), input.hint.toString())
    }

    @Test
    fun speechIsAddedAfterWhatWasTyped() {
        launch()
        input.setText("so ")
        hold()
        assertEquals("so hello there", input.text.toString())
    }

    @Test
    fun theModelLoadsOnceAndClosesWithTheScreen() {
        launch()
        hold()
        hold()
        assertEquals(2, transcribed)
        controller.pause().stop().destroy()
        assertEquals(1, closed)
    }

    @Test
    fun aTapIsNotSpeech() {
        recorded = FloatArray(Pcm.SAMPLE_RATE / 10)
        launch()
        hold()
        assertEquals(0, transcribed)
        assertEquals("", input.text.toString())
    }

    @Test
    fun withoutPermissionItAsksFirst() {
        launch(granted = false)
        hold()
        assertEquals(0, started)
        assertEquals(
            Manifest.permission.RECORD_AUDIO,
            shadowOf(controller.get()).lastRequestedPermission.requestedPermissions.single(),
        )
    }

    @Test
    fun aMicrophoneThatWontOpenIsExplained() {
        startFails = true
        launch()
        hold()
        assertEquals("Couldn't open the microphone", input.hint.toString())
    }

    @Test
    fun aFailedTranscriptionIsExplained() {
        heard = { throw IOException("Speech recognition failed") }
        launch()
        hold()
        assertEquals("Speech recognition failed", input.hint.toString())
        assertEquals("", input.text.toString())
    }

    @Test
    fun silenceSaysSo() {
        heard = { "  " }
        launch()
        hold()
        assertEquals(app.getString(R.string.not_heard), input.hint.toString())
    }

    @Test
    fun otherTouchesAreIgnoredAndCancelStops() {
        launch()
        touch(MotionEvent.ACTION_MOVE)
        assertEquals(0, started)
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_CANCEL)
        assertEquals(1, transcribed)
    }
}
