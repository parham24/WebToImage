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
import com.webtoimage.util.AppLog
import com.webtoimage.util.GallerySaver

class ShareToImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // دریافت متن share شده
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (sharedText.isNullOrBlank()) {
            AppLog.e(this, "No shared text received")
            finish()
            return
        }

        val toLoad = sharedText.trim()
        info.text = "Opening: $toLoad"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // کمی مکث تا رندر کامل‌تر شود
                view.postDelayed({
                    try {
                        val bmp = captureWholeWebViewSafely(view)
                        val name = GallerySaver.saveToGallery(this@ShareToImageActivity, bmp, "share")
                        bmp.recycle()

                        AppLog.i(this@ShareToImageActivity, "Saved to Gallery: $name")
                    } catch (t: Throwable) {
                        AppLog.e(this@ShareToImageActivity, "Capture failed", t)
                    } finally {
                        finish()
                    }
                }, 600)
            }
        }

        // اگر لینک بود loadUrl، اگر متن عادی بود به صورت HTML ساده نمایش بده
        if (toLoad.startsWith("http://") || toLoad.startsWith("https://")) {
            webView.loadUrl(toLoad)
        } else {
            val html = """
                <html>
                  <body style="font-family:sans-serif; background:#ffffff; color:#111; padding:16px;">
                    <pre>${
                        toLoad
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                    }</pre>
                  </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private fun captureWholeWebViewSafely(webView: WebView): Bitmap {
        // طبق تجربه، گرفتن “کل صفحه” ممکن است خیلی بلند شود؛ برای جلوگیری از OOM سقف می‌گذاریم.
        val maxHeightPx = 12000

        val width = webView.width.coerceAtLeast(1)
        val contentHeightPx = (webView.contentHeight * webView.scale).toInt().coerceAtLeast(webView.height)
        val height = contentHeightPx.coerceAtMost(maxHeightPx).coerceAtLeast(1)

        // یک layout موقت برای اینکه draw درست کار کند
        webView.measure(
            ViewGroup.MeasureSpec.makeMeasureSpec(width, ViewGroup.MeasureSpec.EXACTLY),
            ViewGroup.MeasureSpec.makeMeasureSpec(height, ViewGroup.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        webView.draw(canvas)

        return bitmap
    }
}
