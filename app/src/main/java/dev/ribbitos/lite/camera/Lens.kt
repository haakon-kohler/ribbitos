package dev.ribbitos.lite.camera

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import java.util.concurrent.Executor

/**
 * The camera through Camera2: a live preview on a [SurfaceTexture] and one JPEG per [shoot]. Call
 * it only on [handler]'s thread; [onJpeg] and [onFailure] arrive there too. Hardware, so tested on
 * the R1 (docs/RULES.md rule 5).
 */
class Lens(
    private val cameras: CameraManager,
    private val handler: Handler,
    private val onJpeg: (ByteArray) -> Unit,
    private val onFailure: () -> Unit,
) {
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null

    /** How far the sensor is mounted from upright, in degrees. */
    var sensorDegrees = 0
        private set

    /** Opens the R1's camera (its only one) with the preview on [texture]. */
    fun open(texture: SurfaceTexture) {
        val id = cameras.cameraIdList.firstOrNull() ?: return onFailure()
        sensorDegrees = cameras.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        reader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { onJpeg(bytesOf(it)) } }, handler)
        }
        try {
            cameras.openCamera(id, Opened(texture), handler)
        } catch (expected: SecurityException) {
            onFailure() // No CAMERA permission: the same as no camera.
        }
    }

    /** Takes one photo; it arrives through [onJpeg]. */
    fun shoot() {
        val camera = device ?: return
        val still = reader?.surface ?: return
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(still)
            // Pixels as the sensor sees them; Photo.uprightDegrees turns them the right way up.
            set(CaptureRequest.JPEG_ORIENTATION, 0)
        }
        session?.capture(request.build(), null, handler)
    }

    fun close() {
        session?.close()
        device?.close()
        reader?.close()
        session = null
        device = null
        reader = null
    }

    private fun preview(camera: CameraDevice, texture: SurfaceTexture) {
        device = camera
        texture.setDefaultBufferSize(PREVIEW_W, PREVIEW_H)
        val surface = Surface(texture)
        val still = reader?.surface ?: return onFailure()
        val outputs = listOf(OutputConfiguration(surface), OutputConfiguration(still))
        val here = Executor { handler.post(it) }
        camera.createCaptureSession(
            SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, here, Configured(camera, surface)),
        )
    }

    private inner class Opened(private val texture: SurfaceTexture) : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) = preview(camera, texture)

        override fun onDisconnected(camera: CameraDevice) = camera.close()

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            onFailure()
        }
    }

    private inner class Configured(private val camera: CameraDevice, private val surface: Surface) :
        CameraCaptureSession.StateCallback() {
        override fun onConfigured(s: CameraCaptureSession) {
            session = s
            val repeating = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
            s.setRepeatingRequest(repeating.build(), null, handler)
        }

        override fun onConfigureFailed(s: CameraCaptureSession) = onFailure()
    }

    private companion object {
        const val CAPTURE_W = 1280
        const val CAPTURE_H = 960
        const val PREVIEW_W = 640
        const val PREVIEW_H = 480

        fun bytesOf(image: Image): ByteArray {
            val buffer = image.planes[0].buffer
            return ByteArray(buffer.remaining()).also { buffer.get(it) }
        }
    }
}
