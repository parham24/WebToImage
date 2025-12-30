package com.webtoimage.print

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
                    // یک “کاغذ” پیش‌فرض می‌گذاریم (A4) و حاشیه صفر
                    .addMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4, true)
                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                    // یک رزولوشن پیش‌فرض (300dpi)
                    .addResolution(
                        PrinterCapabilitiesInfo.Resolution("r300", "300 dpi", 300, 300),
                        true
                    )
                    // رنگ/سیاه‌سفید
                    .setColorModes(
                        PrinterCapabilitiesInfo.COLOR_MODE_COLOR or PrinterCapabilitiesInfo.COLOR_MODE_MONOCHROME,
                        PrinterCapabilitiesInfo.COLOR_MODE_COLOR
                    )
                    .build()

                return PrinterInfo.Builder(pid, "WebToImage (Save as Image)", PrinterInfo.STATUS_IDLE)
                    .setCapabilities(caps)
                    .build()
            }

            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>?) {
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>?) {
                addPrinters(listOf(buildPrinterInfo()))
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId?) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId?) {}
            override fun onStopPrinterDiscovery() {}
            override fun onDestroy() {}
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        // فعلاً کاری نمی‌کنیم (مرحله بعد: گرفتن PDF و باز کردن UI)
        printJob.start()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }
}
