package com.webtoimage.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.webtoimage.util.GallerySaver

class ShareToImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // باید قبل از ساختن هر WebView صدا زده شود. [web:944]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        val info = TextView(this).apply {
            text = "Loading…"
            setPadding(24, 24, 24, 24)
        }

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setBackgroundColor(Color.WHITE)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info)
            addView(
                webView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        setContentView(root)

        // دریافت متن Share شده از EXTRA_TEXT برای ACTION_SEND و text/plain. [web:645]
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (sharedText.isNullOrBlank()) {
            info.text = "No shared text received."
            finish()
            return
        }

        val toLoad = sharedText.trim()
        info.text = "Opening: $toLoad"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.postDelayed({
                    try {
                        val bmp = captureWholeWebViewSafely(view)
                        val name = GallerySaver.saveToGallery(
                            this@ShareToImageActivity,
                            bmp,
                            "share"
                        )
                        bmp.recycle()
                        info.text = "Saved: $name"
                    } catch (_: Throwable) {
                        // اگر خواستی لاگ هم اضافه می‌کنیم
                    } finally {
                        finish()
                    }
                }, 600)
            }
        }

        // اگر لینک بود loadUrl، اگر متن ساده بود HTML
        if (toLoad.startsWith("http://") || toLoad.startsWith("https://")) {
            webView.loadUrl(toLoad)
        } else {
            val safe = toLoad
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

            val html = """
                <html>
                  <head><meta name="viewport" content="width=device-width, initial-scale=1.0"/></head>
                  <body style="font-family:sans-serif; background:#ffffff; color:#111; padding:16px;">
                    <pre style="white-space:pre-wrap;">$safe</pre>
                  </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private fun captureWholeWebViewSafely(webView: WebView): Bitmap {
        // سقف ارتفاع برای جلوگیری از OOM
        val maxHeightPx = 12000

        val width = webView.width.coerceAtLeast(1)
        val contentHeightPx = (webView.contentHeight * webView.scale).toInt()
            .coerceAtLeast(webView.height)
        val height = contentHeightPx.coerceAtMost(maxHeightPx).coerceAtLeast(1)

        // MeasureSpec.EXACTLY [web:917]
        webView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                width,
                android.view.View.MeasureSpec.EXACTLY
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                height,
                android.view.View.MeasureSpec.EXACTLY
            )
        )
        webView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        webView.draw(canvas)

        return bitmap
    }
}
