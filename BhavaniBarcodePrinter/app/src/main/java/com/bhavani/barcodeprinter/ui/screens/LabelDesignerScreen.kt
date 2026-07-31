package com.bhavani.barcodeprinter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bhavani.barcodeprinter.AppContainer
import com.bhavani.barcodeprinter.data.PrnHistoryEntry
import com.bhavani.barcodeprinter.printing.ElementType
import com.bhavani.barcodeprinter.printing.LABEL_HEIGHT_DOTS
import com.bhavani.barcodeprinter.printing.LABEL_WIDTH_DOTS
import com.bhavani.barcodeprinter.printing.LabelElement
import com.bhavani.barcodeprinter.printing.PrnCodec
import com.bhavani.barcodeprinter.ui.components.StepperControl
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun LabelDesignerScreen(nav: NavHostController) {
    val context = LocalContext.current
    val settings = AppContainer.settings
    val db = AppContainer.db
    val scope = rememberCoroutineScope()

    var elements by remember { mutableStateOf(listOf<LabelElement>()) }
    var widthMm by remember { mutableStateOf(50) }
    var heightMm by remember { mutableStateOf(25) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val customFields by db.customFieldDao().all().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val customKeys = db.customFieldDao().allOnce().map { it.key }
        val parsed = PrnCodec.parse(settings.currentPrnTemplate(), customKeys)
        elements = parsed.elements
        widthMm = parsed.widthMm; heightMm = parsed.heightMm
        loaded = true
    }

    val rawCount = elements.count { it.type == ElementType.RAW }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = File(context.filesDir, "label_images").apply { mkdirs() }
        val outFile = File(dir, "${UUID.randomUUID()}.png")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            val newEl = LabelElement(
                type = ElementType.IMAGE, x = 40, y = 90,
                imageAssetPath = outFile.absolutePath, imageWidthDots = 80, imageHeightDots = 80
            )
            elements = elements + newEl
            selectedId = newEl.id
        } catch (_: Exception) { }
    }

    fun updateSelected(transform: (LabelElement) -> LabelElement) {
        elements = elements.map { if (it.id == selectedId) transform(it) else it }
    }

    fun bindingOptions(): List<Pair<String, String>> {
        val base = listOf(
            "static" to "Static text", "item.name" to "Item Name", "item.mrp" to "MRP",
            "item.sp" to "SP", "item.qty" to "Qty", "item.mfg" to "MFG Date",
            "item.exp" to "EXP Date", "item.barcodeNum" to "Barcode Number"
        )
        return base + customFields.map { "custom.${it.key}" to it.label }
    }

    fun saveTemplate() {
        scope.launch {
            val newPrn = PrnCodec.render(widthMm, heightMm, elements)
            settings.savePrnTemplate(newPrn)
            db.prnHistoryDao().insert(PrnHistoryEntry(content = newPrn, source = "designer"))
            nav.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Label Design", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { saveTemplate() }) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Tap an element below to select it, then use the fields at the bottom for precise " +
                    "position, size, font and binding. Dragging on the preview works too, but numbers are exact.",
                color = Color_Muted, fontSize = 11.sp, modifier = Modifier.padding(16.dp)
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val newEl = LabelElement(type = ElementType.TEXT, fieldKey = "static", staticText = "Text", x = 40, y = 100)
                    elements = elements + newEl; selectedId = newEl.id
                }) { Text("+ Text") }
                OutlinedButton(onClick = {
                    val newEl = LabelElement(type = ElementType.BARCODE, fieldKey = "item.barcodeNum", x = 40, y = 100)
                    elements = elements + newEl; selectedId = newEl.id
                }) { Text("+ Barcode") }
                OutlinedButton(onClick = { imagePicker.launch("image/*") }) { Text("+ Image") }
            }

            Spacer(Modifier.height(12.dp))

            // --- Visual preview (scales to actual screen width, clipped so nothing spills outside the label) ---
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val scale = (maxWidth.value / LABEL_WIDTH_DOTS).coerceAtMost(1.6f)
                Box(
                    Modifier
                        .size((LABEL_WIDTH_DOTS * scale).dp, (LABEL_HEIGHT_DOTS * scale).dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, Gold, RoundedCornerShape(4.dp))
                        .clipToBounds()
                ) {
                    elements.filter { it.type != ElementType.RAW }.forEach { e ->
                        val boxWidthDots = if (e.type == ElementType.IMAGE) e.imageWidthDots else 130
                        val boxHeightDots = if (e.type == ElementType.IMAGE) e.imageHeightDots else 20
                        // Rotation 180 prints "backwards" from the anchor point, so the visual box
                        // is shown ending at (x,y) instead of starting there — much closer to reality.
                        val anchorLeftDots = if (e.rotation == 180) e.x - boxWidthDots else e.x
                        val anchorTopDots = if (e.rotation == 180) (LABEL_HEIGHT_DOTS - e.y) else (LABEL_HEIGHT_DOTS - e.y - boxHeightDots)

                        Box(
                            Modifier
                                .offset(x = (anchorLeftDots * scale).dp, y = (anchorTopDots * scale).dp)
                                .size((boxWidthDots * scale).dp, (boxHeightDots * scale).dp)
                                .border(
                                    if (selectedId == e.id) 2.dp else 1.dp,
                                    if (selectedId == e.id) Gold else Accent,
                                    RoundedCornerShape(2.dp)
                                )
                                .background(if (selectedId == e.id) Accent.copy(alpha = 0.18f) else Color(0x11000000))
                                .clickable { selectedId = e.id }
                                .pointerInput(e.id, scale) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val dxDots = (drag.x / scale).roundToInt()
                                        val dyDots = (drag.y / scale).roundToInt()
                                        elements = elements.map {
                                            if (it.id == e.id) it.copy(
                                                x = (it.x + dxDots).coerceIn(0, LABEL_WIDTH_DOTS),
                                                y = (it.y - dyDots).coerceIn(0, LABEL_HEIGHT_DOTS)
                                            ) else it
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (e.type) {
                                    ElementType.TEXT -> if (e.fieldKey == "static") e.staticText.ifBlank { "Text" } else e.fieldKey.substringAfterLast(".")
                                    ElementType.BARCODE -> "|||| barcode"
                                    ElementType.IMAGE -> "image"
                                    ElementType.RAW -> ""
                                },
                                color = Color.Black, fontSize = 9.sp, maxLines = 1
                            )
                        }
                    }
                }
            }

            if (rawCount > 0) {
                Text(
                    "$rawCount hand-written command(s) in this template aren't shown here — edit them in Raw PRN Template (Settings).",
                    color = WarnColor, fontSize = 11.sp, modifier = Modifier.padding(16.dp)
                )
            }

            // --- Element list: precise, reliable alternative to dragging ---
            Column(Modifier.padding(16.dp)) {
                Text("Elements", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                elements.filter { it.type != ElementType.RAW }.forEach { e ->
                    val label = when (e.type) {
                        ElementType.TEXT -> "Text: " + (if (e.fieldKey == "static") e.staticText.ifBlank { "(empty)" } else e.fieldKey)
                        ElementType.BARCODE -> "Barcode: ${e.fieldKey}"
                        ElementType.IMAGE -> "Image"
                        ElementType.RAW -> ""
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(if (selectedId == e.id) Accent.copy(alpha = 0.25f) else EntryBg, RoundedCornerShape(6.dp))
                            .clickable { selectedId = e.id }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = Fg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("(${e.x},${e.y})", color = Color_Muted, fontSize = 11.sp)
                    }
                }
            }

            val selected = elements.firstOrNull { it.id == selectedId }
            if (selected != null) {
                Column(Modifier.fillMaxWidth().background(EntryBg).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Editing ${selected.type}", color = Gold, fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            elements = elements.filterNot { it.id == selected.id }
                            selectedId = null
                        }) { Text("Delete", color = ErrorColor) }
                    }

                    Text("Position (dots, 0-$LABEL_WIDTH_DOTS x 0-$LABEL_HEIGHT_DOTS)", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("X", selected.x, Modifier.weight(1f)) { v -> updateSelected { it.copy(x = v.coerceIn(0, LABEL_WIDTH_DOTS)) } }
                        NumberField("Y", selected.y, Modifier.weight(1f)) { v -> updateSelected { it.copy(y = v.coerceIn(0, LABEL_HEIGHT_DOTS)) } }
                    }

                    if (selected.type != ElementType.IMAGE) {
                        Text("Bound to:", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        var expanded by remember(selected.id) { mutableStateOf(false) }
                        val options = bindingOptions()
                        val currentLabel = options.firstOrNull { it.first == selected.fieldKey }?.second ?: selected.fieldKey
                        Box {
                            OutlinedButton(onClick = { expanded = true }) { Text(currentLabel) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEach { (key, lbl) ->
                                    DropdownMenuItem(text = { Text(lbl) }, onClick = {
                                        updateSelected { it.copy(fieldKey = key) }
                                        expanded = false
                                    })
                                }
                            }
                        }
                        if (selected.fieldKey == "static") {
                            OutlinedTextField(
                                value = selected.staticText, onValueChange = { v -> updateSelected { it.copy(staticText = v) } },
                                label = { Text("Static text") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = selected.prefix, onValueChange = { v -> updateSelected { it.copy(prefix = v) } },
                                label = { Text("Prefix") }, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = selected.suffix, onValueChange = { v -> updateSelected { it.copy(suffix = v) } },
                                label = { Text("Suffix") }, modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selected.type == ElementType.TEXT) {
                        Text("Font", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        Row {
                            listOf("0", "1", "2", "3", "4", "5", "ROMAN.TTF").forEach { f ->
                                FilterChip(
                                    selected = selected.font == f, onClick = { updateSelected { it.copy(font = f) } },
                                    label = { Text(f) }, modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                        Text("Size (bigger = bigger text)", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column { Text("Width mult.", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.size1, min = 1, max = 20, onChange = { v -> updateSelected { it.copy(size1 = v) } }) }
                            Column { Text("Height mult.", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.size2, min = 1, max = 20, onChange = { v -> updateSelected { it.copy(size2 = v) } }) }
                        }
                    }

                    if (selected.type == ElementType.BARCODE) {
                        Text("Barcode size", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column { Text("Height", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.barcodeHeight, step = 5, min = 10, max = 150, onChange = { v -> updateSelected { it.copy(barcodeHeight = v) } }) }
                            Column { Text("Bar width", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.barcodeWide, min = 1, max = 10, onChange = { v -> updateSelected { it.copy(barcodeWide = v) } }) }
                        }
                    }

                    if (selected.type == ElementType.IMAGE) {
                        Text("Image size (dots)", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column { Text("Width", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.imageWidthDots, step = 8, min = 8, max = LABEL_WIDTH_DOTS, onChange = { v -> updateSelected { it.copy(imageWidthDots = v) } }) }
                            Column { Text("Height", color = Color_Muted, fontSize = 10.sp); StepperControl(value = selected.imageHeightDots, step = 8, min = 8, max = LABEL_HEIGHT_DOTS, onChange = { v -> updateSelected { it.copy(imageHeightDots = v) } }) }
                        }
                    }

                    Text("Rotation", color = Fg, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Row {
                        listOf(0, 90, 180, 270).forEach { r ->
                            FilterChip(
                                selected = selected.rotation == r, onClick = { updateSelected { it.copy(rotation = r) } },
                                label = { Text("$r°") }, modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { v -> v.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
