package com.webtoimage.util

import android.content.Context

object AppPrefs {
    private const val PREFS = "webtoimage_prefs"
    private const val KEY_FORMAT = "output_format" // "png" or "jpg"

    fun getFormat(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FORMAT, "png") ?: "png"

    fun setFormat(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FORMAT, value)
            .apply()
    }
}
