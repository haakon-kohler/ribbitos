package dev.ribbitos.lite.camera

import java.io.File
import java.io.IOException

/**
 * The R1's rotating camera. Angles are degrees, [MIN] to [MAX]: at [MAX] it looks away from the
 * user, at [DOWN] into the R1, and at [MIN] at the user.
 */
interface Motor {
    /** Where the camera points now. */
    val angle: Int

    /** Turns the camera; throws [IOException] if this build may not drive the motor. */
    @Throws(IOException::class)
    fun turnTo(degrees: Int)

    companion object {
        const val MIN = 0
        const val MAX = 180

        /** Lens down, into the R1: where the camera waits whenever it isn't in use. */
        const val DOWN = 90

        /** Where the camera opens: the end of its travel nearest [angle]; from [DOWN], looking away. */
        fun ready(angle: Int): Int = if (facesUser(angle)) MIN else MAX

        /** The other end: one wheel notch turns the camera all the way round. */
        fun flipped(angle: Int): Int = if (facesUser(angle)) MAX else MIN

        /** Past [DOWN] towards the user the camera sees the world upside down. */
        fun facesUser(angle: Int): Boolean = angle < DOWN
    }
}

/**
 * The motor driver's sysfs file (MS35774 stepper). Writing an angle turns the camera there.
 * rabbit's vendor SELinux policy lets only platform and system apps write it, which is why
 * ribbitOS is signed with AOSP's public platform key (keys/README.md).
 */
class SysfsMotor(private val file: File = File(PATH)) : Motor {
    override val angle: Int
        get() = try {
            file.readText().trim().toIntOrNull()?.coerceIn(Motor.MIN, Motor.MAX) ?: DEFAULT
        } catch (expected: IOException) {
            DEFAULT // Unreadable: assume the driver's power-on position.
        }

    override fun turnTo(degrees: Int) {
        require(degrees in Motor.MIN..Motor.MAX) { "Angle $degrees outside ${Motor.MIN}..${Motor.MAX}" }
        file.writeText(degrees.toString())
    }

    private companion object {
        const val PATH = "/sys/devices/platform/step_motor_ms35774/orientation"
        const val DEFAULT = 90
    }
}
