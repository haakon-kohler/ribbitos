package dev.ribbitos.lite.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MotorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun driver(contents: String?): File = File(tmp.root, "orientation").also { f ->
        contents?.let { f.writeText(it) }
    }

    @Test
    fun aNotchIsOneStep() {
        assertEquals(90 + Motor.STEP, Motor.next(90, 1))
        assertEquals(90 - 2 * Motor.STEP, Motor.next(90, -2))
    }

    @Test
    fun theWheelStopsAtTheEnds() {
        assertEquals(Motor.MAX, Motor.next(Motor.MAX - 1, 1))
        assertEquals(Motor.MIN, Motor.next(Motor.MIN + 1, -1))
    }

    @Test
    fun overTheTopItFacesTheUser() {
        assertTrue(Motor.facesUser(Motor.MIN))
        assertFalse(Motor.facesUser(Motor.UP))
        assertFalse(Motor.facesUser(Motor.MAX))
    }

    @Test
    fun readsTheDriversAngle() = assertEquals(45, SysfsMotor(driver("45\n")).angle)

    @Test
    fun anOutOfRangeReadingIsClamped() = assertEquals(Motor.MAX, SysfsMotor(driver("270")).angle)

    @Test
    fun garbageOrNoDriverMeansTheDefault() {
        assertEquals(90, SysfsMotor(driver("??")).angle)
        assertEquals(90, SysfsMotor(File(tmp.root, "missing")).angle)
    }

    @Test
    fun turningWritesTheAngle() {
        val file = driver("90")
        SysfsMotor(file).turnTo(135)
        assertEquals("135", file.readText())
    }

    @Test
    fun anglesOutsideTheRangeAreRejected() {
        val motor = SysfsMotor(driver("90"))
        assertThrows(IllegalArgumentException::class.java) { motor.turnTo(Motor.MAX + 1) }
        assertThrows(IllegalArgumentException::class.java) { motor.turnTo(Motor.MIN - 1) }
    }
}
