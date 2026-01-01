package com.webtoimage.print

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.webtoimage.util.AppLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val pfd = printJob.document?.data
        if (pfd == null) {
            AppLog.e(this, "PrintJob document/data is null")
            printJob.fail("No document data")
            return
        }

        printJob.start()

        Thread {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                // جای ذخیره خروجی (بدون مجوز، داخل فضای اختصاصی اپ روی حافظه خارجی)
                val outDir = File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "WebToImage/$ts"
                ).apply { mkdirs() }

                val renderer = openPdfRendererSafely(pfd)
                renderer.use { pdf ->
                    val pageCount = pdf.pageCount
                    AppLog.i(this, "PDF pages=$pageCount outDir=${outDir.absolutePath}")

                    val scale = 2f // کیفیت: 2 خوبه، 3 خیلی باکیفیت ولی سنگین
                    for (i in 0 until pageCount) {
                        val page = pdf.openPage(i)
                        try {
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                            val matrix = Matrix().apply { setScale(scale, scale) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val outFile = File(outDir, "page_${i + 1}.png")
                            FileOutputStream(outFile).use { fos ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            bitmap.recycle()

                            AppLog.i(this, "Saved ${outFile.absolutePath}")
                        } finally {
                            page.close()
                        }
                    }
                }

                printJob.complete()
                AppLog.i(this, "Job completed OK")
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
            // اگر seekable باشد همین کار می‌کند
            PdfRenderer(pfd)
        } catch (e: IllegalArgumentException) {
            // اگر seekable نبود، اول به فایل موقت کپی می‌کنیم
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
