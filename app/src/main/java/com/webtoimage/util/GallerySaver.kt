package com.webtoimage.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GallerySaver {

    /**
     * Saves bitmap into Pictures/WebToImage (visible in Gallery).
     * Returns the DISPLAY_NAME (file name).
     */
    fun saveToGallery(ctx: Context, bitmap: Bitmap, namePrefix: String = "WebToImage"): String {
        val fmt = AppPrefs.getFormat(ctx) // "png" or "jpg"
        val mime = if (fmt == "jpg") "image/jpeg" else "image/png"
        val ext = if (fmt == "jpg") "jpg" else "png"
        val q = if (fmt == "jpg") AppPrefs.getJpgQuality(ctx) else 100

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${namePrefix}_${ts}.$ext"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)

            // Android 10+ (API 29+): RELATIVE_PATH + IS_PENDING
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/WebToImage"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")

        resolver.openOutputStream(uri).use { out ->
            if (out == null) throw IllegalStateException("openOutputStream failed")

            val cf = if (fmt == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            bitmap.compress(cf, q, out)
        }

        if (Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        }

        return displayName
    }
}
