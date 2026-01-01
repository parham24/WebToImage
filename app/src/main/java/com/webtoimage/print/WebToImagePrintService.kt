package com.webtoimage.print

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.webtoimage.util.AppLog
import com.webtoimage.util.GallerySaver
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WebToImagePrintService : PrintService() {

    private val PRINTER_LOCAL_ID = "webtoimage_virtual_printer"

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {

            private val pid: PrinterId = generatePrinterId(PRINTER_LOCAL_ID)

            private fun buildPrinterInfo(): PrinterInfo {
                val caps = PrinterCapabilitiesInfo.Builder(pid)
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .addResolution(
                        PrintAttributes.Resolution("r300", "300 dpi", 300, 300),
                        true
                    )
                    .setColorModes(
                        PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                        PrintAttributes.COLOR_MODE_COLOR
                    )
                    .build()

                return PrinterInfo.Builder(
                    pid,
                    "WebToImage (Save as Image)",
                    PrinterInfo.STATUS_IDLE
                ).setCapabilities(caps).build()
            }

            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterDiscovery() {}
            override fun onDestroy() {}
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        AppLog.i(
            this,
            "onPrintJobQueued id=${printJob.id} queued=${printJob.isQueued} started=${printJob.isStarted}"
        )

        val pfd = printJob.document?.data
        if (pfd == null) {
            AppLog.e(this, "PrintJob document/data is null")
            printJob.fail("No document data")
            return
        }

        val started = printJob.start()
        AppLog.i(this, "printJob.start() = $started")

        Thread {
            try {
                val renderer = openPdfRendererSafely(pfd)
                renderer.use { pdf ->
                    val pageCount = pdf.pageCount
                    AppLog.i(this, "PDF pages=$pageCount")

                    val scale = 2f
                    for (i in 0 until pageCount) {
                        val page = pdf.openPage(i)
                        try {
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                            val matrix = Matrix().apply { setScale(scale, scale) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val name = GallerySaver.saveToGallery(
                                this@WebToImagePrintService,
                                bitmap,
                                "print_page_${i + 1}"
                            )
                            AppLog.i(this, "Saved to Gallery: $name")

                            bitmap.recycle()
                        } finally {
                            page.close()
                        }
                    }
                }

                val completed = printJob.complete()
                AppLog.i(this, "printJob.complete() = $completed")
            } catch (t: Throwable) {
                AppLog.e(this, "Convert PDF->Image failed", t)
                runCatching { printJob.fail(t.message ?: "Convert failed") }
            } finally {
                runCatching { pfd.close() }
            }
        }.start()
    }

    private fun openPdfRendererSafely(pfd: ParcelFileDescriptor): PdfRenderer {
        return try {
            PdfRenderer(pfd)
        } catch (e: IllegalArgumentException) {
            // اگر PFD seekable نبود، اول PDF را در cache کپی می‌کنیم. [web:621]
            AppLog.e(this, "PFD not seekable; copying to cache", e)

            val tmp = File(cacheDir, "printjob_${System.currentTimeMillis()}.pdf")
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }

            val tmpPfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(tmpPfd)
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        runCatching { printJob.cancel() }
    }
}
