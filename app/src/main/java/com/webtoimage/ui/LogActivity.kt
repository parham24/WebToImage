package com.webtoimage.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.webtoimage.util.AppLog

class LogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            textSize = 12f
            setPadding(24, 24, 24, 24)
            text = AppLog.readAll(this@LogActivity).ifBlank { "No logs yet." }
        }

        val btnShare = Button(this).apply {
            text = "Share logs"
            setOnClickListener {
                val logs = AppLog.readAll(this@LogActivity)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, logs)
                }
                startActivity(Intent.createChooser(send, "Share logs"))
            }
        }

        val btnClear = Button(this).apply {
            text = "Clear logs"
            setOnClickListener {
                AppLog.clear(this@LogActivity)
                tv.text = "Cleared."
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(btnShare)
            addView(btnClear)
            addView(ScrollView(this@LogActivity).apply { addView(tv) })
        }

        setContentView(root)
    }
}
