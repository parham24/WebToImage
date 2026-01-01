package com.webtoimage.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.webtoimage.util.GallerySaver
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.print.PrintAttributes
import android.print.WebViewPdfSaver

class ShareToImageActivity : Activity() {

    private var originalUa: String? = null
    private var currentLoad: String? = null
    private var isUrl: Boolean = false

    // برای viewport injection فقط یک بار (اگر لازم شد)
    private var injectedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        val prefs = getSharedPreferences("webtoimage", MODE_PRIVATE)

        // پیش‌فرض دسکتاپ
        val desktopDefault = prefs.getBoolean("desktop_mode", true)

        val info = TextView(this).apply {
            text = "Loading…"
            setPadding(24, 16, 24, 16)
        }

        val modeSwitch = Switch(this).apply {
            text = "Desktop mode"
            isChecked = desktopDefault
        }

        val reloadBtn = Button(this).apply { text = "RELOAD" }
        val clearSelBtn = Button(this).apply { text = "CLEAR" }

        val selectToggle = ToggleButton(this).apply {
            textOn = "SELECT: ON"
            textOff = "SELECT: OFF"
            isChecked = false
        }

        val saveImgBtn = Button(this).apply {
            text = "CROP & SAVE (IMAGE)"
            isEnabled = false
        }

        val savePdfBtn = Button(this).apply {
            text = "SAVE PDF (FULL PAGE)"
            isEnabled = false
        }

