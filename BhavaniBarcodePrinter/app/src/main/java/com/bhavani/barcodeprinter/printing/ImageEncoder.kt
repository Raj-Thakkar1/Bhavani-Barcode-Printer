package com.bhavani.barcodeprinter.printing

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Encodes a bitmap into the raw payload for TSPL's BITMAP command:
 * BITMAP x,y,widthBytes,heightDots,mode,<rawBinaryData>
 *
 * Each row is packed 1 bit per pixel (black=1), MSB first, padded to a full byte.
 * The returned ByteArray is the raw binary that must be appended immediately after
 * the BITMAP command's trailing comma (see TsplBuilder.fillTemplate, which is binary-safe).
 */
object ImageEncoder {

    data class Encoded(val widthBytes: Int, val heightDots: Int, val data: ByteArray)

    fun encode(bitmap: Bitmap, targetWidthDots: Int, targetHeightDots: Int, threshold: Int = 160): Encoded {
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidthDots, targetHeightDots, true)
        val widthBytes = (targetWidthDots + 7) / 8
        val out = ByteArray(widthBytes * targetHeightDots)

        for (yy in 0 until targetHeightDots) {
            for (xx in 0 until targetWidthDots) {
                val pixel = scaled.getPixel(xx, yy)
                val a = Color.alpha(pixel)
                val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114)
                val isBlack = a > 32 && gray < threshold
                if (isBlack) {
                    val byteIndex = yy * widthBytes + (xx / 8)
                    val bitIndex = 7 - (xx % 8)
                    out[byteIndex] = (out[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        return Encoded(widthBytes, targetHeightDots, out)
    }
}
