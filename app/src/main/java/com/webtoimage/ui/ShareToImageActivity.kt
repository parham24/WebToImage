package com.webtoimage.ui

import com.webtoimage.R
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.WebViewPdfSaver
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.webtoimage.util.GallerySaver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ShareToImageActivity : AppCompatActivity() {

    private var originalUa: String? = null
    private var currentLoad: String? = null
    private var isUrl: Boolean = false

    // fallback اگر DocumentStartScript پشتیبانی نشد
    private var injectedOnce = false
    private var docStartScriptInstalled = false

    private var desktopMode = true
    private var selectOn = false

    private lateinit var toolbar: MaterialToolbar
    private lateinit var webView: WebView
    private lateinit var overlay: SelectionOverlayView
    private lateinit var fabCrop: ExtendedFloatingActionButton
    private lateinit var fabSelect: FloatingActionButton
    private lateinit var fabClear: FloatingActionButton

    // آیتم‌های منو (برنامه‌نویسی)
    private var menuReloadId = 1
    private var menuDesktopId = 2
    private var menuPdfId = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remote Debug (chrome://inspect) فقط در build دیباگ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }

        // UI از XML
        setContentView(R.layout.activity_share_to_image)

        toolbar = findViewById(R.id.toolbar)
        webView = findViewById(R.id.webView)
        overlay = findViewById(R.id.overlay)
        fabCrop = findViewById(R.id.fabCrop)
        fabSelect = findViewById(R.id.fabSelect)
        fabClear = findViewById(R.id.fabClear)

        setSupportActionBar(toolbar)

        val prefs = getSharedPreferences("webtoimage", MODE_PRIVATE)
        desktopMode = prefs.getBoolean("desktop_mode", true)

        setupToolbarMenu(prefs)

        // WebView settings
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(Color.WHITE)

        originalUa = webView.settings.userAgentString
        applyDesktopMode(webView, desktopMode)

        // تزریق viewport از Document Start (برای مشکل GitHub و سایت‌هایی که viewport موبایل می‌گذارند)
        docStartScriptInstalled = installDesktopViewportDocStartScript(webView)

        // JS console logs => Logcat
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.d(
                    "W2I/Console",
                    "${cm.message()} -- line ${cm.lineNumber()} @ ${cm.sourceId()} [${cm.messageLevel()}]"
                )
                return true
            }
        }

        // Overlay: پیش‌فرض خاموش تا اسکرول WebView کار کند
        overlay.visibility = View.GONE
        overlay.setSelectionEnabled(false)

        // دکمه‌ها
        fabCrop.isEnabled = false

        fabSelect.setOnClickListener {
    selectOn = !selectOn
    overlay.clearSelection()
    overlay.setSelectionEnabled(selectOn)
    overlay.visibility = if (selectOn) View.VISIBLE else View.GONE

    if (selectOn) {
        overlay.bringToFront()
        overlay.requestFocus()
        Toast.makeText(this, "Drag روی صفحه برای انتخاب کادر", Toast.LENGTH_SHORT).show()
    }
        }
        fabClear.setOnClickListener {
            overlay.clearSelection()
        }

        // ذخیره تصویر (viewport) + crop
        fabCrop.setOnClickListener {
            try {
                val full = captureViewport(webView)

                // Rect را فریز کن که در لحظه‌ی redraw تغییر نکند
                val cropRect = overlay.getSelectionRect()?.let { RectF(it) }

                val outBitmap = if (cropRect != null) {
                    // تبدیل Rect از مختصات overlay به مختصات webView
                    val overlayLoc = IntArray(2)
                    val webLoc = IntArray(2)
                    overlay.getLocationOnScreen(overlayLoc)
                    webView.getLocationOnScreen(webLoc)

                    val rectWeb = RectF(cropRect)
                    rectWeb.offset(
                        (overlayLoc[0] - webLoc[0]).toFloat(),
                        (overlayLoc[1] - webLoc[1]).toFloat()
                    )

                    rectWeb.left = rectWeb.left.coerceIn(0f, webView.width.toFloat())
                    rectWeb.right = rectWeb.right.coerceIn(0f, webView.width.toFloat())
                    rectWeb.top = rectWeb.top.coerceIn(0f, webView.height.toFloat())
                    rectWeb.bottom = rectWeb.bottom.coerceIn(0f, webView.height.toFloat())

                    cropFromViewport(full, rectWeb, webView.width, webView.height)
                } else {
                    full
                }

                val name = GallerySaver.saveToGallery(this, outBitmap, "share_crop")
                if (outBitmap !== full) outBitmap.recycle()
                full.recycle()

                Toast.makeText(this, "Saved image: $name", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 600)

            } catch (_: Throwable) {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        }

        // دریافت متن Share شده
        val incoming: String? = when (intent?.action) {

    Intent.ACTION_VIEW -> {
        intent.dataString
    }

    Intent.ACTION_SEND -> {
        val t1 = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val t2 = intent.clipData?.let { cd ->
            if (cd.itemCount > 0) cd.getItemAt(0).coerceToText(this)?.toString() else null
        }
        t1 ?: t2
    }

    else -> null
}

if (incoming.isNullOrBlank()) {
    Toast.makeText(this, "Nothing received from Share", Toast.LENGTH_LONG).show()
    return
}

currentLoad = incoming.trim()
isUrl = currentLoad!!.startsWith("http://") || currentLoad!!.startsWith("https://")
        webView.webViewClient = object : WebViewClient() {

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                Log.e(
                    "W2I/WebError",
                    "url=${request.url} main=${request.isForMainFrame} code=${error.errorCode} desc=${error.description}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                Log.e(
                    "W2I/HttpError",
                    "url=${request.url} main=${request.isForMainFrame} status=${errorResponse.statusCode}"
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                fabCrop.isEnabled = true
                toolbar.menu.findItem(menuPdfId)?.isEnabled = true

                // لاگ viewport/UA برای تشخیص موبایل/دسکتاپ
                view.evaluateJavascript(
                    "(function(){return JSON.stringify({ua:navigator.userAgent,w:window.innerWidth,dpr:window.devicePixelRatio,viewport:(document.querySelector('meta[name=viewport]')||{}).content});})();"
                ) { v ->
                    Log.d("W2I/Viewport", v ?: "null")
                }

                // fallback فقط اگر DocumentStartScript نداریم
                if (desktopMode && !docStartScriptInstalled && !injectedOnce) {
                    injectedOnce = true
                    forceDesktopViewport(view)
                    view.reload()
                    return
                }
            }
        }

        reloadCurrent(webView)
    }

    private fun setupToolbarMenu(prefs: android.content.SharedPreferences) {
        val m = toolbar.menu
        m.clear()

        val itemDesktop = m.add(0, menuDesktopId, 0, "Desktop mode")
        itemDesktop.isCheckable = true
        itemDesktop.isChecked = desktopMode

        val itemReload = m.add(0, menuReloadId, 1, "Reload")

        val itemPdf = m.add(0, menuPdfId, 2, "Save PDF")
        itemPdf.isEnabled = false

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                menuDesktopId -> {
                    desktopMode = !item.isChecked
                    item.isChecked = desktopMode
                    prefs.edit().putBoolean("desktop_mode", desktopMode).apply()

                    injectedOnce = false
                    applyDesktopMode(webView, desktopMode)

                    webView.clearCache(true)
                    webView.clearHistory()

                    overlay.clearSelection()
                    selectOn = false
                    overlay.setSelectionEnabled(false)
                    overlay.visibility = View.GONE

                    fabCrop.isEnabled = false
                    toolbar.menu.findItem(menuPdfId)?.isEnabled = false

                    reloadCurrent(webView)
                    true
                }

                menuReloadId -> {
                    injectedOnce = false
                    overlay.clearSelection()
                    webView.reload()
                    true
                }

                menuPdfId -> {
                    Toast.makeText(this, "Generating PDF…", Toast.LENGTH_SHORT).show()
                    fabCrop.isEnabled = false
                    toolbar.menu.findItem(menuPdfId)?.isEnabled = false

                    createPdfFromWebView(webView) { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun installDesktopViewportDocStartScript(webView: WebView): Boolean {
        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false

            // مهم: Script فقط وقتی UA دسکتاپ باشد اعمال می‌شود
            val script = """
                (function(){
                  try{
                    var ua = navigator.userAgent || "";
                    if (ua.indexOf("Windows NT") === -1) return;

                    function applyVp(){
                      var head = document.head || document.getElementsByTagName('head')[0];
                      if(!head) return;
                      var m = document.querySelector('meta[name="viewport"]');
                      if(!m){ m=document.createElement('meta'); m.name='viewport'; head.appendChild(m); }
                      m.setAttribute('content','width=1200, initial-scale=1.0');
                    }

                    applyVp();
                    var t=setInterval(applyVp,50);
                    setTimeout(function(){ try{clearInterval(t);}catch(e){} }, 2000);
                  }catch(e){}
                })();
            """.trimIndent()

            val rules = setOf("*")
            WebViewCompat.addDocumentStartJavaScript(webView, script, rules)
            true
        } catch (_: Throwable) {
            false
        }
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

        val desktopUA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        s.userAgentString = desktopUA
        s.useWideViewPort = true
        s.loadWithOverviewMode = true

        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false

        s.domStorageEnabled = true
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

        val scaleX = src.width.toFloat() / viewW.toFloat()
        val scaleY = src.height.toFloat() / viewH.toFloat()

        val x = (l * scaleX).roundToInt().coerceIn(0, src.width - 1)
        val y = (t * scaleY).roundToInt().coerceIn(0, src.height - 1)
        val w = ((r - l) * scaleX).roundToInt().coerceAtLeast(1).coerceAtMost(src.width - x)
        val h = ((b - t) * scaleY).roundToInt().coerceAtLeast(1).coerceAtMost(src.height - y)

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
                try {
                    pfd.close()
                } catch (_: Throwable) {
                }

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

    private fun createPdfOutput(baseName: String): Pair<Uri?, java.io.File?> {
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
            val dir = java.io.File(
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "WebToImage"
            )
            if (!dir.exists()) dir.mkdirs()
            Pair(null, java.io.File(dir, fileName))
        }
    }
}