        // ردیف ۱: سوییچ + Reload
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 8)

            addView(
                modeSwitch,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(reloadBtn)
        }

        // ردیف ۲: دکمه‌ها + انتخاب
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 0, 24, 8)
            addView(selectToggle)
            addView(clearSelBtn)
            addView(saveImgBtn)
            addView(savePdfBtn)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row1)
            addView(row2)
        }

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.WHITE)
        }

        val overlay = SelectionOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // خیلی مهم: پیش‌فرض خاموش تا اسکرول WebView کار کند
            visibility = View.GONE
            setSelectionEnabled(false)
        }

        val webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(webView)
            addView(overlay)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar)
            addView(info)
            addView(webContainer)
        }
        setContentView(root)

        // دریافت متن Share شده
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (sharedText.isNullOrBlank()) {
            info.text = "No shared text received."
            finish()
            return
        }

        currentLoad = sharedText.trim()
        isUrl = currentLoad!!.startsWith("http://") || currentLoad!!.startsWith("https://")

        originalUa = webView.settings.userAgentString
        applyDesktopMode(webView, modeSwitch.isChecked)

        // SELECT toggle: روشن = اجازه انتخاب کادر، خاموش = اسکرول آزاد
        selectToggle.setOnCheckedChangeListener { _, on ->
            overlay.clearSelection()
            overlay.setSelectionEnabled(on)
            overlay.visibility = if (on) View.VISIBLE else View.GONE
        }

        clearSelBtn.setOnClickListener { overlay.clearSelection() }

        modeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("desktop_mode", checked).apply()
            injectedOnce = false

            applyDesktopMode(webView, checked)

            // کمک به اینکه نتیجه قبلی cache نشود
            webView.clearCache(true)
            webView.clearHistory()

            info.text = "Reloading…"
            saveImgBtn.isEnabled = false
            savePdfBtn.isEnabled = false
            overlay.clearSelection()
            reloadCurrent(webView)
        }

        reloadBtn.setOnClickListener {
            injectedOnce = false
            info.text = "Reloading…"
            saveImgBtn.isEnabled = false
            savePdfBtn.isEnabled = false
            overlay.clearSelection()
            webView.reload()
        }

        // ذخیره تصویر (viewport) + crop
        saveImgBtn.setOnClickListener {
            try {
                val full = captureViewport(webView)
                val cropRect = overlay.getSelectionRect()

                val outBitmap = if (cropRect != null) {
                    cropFromViewport(full, cropRect, webView.width, webView.height)
                } else {
                    full
                }

                val name = GallerySaver.saveToGallery(this, outBitmap, "share_crop")
                if (outBitmap !== full) outBitmap.recycle()
                full.recycle()

                info.text = "Saved image: $name"
            } catch (_: Throwable) {
            } finally {
                finish()
            }
        }

        // ذخیره PDF کل صفحه
        savePdfBtn.setOnClickListener {
            info.text = "Generating PDF…"
            saveImgBtn.isEnabled = false
            savePdfBtn.isEnabled = false

            createPdfFromWebView(webView) { msg ->
                info.text = msg
                finish()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                saveImgBtn.isEnabled = true
                savePdfBtn.isEnabled = true

                // اگر desktop روشن است، یک بار viewport را هم (در صورت نیاز) اجبار کن و reload کن
                if (modeSwitch.isChecked && !injectedOnce) {
                    injectedOnce = true
                    forceDesktopViewport(view)
                    view.reload()
                    return
                }

                info.text =
                    "Loaded. SELECT: OFF برای اسکرول، SELECT: ON برای انتخاب کادر. سپس Crop & Save یا Save PDF."
            }
        }

        reloadCurrent(webView)
    }

    private fun reloadCurrent(webView: WebView) {
        val toLoad = currentLoad ?: return
        if (isUrl) {
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

    private fun applyDesktopMode(webView: WebView, enabled: Boolean) {
        val s = webView.settings

        if (!enabled) {
            s.userAgentString = originalUa ?: s.userAgentString
            s.useWideViewPort = false
            s.loadWithOverviewMode = false
            return
        }

        // UA دسکتاپ ثابت
        val desktopUA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        s.userAgentString = desktopUA
        s.useWideViewPort = true
        s.loadWithOverviewMode = true

        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false

        s.domStorageEnabled = true

        // اجازه بده WebView خودش scale مناسب را تنظیم کند
        webView.setInitialScale(0)
    }

    private fun forceDesktopViewport(webView: WebView) {
        val js = """
            (function(){
              var head = document.head || document.getElementsByTagName('head')[0];
              if(!head) return;
              var m = document.querySelector('meta[name="viewport"]');
              if(!m){ m=document.createElement('meta'); m.name='viewport'; head.appendChild(m); }
              m.setAttribute('content','width=1200, initial-scale=1.0');
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun captureViewport(webView: WebView): Bitmap {
        val w = webView.width.coerceAtLeast(1)
        val h = webView.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        webView.draw(canvas)
        return bitmap
    }

    private fun cropFromViewport(src: Bitmap, cropRect: RectF, viewW: Int, viewH: Int): Bitmap {
        val left = cropRect.left.coerceIn(0f, viewW.toFloat())
        val top = cropRect.top.coerceIn(0f, viewH.toFloat())
        val right = cropRect.right.coerceIn(0f, viewW.toFloat())
        val bottom = cropRect.bottom.coerceIn(0f, viewH.toFloat())

        val l = min(left, right)
        val t = min(top, bottom)
        val r = max(left, right)
        val b = max(top, bottom)

        val x = l.toInt().coerceIn(0, src.width - 1)
        val y = t.toInt().coerceIn(0, src.height - 1)
        val w = (r - l).toInt().coerceAtLeast(1).coerceAtMost(src.width - x)
        val h = (b - t).toInt().coerceAtLeast(1).coerceAtMost(src.height - y)

        return Bitmap.createBitmap(src, x, y, w, h)
    }

    private fun createPdfFromWebView(webView: WebView, done: (message: String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            done("PDF is not supported on this Android version.")
            return
        }

        val jobName = "WebToImage_${System.currentTimeMillis()}"
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val (outUri, outFile) = createPdfOutput(jobName)

        try {
            val pfd = if (outUri != null) {
                contentResolver.openFileDescriptor(outUri, "w")?.fileDescriptor
                    ?.let { android.os.ParcelFileDescriptor.dup(it) }
            } else {
                android.os.ParcelFileDescriptor.open(
                    outFile,
                    android.os.ParcelFileDescriptor.MODE_READ_WRITE
                )
            }

            if (pfd == null) {
                done("Cannot open output for PDF.")
                return
            }

            WebViewPdfSaver.writeWebViewToPdf(webView, jobName, attrs, pfd) { ok, err ->
                try { pfd.close() } catch (_: Throwable) {}

                if (!ok) {
                    done("PDF failed: ${err ?: "unknown"}")
                    return@writeWebViewToPdf
                }

                if (outUri == null && outFile != null) {
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(outFile.absolutePath),
                        arrayOf("application/pdf"),
                        null
                    )
                }

                done(
                    if (outUri != null) "Saved PDF in Downloads/WebToImage"
                    else "Saved PDF: ${outFile?.absolutePath ?: ""}"
                )
            }
        } catch (t: Throwable) {
            done("PDF error: ${t.message ?: "unknown"}")
        }
    }

    private fun createPdfOutput(baseName: String): Pair<Uri?, File?> {
        val fileName = "$baseName.pdf"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/WebToImage"
                )
            }
            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )
            Pair(uri, null)
        } else {
            val dir = File(
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "WebToImage"
            )
            if (!dir.exists()) dir.mkdirs()
            Pair(null, File(dir, fileName))
        }
    }

    private class SelectionOverlayView(context: Activity) : View(context) {
        private val shadePaint = Paint().apply {
            color = 0x66000000
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        private var selectionEnabled = false

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var dragging = false

        fun setSelectionEnabled(enabled: Boolean) {
            selectionEnabled = enabled
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // وقتی انتخاب خاموش است، event را مصرف نکن تا WebView اسکرول کند
            if (!selectionEnabled) return false

            // وقتی انتخاب روشن است، اجازه نده parent اسکرول را بدزدد
            parent?.requestDisallowInterceptTouchEvent(true)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    startX = event.x
                    startY = event.y
                    endX = startX
                    endY = startY
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return false
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = getSelectionRect() ?: return

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)

            val clearPaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawRect(rect, clearPaint)
            canvas.restoreToCount(saved)

            canvas.drawRect(rect, borderPaint)
        }

        fun clearSelection() {
            startX = 0f; startY = 0f; endX = 0f; endY = 0f
            invalidate()
        }

        fun getSelectionRect(): RectF? {
            if (abs(endX - startX) < 20f || abs(endY - startY) < 20f) return null
            val l = min(startX, endX).coerceIn(0f, width.toFloat())
            val t = min(startY, endY).coerceIn(0f, height.toFloat())
            val r = max(startX, endX).coerceIn(0f, width.toFloat())
            val b = max(startY, endY).coerceIn(0f, height.toFloat())
            return RectF(l, t, r, b)
        }
    }
}
