package com.webtoimage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.webtoimage.ui.LogActivity
import com.webtoimage.util.AppPrefs

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "WebToImage"
            textSize = 20f
            setPadding(24, 24, 24, 24)
        }

        val btnLogs = Button(this).apply {
            text = "View logs"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LogActivity::class.java))
            }
        }

        val btnFormat = Button(this).apply {
            text = "Output format: " + AppPrefs.getFormat(this@MainActivity).uppercase()
            setOnClickListener {
                showFormatDialog(this@MainActivity, this)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(btnLogs)
            addView(btnFormat)
        }

        setContentView(root)
    }

    private fun showFormatDialog(ctx: android.content.Context, btn: Button) {
        val items = arrayOf("PNG (lossless)", "JPG (smaller)")
        val current = if (AppPrefs.getFormat(ctx) == "jpg") 1 else 0

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Output format")
            .setSingleChoiceItems(items, current) { dialog, which ->
                val v = if (which == 1) "jpg" else "png"
                AppPrefs.setFormat(ctx, v)
                btn.text = "Output format: " + v.uppercase()
                dialog.dismiss()
            }
            .show()
    }
}
