package dev.ribbitos.lite.camera

import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import java.io.File

/**
 * The photo waiting to go with the next message, shown as a thumbnail beside the text box. The
 * camera leaves its JPEG at [inbox]; [collect] takes it from there. Tapping the thumbnail drops it.
 */
class Attachment(private val thumb: ImageView, private val inbox: File) {
    /** The JPEG to send, or null. */
    var photo: ByteArray? = null
        private set

    init {
        thumb.setOnClickListener { clear() }
    }

    /** Takes a photo the camera left in [inbox], if there is one. */
    fun collect() {
        if (!inbox.isFile) return
        val bytes = inbox.readBytes()
        inbox.delete()
        if (bytes.size !in 1..Photo.MAX_BYTES) return
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        photo = bytes
        thumb.setImageBitmap(image)
        thumb.visibility = View.VISIBLE
    }

    fun clear() {
        photo = null
        thumb.setImageDrawable(null)
        thumb.visibility = View.GONE
    }
}
