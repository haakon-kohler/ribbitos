package dev.ribbitos.lite

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import java.io.IOException

/**
 * Puts ribbitOS's picture (res/drawable/lock_screen.xml: change that file to change the look) on
 * Android's own lock screen. Android keeps doing the locking: a swipe, or the PIN that
 * tools/provision.sh can set.
 */
object LockWallpaper {
    /** Bump this to push a changed picture to R1s that already have the old one. */
    const val VERSION = 3
    private const val KEY = "lock_wallpaper_version"
    private const val TAG = "ribbitos"

    /** Hands the picture to Android's lock screen; replaced by a fake in tests. */
    @Volatile
    var setter: (Context, Bitmap) -> Unit = { ctx, picture ->
        WallpaperManager.getInstance(ctx).setBitmap(picture, null, true, WallpaperManager.FLAG_LOCK)
    }

    /** Sets the picture unless this [VERSION] of it is already set. */
    fun applyOnce(ctx: Context) {
        val prefs = ctx.getSharedPreferences("lock_screen", Context.MODE_PRIVATE)
        if (prefs.getInt(KEY, 0) == VERSION) return
        try {
            apply(ctx)
            prefs.edit().putInt(KEY, VERSION).apply()
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't set the lock screen picture", e)
        }
    }

    /** Draws lock_screen.xml at the screen's size and sets it as the lock screen wallpaper. */
    @Throws(IOException::class)
    fun apply(ctx: Context) {
        val m = ctx.resources.displayMetrics
        val picture = Bitmap.createBitmap(m.widthPixels, m.heightPixels, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(ctx.getDrawable(R.drawable.lock_screen)) { "lock_screen drawable missing" }
        drawable.setBounds(0, 0, m.widthPixels, m.heightPixels)
        drawable.draw(Canvas(picture))
        setter(ctx, picture)
    }
}
