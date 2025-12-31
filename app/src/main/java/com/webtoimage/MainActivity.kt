package com.webtoimage

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.webtoimage.ui.LogActivity

class MainActivity : AppCompatActivity() {

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(btnLogs)
        }

        setContentView(root) // این خط باعث می‌شود UI نمایش داده شود [web:355]
    }
}
