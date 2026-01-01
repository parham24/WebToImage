package com.webtoimage.util

import android.content.Context

object AppPrefs {
    private const val PREFS = "webtoimage_prefs"
    private const val KEY_FORMAT = "output_format"   // "png" or "jpg"
    private const val KEY_JPG_QUALITY = "jpg_quality" // 0..100

    fun getFormat(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FORMAT, "png") ?: "png"

    fun setFormat(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FORMAT, value)
            .apply()
    }

    fun getJpgQuality(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_JPG_QUALITY, 92)

    fun setJpgQuality(ctx: Context, value: Int) {
        val v = value.coerceIn(0, 100)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_JPG_QUALITY, v)
            .apply()
    }
}
