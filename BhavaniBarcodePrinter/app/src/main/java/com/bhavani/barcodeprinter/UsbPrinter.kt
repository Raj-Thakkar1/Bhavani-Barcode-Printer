package com.bhavani.barcodeprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * Talks to the TSC printer over USB-OTG.
 *
 * Bug fix history: previously every single print job re-sent the SIZE/GAP/OFFSET header,
 * which makes TSC-protocol printers re-run gap-sensor calibration on every job. On marginal
 * sensors/label stock that occasionally ate a label mid-batch ("1 prints, 1 blank, then
 * continues"). Now the header is sent at most once per device connection (cached below),
 * or on-demand via calibrate(). Also, sendRaw() now verifies every byte was actually
 * transmitted (chunked, since usbfs bulk transfers can silently truncate large single writes)
 * instead of trusting `bulkTransfer(...) >= 0` alone.
 */
class UsbPrinter(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.bhavani.barcodeprinter.USB_PERMISSION"
        const val TSC_VENDOR_ID = 0x1203 // TSC Auto ID Technology Co., Ltd.
        private const val CHUNK_SIZE = 4096
    }

    // getSystemService(Context.USB_SERVICE) can return null on devices/emulators that don't
    // expose the USB host framework (the "as UsbManager" unsafe cast this used to be would
    // throw a NullPointerException here on EVERY app launch on such a device, since this
    // class is constructed unconditionally in AppContainer.init() from MainActivity.onCreate()
    // - i.e. "app keeps stopping" immediately on open, before any UI is shown).
    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    /** deviceName -> "has the SIZE/GAP header already been sent this app session?" */
    private val headerSentFor = HashSet<String>()

    /** True if this device actually has a working USB host service to talk to. */
    fun isUsbHostSupported(): Boolean = usbManager != null

    fun findCandidateDevices(): List<UsbDevice> {
        val manager = usbManager ?: return emptyList()
        return manager.deviceList.values.filter { device ->
            device.vendorId == TSC_VENDOR_ID || isPrinterClassDevice(device)
        }
    }

    private fun isPrinterClassDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
        }
        return false
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager?.hasPermission(device) ?: false

    fun requestPermission(device: UsbDevice, onResult: (granted: Boolean) -> Unit) {
        val manager = usbManager
        if (manager == null) {
            onResult(false)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onResult(granted)
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        }
        manager.requestPermission(device, pendingIntent)
    }

    /** Forces the header (and therefore the printer's gap calibration) to be resent next time. */
    fun forgetCalibration(device: UsbDevice) {
        headerSentFor.remove(device.deviceName)
    }

    /** Explicit "Calibrate printer" action for the Settings screen. headerText = SIZE/GAP/... lines only. */
    fun calibrate(device: UsbDevice, headerText: String): Pair<Boolean, String> {
        if (headerText.isBlank()) return false to "No header found in the current PRN template."
        val (ok, msg) = sendRaw(device, "$headerText\n")
        if (ok) headerSentFor.add(device.deviceName)
        return ok to msg
    }

    /**
     * Sends one print job. Sends the calibration header first ONLY if it hasn't been sent
     * yet for this device (see class doc) - this is the core fix for the blank-label bug.
     * headerText/bodyText come from TsplBuilder.splitHeaderAndBody() on the current template.
     */
    fun printJob(device: UsbDevice, headerText: String, bodyText: String): Pair<Boolean, String> {
        if (headerText.isNotBlank() && !headerSentFor.contains(device.deviceName)) {
            val (ok, msg) = sendRaw(device, "$headerText\n")
            if (!ok) return false to "Calibration failed: $msg"
            headerSentFor.add(device.deviceName)
        }
        return sendRaw(device, bodyText)
    }

    /** Low-level: opens the device, claims the printer interface, writes bytes, verifies count, closes. */
    private fun sendRaw(device: UsbDevice, data: String): Pair<Boolean, String> {
        val manager = usbManager
            ?: return false to "USB host mode isn't available on this device."

        if (!manager.hasPermission(device)) {
            return false to "No USB permission for this device yet."
        }

        val printerInterface: UsbInterface? = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            ?: if (device.interfaceCount > 0) device.getInterface(0) else null

        if (printerInterface == null) {
            return false to "No usable USB interface found on this device."
        }

        val outEndpoint: UsbEndpoint? = (0 until printerInterface.endpointCount)
            .map { printerInterface.getEndpoint(it) }
            .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }

        if (outEndpoint == null) {
            return false to "No OUT endpoint found - is this really the printer?"
        }

        val connection: UsbDeviceConnection = manager.openDevice(device)
            ?: return false to "Could not open USB device."

        return try {
            if (!connection.claimInterface(printerInterface, true)) {
                return false to "Could not claim USB interface (busy or in use by another app)."
            }
            // ISO-8859-1 is a 1:1 byte<->char mapping, so this round-trips raw binary
            // (e.g. embedded BITMAP payloads) exactly, same as the Python desktop app's latin-1 encode.
            val bytes = data.toByteArray(Charsets.ISO_8859_1)

            var offset = 0
            var totalSent = 0
            while (offset < bytes.size) {
                val len = minOf(CHUNK_SIZE, bytes.size - offset)
                val sent = connection.bulkTransfer(outEndpoint, bytes, offset, len, 5000)
                if (sent < 0) {
                    connection.releaseInterface(printerInterface)
                    connection.close()
                    return false to "USB write failed at byte $offset of ${bytes.size} (bulkTransfer returned $sent)."
                }
                totalSent += sent
                offset += len
                if (sent < len) {
                    connection.releaseInterface(printerInterface)
                    connection.close()
                    return false to "USB write incomplete: sent $totalSent of ${bytes.size} bytes."
                }
            }

            connection.releaseInterface(printerInterface)
            connection.close()
            true to "Sent $totalSent bytes to printer successfully."
        } catch (e: Exception) {
            try { connection.close() } catch (_: Exception) {}
            false to "USB printer error: ${e.message}"
        }
    }
}
