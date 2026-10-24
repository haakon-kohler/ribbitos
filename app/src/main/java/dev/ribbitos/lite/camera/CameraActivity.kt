package dev.ribbitos.lite.camera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.widget.TextView
import dev.ribbitos.lite.R
import dev.ribbitos.lite.Screen
import dev.ribbitos.lite.Wiring
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Full-screen camera: the wheel flips the R1's camera ([Motor]), the shutter takes a photo, and the
 * JPEG is left in the cache as [RESULT_FILE] for the chat to collect. Leaving the screen (locking
 * the R1 included) turns the camera back down. Hardware-facing, so tested on the R1; the rules it
 * follows ([Photo], [Motor]) are unit-tested.
 */
class CameraActivity : Activity() {
    private val preview by lazy { findViewById<TextureView>(R.id.preview) }
    private val hint by lazy { findViewById<TextView>(R.id.camera_hint) }
    private val motor by lazy { Wiring.motorFactory() }
    private val thread = HandlerThread("ribbit-camera")

    /** When the wheel last flipped the camera (uptime ms). */
    private var flippedAt = 0L

    // Everything camera-side runs on this one thread.
    private val handler by lazy { Handler(thread.looper) }
    private val lens by lazy { Lens(getSystemService(CameraManager::class.java), handler, ::deliver, ::unavailable) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_camera)
        Screen.immersive(this)
        thread.start()
        findViewById<View>(R.id.close).setOnClickListener { finish() }
        findViewById<View>(R.id.shutter).setOnClickListener { handler.post { lens.shoot() } }
    }

    override fun onResume() {
        super.onResume()
        when {
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ->
                requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST)
            preview.isAvailable -> preview.surfaceTexture?.let(::open)
            else -> preview.surfaceTextureListener = WhenReady(::open)
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }

    override fun onPause() {
        handler.post {
            lens.close()
            aim { Motor.DOWN }
        }
        super.onPause()
    }

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    /** A wheel notch either way flips the camera; the rest of a spin arrives while it turns. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.eventTime - flippedAt >= TURN_MS) {
            flippedAt = event.eventTime
            handler.post { aim(Motor::flipped) }
        }
        return true
    }

    private fun open(texture: SurfaceTexture) {
        handler.post {
            lens.open(texture)
            aim(Motor::ready)
        }
    }

    /** Turns the camera from where it is to [where] it should go, and keeps the preview upright. */
    private fun aim(where: (Int) -> Int) {
        try {
            val now = motor.angle
            val angle = where(now)
            if (angle != now) motor.turnTo(angle)
            val upright = if (Motor.facesUser(angle)) HALF_TURN else 0f
            runOnUiThread { preview.rotation = upright }
        } catch (e: IOException) {
            runOnUiThread { hint.text = getString(R.string.motor_unavailable, e.message.orEmpty()) }
        }
    }

    /** Shrinks the shot to [Photo]'s size, turns it upright, and leaves it for the chat. */
    private fun deliver(jpeg: ByteArray) {
        val full = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return unavailable()
        val (w, h) = Photo.fit(full.width, full.height)
        val turn = Matrix().apply { postRotate(Photo.uprightDegrees(lens.sensorDegrees, motor.angle).toFloat()) }
        val upright = Bitmap.createBitmap(Bitmap.createScaledBitmap(full, w, h, true), 0, 0, w, h, turn, true)
        val out = ByteArrayOutputStream()
        upright.compress(Bitmap.CompressFormat.JPEG, Photo.QUALITY, out)
        File(cacheDir, RESULT_FILE).writeBytes(out.toByteArray())
        runOnUiThread { finish() }
    }

    private fun unavailable() {
        runOnUiThread { hint.text = getString(R.string.camera_unavailable) }
    }

    /** Runs [ready] once the preview surface exists. */
    private class WhenReady(private val ready: (SurfaceTexture) -> Unit) : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) = ready(t)

        override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) = Unit

        override fun onSurfaceTextureDestroyed(t: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(t: SurfaceTexture) = Unit
    }

    companion object {
        /** The photo, in the app's cache directory, until the chat collects it ([Attachment]). */
        const val RESULT_FILE = "photo.jpg"
        private const val PERMISSION_REQUEST = 2
        private const val HALF_TURN = 180f

        // How long a half turn takes the motor.
        private const val TURN_MS = 1200L
    }
}
