package dev.ribbitos.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockWallpaperTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val saved = LockWallpaper.setter
    private val set = mutableListOf<Bitmap>()

    @Before
    fun fake() {
        LockWallpaper.setter = { _, picture -> set += picture }
    }

    @After
    fun real() {
        LockWallpaper.setter = saved
    }

    @Test
    fun thePictureFillsTheScreenOnABlackBackground() {
        LockWallpaper.applyOnce(ctx)
        val picture = set.single()
        val m = ctx.resources.displayMetrics
        assertEquals(m.widthPixels, picture.width)
        assertEquals(m.heightPixels, picture.height)
        assertEquals(Color.BLACK, picture.getPixel(0, 0))
    }

    @Test
    fun itIsSetOncePerVersion() {
        LockWallpaper.applyOnce(ctx)
        LockWallpaper.applyOnce(ctx)
        assertEquals(1, set.size)
    }

    @Test
    fun aFailureIsRetriedNextTime() {
        LockWallpaper.setter = { _, _ -> throw IOException("no wallpaper service") }
        LockWallpaper.applyOnce(ctx)
        LockWallpaper.setter = { _, picture -> set += picture }
        LockWallpaper.applyOnce(ctx)
        assertEquals(1, set.size)
    }

    @Test
    fun homeScreenSetsItOnStart() {
        Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertTrue(set.isNotEmpty())
    }
}
