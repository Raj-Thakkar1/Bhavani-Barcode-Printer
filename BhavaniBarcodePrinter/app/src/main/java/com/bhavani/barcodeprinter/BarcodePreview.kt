package com.bhavani.barcodeprinter

import android.graphics.Bitmap
import android.graphics.Color as AColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer

/**
 * Renders a Code128 barcode bitmap for the ON-SCREEN preview only.
 * The actual print job never uses this - it sends raw TSPL and the TSC
 * printer generates its own barcode from the BARCODE command, same as
 * the desktop app.
 */
object BarcodePreview {

    fun render(data: String, widthPx: Int, heightPx: Int): Bitmap? {
        if (data.isBlank()) return null
        return try {
            val writer = Code128Writer()
            val hints = mapOf(EncodeHintType.MARGIN to 0)
            val matrix = writer.encode(data, BarcodeFormat.CODE_128, widthPx, heightPx, hints)
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.RGB_565)
            for (x in 0 until widthPx) {
                for (y in 0 until heightPx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) AColor.BLACK else AColor.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
