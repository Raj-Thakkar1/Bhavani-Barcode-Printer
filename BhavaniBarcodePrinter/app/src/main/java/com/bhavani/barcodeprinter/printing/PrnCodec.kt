package com.bhavani.barcodeprinter.printing

import java.util.UUID

/** Printer resolution for the 50x25mm TSC label: 8 dots/mm -> 400x200 dot canvas. */
const val LABEL_WIDTH_DOTS = 400
const val LABEL_HEIGHT_DOTS = 200
const val DOTS_PER_MM = 8

enum class ElementType { TEXT, BARCODE, IMAGE, RAW }

/**
 * One element as understood by the visual Label Designer. This is a VIEW over the raw PRN
 * text, not a separate stored format — PrnCodec.parse() derives this from PRN text, and
 * PrnCodec.render() turns edited elements straight back into PRN text, which is then saved
 * as the new current template (and a new PrnHistory row).
 *
 * `fieldKey` selects what data fills a TEXT/BARCODE element at print time:
 *  - "static"                      -> uses `staticText` literally
 *  - "item.name" / "item.mrp" / "item.sp" / "item.qty" / "item.mfg" / "item.exp" / "item.barcodeNum"
 *  - "custom.<key>"                -> pulled from the item's custom-field JSON map
 *
 * type == RAW means this line didn't match a recognized TEXT/BARCODE/IMAGE pattern (e.g. a
 * TSPL command typed by hand in the raw editor). It is preserved verbatim and re-emitted in
 * place, but isn't drag-editable on the canvas — only removable, or editable via the raw editor.
 */
data class LabelElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ElementType,
    val fieldKey: String = "static",
    val staticText: String = "",
    val prefix: String = "",
    val suffix: String = "",
    var x: Int = 0,
    var y: Int = 0,
    val font: String = "0",       // "0".."5" builtin bitmap fonts, or "ROMAN.TTF" scalable font
    val rotation: Int = 180,      // 0/90/180/270 — label is mounted upside-down on this printer
    val size1: Int = 8,
    val size2: Int = 7,
    val barcodeHeight: Int = 53,
    val barcodeNarrow: Int = 2,
    val barcodeWide: Int = 4,
    val barcodeContentPrefix: String = "",
    val imageAssetPath: String? = null,
    val imageWidthDots: Int = 96,
    val imageHeightDots: Int = 96,
    val rawLine: String = ""
)

data class ParsedTemplate(
    val widthMm: Int,
    val heightMm: Int,
    val elements: List<LabelElement>
)

object PrnCodec {

    private val textRegex = Regex("""^TEXT (-?\d+),(-?\d+),"([^"]*)",(\d+),(\d+),(\d+),"(.*)"$""")
    private val barcodeRegex = Regex("""^BARCODE (-?\d+),(-?\d+),"128M",(\d+),([01]),(\d+),(\d+),(\d+),"(.*)"$""")
    private val imageRegex = Regex("""^IMAGE (-?\d+),(-?\d+),(\d+),(\d+),"(.*)"$""")
    private val sizeRegex = Regex("""^SIZE (\d+)\s*mm,\s*(\d+)\s*mm$""")

    private val knownFieldKeys = listOf(
        "item.name", "item.mrp", "item.sp", "item.qty", "item.mfg", "item.exp", "item.barcodeNum"
    )

    /** Splits `Xxx:{{item.mrp}}Yyy` into ("Xxx:", "item.mrp", "Yyy"), or null if no known token found. */
    private fun extractToken(content: String, customKeys: List<String>): Triple<String, String, String>? {
        val allKeys = knownFieldKeys + customKeys.map { "custom.$it" }
        for (key in allKeys) {
            val token = "{{$key}}"
            val idx = content.indexOf(token)
            if (idx >= 0) {
                return Triple(content.substring(0, idx), key, content.substring(idx + token.length))
            }
        }
        return null
    }

