package com.bhavani.barcodeprinter.printing

import android.graphics.Bitmap
import com.bhavani.barcodeprinter.data.Item
import org.json.JSONObject

/**
 * Turns the stored PRN template text (with {{tokens}}) into the exact bytes to send to the
 * printer for one job, and splits header from body so UsbPrinter can send the SIZE/GAP
 * calibration header only once per session (the fix for the "1 prints, 1 blank, then
 * continues" bug) even though the template itself is now fully user-editable.
 */
object TsplBuilder {

    fun randomBarcode(): String = (1..12).map { ('0'..'9').random() }.joinToString("")

    private fun resolveValue(fieldKey: String, item: Item): String {
        if (fieldKey.startsWith("custom.")) {
            val key = fieldKey.removePrefix("custom.")
            val map = try { JSONObject(item.customFieldsJson) } catch (_: Exception) { JSONObject() }
            return map.optString(key, "")
        }
        return when (fieldKey) {
            "item.name" -> item.name
            "item.mrp" -> item.mrp
            "item.sp" -> item.sp
            "item.qty" -> item.qty
            "item.mfg" -> item.mfg
            "item.exp" -> item.exp
            "item.barcodeNum" -> item.barcodeNum
            else -> ""
        }
    }

    private val tokenRegex = Regex("""\{\{([a-zA-Z0-9_.]+)\}\}""")
    private val imageLineRegex = Regex("""^IMAGE (-?\d+),(-?\d+),(\d+),(\d+),"(.*)"$""")

    /** True binary-safe append: raw bytes are mapped 1:1 to chars 0-255, matching sendRaw's ISO_8859_1 encode. */
    private fun StringBuilder.appendRawBytes(bytes: ByteArray) {
        for (b in bytes) append((b.toInt() and 0xFF).toChar())
    }

    /**
     * Fills in {{item.xxx}}/{{custom.xxx}}/{{copies}} tokens and expands IMAGE marker lines
     * into real TSPL BITMAP commands with embedded binary. Returns the full, ready-to-send text.
     */
    fun fillTemplate(
        template: String,
        item: Item,
        copies: Int,
        imageProvider: (assetPath: String) -> Bitmap?
    ): String {
        val sb = StringBuilder()

        for (rawLine in template.lines()) {
            val imgMatch = imageLineRegex.find(rawLine.trim())
            if (imgMatch != null) {
                val (xs, ys, ws, hs, path) = imgMatch.destructured
                val bmp = if (path.isNotBlank()) imageProvider(path) else null
                if (bmp != null) {
                    val encoded = ImageEncoder.encode(bmp, ws.toInt(), hs.toInt())
                    sb.append("BITMAP $xs,$ys,${encoded.widthBytes},${encoded.heightDots},0,")
                    sb.appendRawBytes(encoded.data)
                    sb.append("\n")
                }
                continue
            }

            val filled = tokenRegex.replace(rawLine) { m ->
                val key = m.groupValues[1]
                if (key == "copies") copies.toString() else resolveValue(key, item)
            }
            sb.append(filled).append("\n")
        }
        return sb.toString()
    }

    /**
     * Splits a (possibly already token-filled) PRN text into the calibration header
     * (everything before CLS) and the job body (CLS through PRINT). If no CLS line is
     * found, the whole thing is treated as body with an empty header.
     */
    fun splitHeaderAndBody(fullPrn: String): Pair<String, String> {
        val lines = fullPrn.lines()
        val idx = lines.indexOfFirst { it.trim() == "CLS" }
        return if (idx == -1) {
            "" to fullPrn
        } else {
            lines.subList(0, idx).joinToString("\n") to lines.subList(idx, lines.size).joinToString("\n")
        }
    }
}
