package com.webtoimage.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val TAG = "WebToImage"
    private const val FILE_NAME = "webtoimage.log"
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logFile(ctx: Context): File {
        val dir = File(ctx.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun i(ctx: Context, msg: String) {
        Log.i(TAG, msg)
        append(ctx, "I", msg, null)
    }

    fun e(ctx: Context, msg: String, tr: Throwable? = null) {
        Log.e(TAG, msg, tr)
        append(ctx, "E", msg, tr)
    }

    private fun append(ctx: Context, level: String, msg: String, tr: Throwable?) {
        runCatching {
            val file = logFile(ctx)

            val line = buildString {
                append(df.format(Date()))
                append(" ")
                append(level)
                append(" ")
                append(msg)
                appendLine()
                if (tr != null) {
                    append(Log.getStackTraceString(tr))
                    appendLine()
                }
            }

            file.appendText(line)
        }
    }

    fun readAll(ctx: Context): String {
        val f = logFile(ctx)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(ctx: Context) {
        val f = logFile(ctx)
        if (f.exists()) f.writeText("")
    }
}