    fun parse(prn: String, customKeys: List<String> = emptyList()): ParsedTemplate {
        val lines = prn.lines()
        val sizeLine = lines.firstOrNull { sizeRegex.matches(it.trim()) }
        val sizeMatch = sizeLine?.let { sizeRegex.find(it.trim()) }
        val widthMm = sizeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 50
        val heightMm = sizeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 25

        val clsIdx = lines.indexOfFirst { it.trim() == "CLS" }
        val bodyLines = if (clsIdx >= 0) lines.subList(clsIdx + 1, lines.size) else lines

        val elements = mutableListOf<LabelElement>()
        for (raw in bodyLines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("PRINT ")) continue // footer, reconstructed by render()

            val textMatch = textRegex.find(line)
            val barcodeMatch = barcodeRegex.find(line)
            val imageMatch = imageRegex.find(line)

            when {
                textMatch != null -> {
                    val (x, y, font, rot, s1, s2, content) = textMatch.destructured
                    val tok = extractToken(content, customKeys)
                    if (tok != null) {
                        elements += LabelElement(
                            type = ElementType.TEXT, fieldKey = tok.second, prefix = tok.first, suffix = tok.third,
                            x = x.toInt(), y = y.toInt(), font = font, rotation = rot.toInt(),
                            size1 = s1.toInt(), size2 = s2.toInt()
                        )
                    } else {
                        elements += LabelElement(
                            type = ElementType.TEXT, fieldKey = "static", staticText = content,
                            x = x.toInt(), y = y.toInt(), font = font, rotation = rot.toInt(),
                            size1 = s1.toInt(), size2 = s2.toInt()
                        )
                    }
                }
                barcodeMatch != null -> {
                    val g = barcodeMatch.groupValues
                    val content = g[8]
                    val tok = extractToken(content, customKeys)
                    val prefixLiteral = tok?.first ?: content.substringBefore("{{").ifEmpty { "" }
                    elements += LabelElement(
                        type = ElementType.BARCODE, fieldKey = tok?.second ?: "item.barcodeNum",
                        barcodeContentPrefix = prefixLiteral,
                        x = g[1].toInt(), y = g[2].toInt(), barcodeHeight = g[3].toInt(),
                        rotation = g[5].toInt(), barcodeNarrow = g[6].toInt(), barcodeWide = g[7].toInt()
                    )
                }
                imageMatch != null -> {
                    val g = imageMatch.groupValues
                    elements += LabelElement(
                        type = ElementType.IMAGE, x = g[1].toInt(), y = g[2].toInt(),
                        imageWidthDots = g[3].toInt(), imageHeightDots = g[4].toInt(), imageAssetPath = g[5]
                    )
                }
                else -> {
                    elements += LabelElement(type = ElementType.RAW, rawLine = raw)
                }
            }
        }

        return ParsedTemplate(widthMm, heightMm, elements)
    }

    fun headerFor(widthMm: Int, heightMm: Int): String = """
        SIZE $widthMm mm, $heightMm mm
        GAP 3 mm, 0 mm
        DIRECTION 0,0
        REFERENCE 0,0
        OFFSET 0 mm
        SET PEEL OFF
        SET CUTTER OFF
        SET PARTIAL_CUTTER OFF
        SET TEAR ON
        CODEPAGE 1252
    """.trimIndent()

    /** Rebuilds full PRN text (header + CLS + elements + PRINT) from the designer's element list. */
    fun render(widthMm: Int, heightMm: Int, elements: List<LabelElement>): String {
        val sb = StringBuilder()
        sb.append(headerFor(widthMm, heightMm)).append("\n")
        sb.append("CLS\n")
        for (e in elements) {
            when (e.type) {
                ElementType.TEXT -> {
                    val content = if (e.fieldKey == "static") e.staticText else "${e.prefix}{{${e.fieldKey}}}${e.suffix}"
                    sb.append("TEXT ${e.x},${e.y},\"${e.font}\",${e.rotation},${e.size1},${e.size2},\"$content\"\n")
                }
                ElementType.BARCODE -> {
                    val content = "${e.barcodeContentPrefix}{{${e.fieldKey}}}"
                    sb.append("BARCODE ${e.x},${e.y},\"128M\",${e.barcodeHeight},0,${e.rotation},${e.barcodeNarrow},${e.barcodeWide},\"$content\"\n")
                }
                ElementType.IMAGE -> {
                    sb.append("IMAGE ${e.x},${e.y},${e.imageWidthDots},${e.imageHeightDots},\"${e.imageAssetPath ?: ""}\"\n")
                }
                ElementType.RAW -> {
                    sb.append(e.rawLine.trim()).append("\n")
                }
            }
        }
        sb.append("PRINT 1,{{copies}}\n")
        return sb.toString()
    }

    /** The app's original hardcoded layout, expressed as an editable, human-readable PRN template. */
    val DEFAULT_PRN_TEMPLATE: String = """
        SIZE 50 mm, 25 mm
        GAP 3 mm, 0 mm
        DIRECTION 0,0
        REFERENCE 0,0
        OFFSET 0 mm
        SET PEEL OFF
        SET CUTTER OFF
        SET PARTIAL_CUTTER OFF
        SET TEAR ON
        CODEPAGE 1252
        CLS
        TEXT 390,187,"0",180,8,7,"BHAVANI PROVISION STORES"
        TEXT 390,162,"0",180,11,6,"{{item.name}}"
        BARCODE 390,138,"128M",53,0,180,2,4,"!104{{item.barcodeNum}}"
        TEXT 300,83,"ROMAN.TTF",180,1,5,"{{item.barcodeNum}}"
        TEXT 390,70,"0",180,7,4,"QTY:{{item.qty}}"
        TEXT 390,58,"0",180,8,7,"MRP:{{item.mrp}}"
        TEXT 215,58,"0",180,10,7,"SP:{{item.sp}}"
        TEXT 390,38,"0",180,7,6,"MFG:{{item.mfg}}"
        TEXT 215,37,"0",180,9,6,"EXP:{{item.exp}}"
        PRINT 1,{{copies}}
    """.trimIndent() + "\n"
}
