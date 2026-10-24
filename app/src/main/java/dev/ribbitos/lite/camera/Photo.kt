package dev.ribbitos.lite.camera

import java.util.Base64
import kotlin.math.roundToInt

/** The rules for a photo sent with a message: a JPEG no larger than [MAX_SIDE] on its long side. */
object Photo {
    /** Long side in pixels: plenty for a model to read, about 100-200 KB as JPEG. */
    const val MAX_SIDE = 1024
    const val QUALITY = 80

    /** Largest photo accepted for sending, as a guard on whatever produced it. */
    const val MAX_BYTES = 2_000_000
    private const val HALF_TURN = 180
    private const val FULL_TURN = 360

    /** [width] x [height] scaled down (never up) so the long side is at most [MAX_SIDE]. */
    fun fit(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Empty photo ${width}x$height" }
        val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }

    /**
     * Degrees to turn a sensor image upright: the sensor's mounting, plus a half turn when the
     * camera faces the user ([Motor.facesUser]).
     */
    fun uprightDegrees(sensorDegrees: Int, motorAngle: Int): Int {
        val flip = if (Motor.facesUser(motorAngle)) HALF_TURN else 0
        return (sensorDegrees + flip) % FULL_TURN
    }

    /** [jpeg] as a data: URI, the form OpenAI-style chat APIs take images in. */
    fun dataUri(jpeg: ByteArray): String = "data:image/jpeg;base64," + base64(jpeg)

    fun base64(jpeg: ByteArray): String {
        require(jpeg.size in 1..MAX_BYTES) { "Photo of ${jpeg.size} bytes" }
        return Base64.getEncoder().encodeToString(jpeg)
    }
}
