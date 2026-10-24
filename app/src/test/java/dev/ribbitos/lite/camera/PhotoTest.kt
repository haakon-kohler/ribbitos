package dev.ribbitos.lite.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoTest {
    @Test
    fun aBigPhotoShrinksToTheLongSide() = assertEquals(1024 to 768, Photo.fit(1280, 960))

    @Test
    fun portraitShrinksByItsHeight() = assertEquals(768 to 1024, Photo.fit(960, 1280))

    @Test
    fun aSmallPhotoIsNeverEnlarged() = assertEquals(640 to 480, Photo.fit(640, 480))

    @Test
    fun aSliverKeepsAtLeastOnePixel() = assertEquals(Photo.MAX_SIDE to 1, Photo.fit(100_000, 2))

    @Test
    fun anEmptyPhotoIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Photo.fit(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { Photo.fit(10, -1) }
    }

    @Test
    fun lookingAwayTheSensorsMountingIsUpright() = assertEquals(90, Photo.uprightDegrees(90, Motor.UP))

    @Test
    fun facingTheUserItFlips() = assertEquals(270, Photo.uprightDegrees(90, Motor.UP - 1))

    @Test
    fun aFlipWrapsAround() = assertEquals(90, Photo.uprightDegrees(270, Motor.MIN))

    @Test
    fun aDataUriIsBase64Jpeg() = assertEquals("data:image/jpeg;base64,AQID", Photo.dataUri(byteArrayOf(1, 2, 3)))

    @Test
    fun nothingAndTooMuchAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Photo.base64(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { Photo.base64(ByteArray(Photo.MAX_BYTES + 1)) }
    }
}
