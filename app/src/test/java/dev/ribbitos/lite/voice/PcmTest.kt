package dev.ribbitos.lite.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmTest {
    @Test
    fun samplesBecomeFloatsInMinusOneToOne() {
        val pcm = Pcm.toFloats(shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, 16_384, 99), 4)
        assertArrayEquals(floatArrayOf(0f, 32_767f / 32_768f, -1f, 0.5f), pcm, 0f)
    }

    @Test
    fun countOutsideTheArrayIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Pcm.toFloats(ShortArray(2), 3) }
        assertThrows(IllegalArgumentException::class.java) { Pcm.toFloats(ShortArray(2), -1) }
    }
}

class SpeechTest {
    @org.junit.Test
    fun nonSpeechTagsAndExtraSpacesGo() {
        org.junit.Assert.assertEquals("hello there", Speech.clean("  [MUSIC] hello   there [BLANK_AUDIO] "))
    }

    @org.junit.Test
    fun onlyTagsMeansNothingWasSaid() {
        org.junit.Assert.assertEquals("", Speech.clean("[BLANK_AUDIO]"))
    }
}
