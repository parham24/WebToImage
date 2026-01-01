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
            text = formatButtonText()
            setOnClickListener {
                showFormatDialog(this@MainActivity) { text = formatButtonText() }
            }
        }

        val btnQuality = Button(this).apply {
            text = qualityButtonText()
            setOnClickListener {
                showQualityDialog(this@MainActivity) { text = qualityButtonText() }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(btnLogs)
            addView(btnFormat)
            addView(btnQuality)
        }

        setContentView(root)
    }

    private fun formatButtonText(): String {
        return "Output format: " + AppPrefs.getFormat(this).uppercase()
    }

    private fun qualityButtonText(): String {
        val q = AppPrefs.getJpgQuality(this)
        return "JPG quality: $q"
    }

    private fun showFormatDialog(ctx: android.content.Context, onChanged: () -> Unit) {
        val items = arrayOf("PNG (lossless)", "JPG (smaller)")
        val current = if (AppPrefs.getFormat(ctx) == "jpg") 1 else 0

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Output format")
            .setSingleChoiceItems(items, current) { dialog, which ->
                val v = if (which == 1) "jpg" else "png"
                AppPrefs.setFormat(ctx, v)
                onChanged()
                dialog.dismiss()
            }
            .show()
    }

    private fun showQualityDialog(ctx: android.content.Context, onChanged: () -> Unit) {
        val qualities = intArrayOf(100, 95, 92, 90, 85, 80, 75, 70, 60, 50)
        val labels = qualities.map { "$it" }.toTypedArray()
        val currentQ = AppPrefs.getJpgQuality(ctx)
        val currentIndex = qualities.indexOf(currentQ).let { if (it >= 0) it else 2 } // default 92

        android.app.AlertDialog.Builder(ctx)
            .setTitle("JPG quality (0-100)")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                AppPrefs.setJpgQuality(ctx, qualities[which])
                onChanged()
                dialog.dismiss()
            }
            .show()
    }
}
