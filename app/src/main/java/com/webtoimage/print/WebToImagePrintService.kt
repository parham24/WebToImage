package com.webtoimage.print

import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.webtoimage.util.AppLog

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
        try {
            AppLog.i(
                this,
                "onPrintJobQueued id=${printJob.id} queued=${printJob.isQueued} started=${printJob.isStarted}"
            )

            val pfd: ParcelFileDescriptor? = printJob.document?.data
            if (pfd == null) {
                AppLog.e(this, "PrintJob document/data is null")
                printJob.fail("No document data")
                return
            }

            // فعلاً فقط برای اینکه کرش نکند و job گیر نکند:
            val started = printJob.start()
            AppLog.i(this, "printJob.start() = $started")

            val completed = printJob.complete()
            AppLog.i(this, "printJob.complete() = $completed")
        } catch (t: Throwable) {
            AppLog.e(this, "Print crashed", t)
            runCatching { printJob.fail(t.message ?: "Crash") }
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        runCatching { printJob.cancel() }
    }
}
