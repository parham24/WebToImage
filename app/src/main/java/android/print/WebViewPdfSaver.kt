package android.print

import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.webkit.WebView

object WebViewPdfSaver {

    fun writeWebViewToPdf(
        webView: WebView,
        jobName: String,
        attributes: PrintAttributes,
        output: ParcelFileDescriptor,
        onDone: (ok: Boolean, error: String?) -> Unit
    ) {
        val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.createPrintDocumentAdapter(jobName)
        } else {
            @Suppress("DEPRECATION")
            webView.createPrintDocumentAdapter()
        }

        adapter.onLayout(
            null,
            attributes,
            null,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFailed(error: CharSequence?) {
                    onDone(false, error?.toString())
                }

                override fun onLayoutCancelled() {
                    onDone(false, "Layout cancelled")
                }

                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        output,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFailed(error: CharSequence?) {
                                onDone(false, error?.toString())
                            }

                            override fun onWriteCancelled() {
                                onDone(false, "Write cancelled")
                            }

                            override fun onWriteFinished(pages: Array<PageRange>) {
                                onDone(true, null)
                            }
                        }
                    )
                }
            },
            Bundle()
        )
    }
}
