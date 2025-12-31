package com.webtoimage.print

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession

class WebToImagePrintService : PrintService() {

    private val PRINTER_LOCAL_ID = "webtoimage_virtual_printer"

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {

            private val pid: PrinterId = generatePrinterId(PRINTER_LOCAL_ID)

            private fun buildPrinterInfo(): PrinterInfo {
                val caps = PrinterCapabilitiesInfo.Builder(pid)
                    // A4 و حداقل حاشیه
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    // رزولوشن پیش‌فرض
                    .addResolution(
                        PrintAttributes.Resolution("r300", "300 dpi", 300, 300),
                        true
                    )
                    // حالت‌های رنگ
                    .setColorModes(
                        PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                        PrintAttributes.COLOR_MODE_COLOR
                    )
                    .build()

                return PrinterInfo.Builder(pid, "WebToImage (Save as Image)", PrinterInfo.STATUS_IDLE)
                    .setCapabilities(caps)
                    .build()
            }

            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                // می‌توانی فقط همان پرینتر را دوباره اضافه/آپدیت کنی
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
                // فعلاً چیزی لازم نیست
            }

            override fun onStopPrinterStateTracking(printerId: PrinterId) {
                // فعلاً چیزی لازم نیست
            }

            override fun onStopPrinterDiscovery() {
                // فعلاً چیزی لازم نیست
            }

            override fun onDestroy() {
                // cleanup اگر لازم شد
            }
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        // هشدار: این فقط برای رد شدن از مرحله build است و منطق چاپ واقعی نیست.
        printJob.start()
        // اگر می‌خواهی Job تمام شود (و گیر نکند) معمولاً باید complete() هم صدا زده شود.
        // printJob.complete()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }
}
