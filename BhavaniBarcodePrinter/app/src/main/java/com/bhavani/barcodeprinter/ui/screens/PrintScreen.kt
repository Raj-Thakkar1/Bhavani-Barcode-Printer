package com.bhavani.barcodeprinter.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bhavani.barcodeprinter.AppContainer
import com.bhavani.barcodeprinter.data.CustomFieldDef
import com.bhavani.barcodeprinter.data.FieldDataType
import com.bhavani.barcodeprinter.data.Item
import com.bhavani.barcodeprinter.data.PriceHistoryEntry
import com.bhavani.barcodeprinter.data.PrintHistoryEntry
import com.bhavani.barcodeprinter.printing.TsplBuilder
import com.bhavani.barcodeprinter.ui.components.StepperControl
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun PrintScreen(nav: NavHostController, itemId: Long?) {
    val db = AppContainer.db
    val settings = AppContainer.settings
    val usbPrinter = AppContainer.usbPrinter
    val scope = rememberCoroutineScope()

    var currentItemId by remember(itemId) { mutableStateOf(itemId) }
    var itemName by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }
    var sp by remember { mutableStateOf("") }
    var mfg by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var copies by remember { mutableStateOf(1) }
    var barcodeNum by remember { mutableStateOf(TsplBuilder.randomBarcode()) }
    var customValues by remember { mutableStateOf(mapOf<String, String>()) }
    var saveChangesToDb by remember { mutableStateOf(true) }

    var similarItems by remember { mutableStateOf(listOf<Item>()) }

    var statusMsg by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color_Muted) }
    var busy by remember { mutableStateOf(false) }

    val customFields by db.customFieldDao().all().collectAsState(initial = emptyList())
    val savedDeviceNameSnapshot by settings.savedPrinterDeviceName.collectAsState(initial = null)

    LaunchedEffect(Unit) { settings.defaultCopies.collect { copies = it } }

    // Load existing item when navigated with an id (from Database screen)
    LaunchedEffect(itemId) {
        if (itemId != null) {
            val item = db.itemDao().getById(itemId)
            if (item != null) {
                itemName = item.name; mrp = item.mrp; sp = item.sp; mfg = item.mfg
                exp = item.exp; qty = item.qty; barcodeNum = item.barcodeNum
                val map = try { JSONObject(item.customFieldsJson) } catch (_: Exception) { JSONObject() }
                customValues = customFields.associate { it.key to map.optString(it.key, "") }
            }
        }
    }

    LaunchedEffect(itemName, itemId) {
        similarItems = if (itemId == null && itemName.length >= 3) {
            db.itemDao().findSimilarByName(itemName)
        } else emptyList()
    }

    fun loadItemIntoForm(item: Item) {
        currentItemId = item.id
        itemName = item.name; mrp = item.mrp; sp = item.sp; mfg = item.mfg
        exp = item.exp; qty = item.qty; barcodeNum = item.barcodeNum
        val map = try { JSONObject(item.customFieldsJson) } catch (_: Exception) { JSONObject() }
        customValues = customFields.associate { it.key to map.optString(it.key, "") }
        similarItems = emptyList()
        statusMsg = "Loaded existing item — barcode kept unchanged."
        statusColor = SuccessColor
    }

    fun doReset() {
        currentItemId = null
        itemName = ""; mrp = ""; sp = ""; mfg = ""; exp = ""; qty = ""
        barcodeNum = TsplBuilder.randomBarcode()
        customValues = emptyMap()
        statusMsg = "Form reset."
        statusColor = Color_Muted
    }

    /** Upserts the current form into the database. Returns (itemId, resolvedItem). */
    suspend fun persistItem(): Pair<Long, Item> {
        val customJson = JSONObject()
        customValues.forEach { (k, v) -> customJson.put(k, v) }

        var id = currentItemId
        val existingByName = if (id == null) db.itemDao().getByExactName(itemName) else null
        if (id == null && existingByName != null) id = existingByName.id
        val priorItem = id?.let { db.itemDao().getById(it) }

        val finalItem = Item(
            id = id ?: 0,
            name = itemName, barcodeNum = priorItem?.barcodeNum ?: barcodeNum,
            mrp = mrp, sp = sp, qty = qty, mfg = mfg, exp = exp,
            customFieldsJson = customJson.toString(),
            createdAt = priorItem?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val newId = if (priorItem == null) {
            db.itemDao().insert(finalItem)
        } else {
            db.itemDao().update(finalItem.copy(id = priorItem.id))
            if (priorItem.mrp != mrp || priorItem.sp != sp) {
                db.priceHistoryDao().insert(PriceHistoryEntry(itemId = priorItem.id, mrp = mrp, sp = sp))
            }
            priorItem.id
        }
        barcodeNum = finalItem.barcodeNum
        currentItemId = newId
        return newId to finalItem.copy(id = newId)
    }

    fun doSaveOnly() {
        if (itemName.isBlank()) { statusMsg = "Item name is required."; statusColor = ErrorColor; return }
        busy = true
        scope.launch {
            try {
                persistItem()
                statusMsg = "Saved to database (no label printed)."; statusColor = SuccessColor
            } catch (e: Exception) {
                statusMsg = "Save failed: ${e.message}"; statusColor = ErrorColor
            }
            busy = false
        }
    }

    fun doPrint() {
        if (itemName.isBlank()) {
            statusMsg = "Item name is required."; statusColor = ErrorColor; return
        }
        val devices = usbPrinter.findCandidateDevices()
        val savedName = savedDeviceNameSnapshot
        val device = devices.firstOrNull { it.deviceName == savedName } ?: devices.firstOrNull()
        if (device == null) {
            statusMsg = "No USB printer found. Plug in the TSC printer, or check Settings."
            statusColor = ErrorColor
            return
        }

        busy = true
        scope.launch {
            try {
                val (id, finalItem) = if (saveChangesToDb) persistItem() else {
                    val priorItem = currentItemId?.let { db.itemDao().getById(it) }
                    (currentItemId ?: 0L) to Item(
                        id = currentItemId ?: 0, name = itemName, barcodeNum = priorItem?.barcodeNum ?: barcodeNum,
                        mrp = mrp, sp = sp, qty = qty, mfg = mfg, exp = exp,
                        customFieldsJson = JSONObject(customValues).toString()
                    )
                }

                val rawTemplate = settings.currentPrnTemplate()
                val filled = TsplBuilder.fillTemplate(rawTemplate, finalItem, copies) { path ->
                    try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                }
                val (header, body) = TsplBuilder.splitHeaderAndBody(filled)

                suspend fun send() {
                    val (ok, msg) = usbPrinter.printJob(device, header, body)
                    statusMsg = msg; statusColor = if (ok) SuccessColor else ErrorColor
                    busy = false
                    db.printHistoryDao().insert(
                        PrintHistoryEntry(itemId = if (id == 0L) null else id, itemName = itemName, barcodeNum = finalItem.barcodeNum, copies = copies, success = ok, message = msg)
                    )
                }

                if (!usbPrinter.hasPermission(device)) {
                    statusMsg = "Requesting USB permission…"; statusColor = WarnColor
                    usbPrinter.requestPermission(device) { granted ->
                        if (granted) scope.launch { send() }
                        else { statusMsg = "USB permission denied."; statusColor = ErrorColor; busy = false }
                    }
                } else {
                    send()
                }
            } catch (e: Exception) {
                statusMsg = "Error: ${e.message}"; statusColor = ErrorColor; busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BHAVANI PROVISION STORES", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (currentItemId != null) "Editing existing item — barcode locked" else "New Label  ·  TSC Thermal Printer",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
            )
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LabeledField("Item Name", itemName) { itemName = it }

            if (similarItems.isNotEmpty()) {
                Text("Similar items already exist — tap to reuse their barcode:", color = Gold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    similarItems.take(3).forEach { s ->
                        AssistChip(onClick = { loadItemIntoForm(s) }, label = { Text(s.name) })
                    }
                }
            }

            LabeledField("MRP (₹)", mrp, KeyboardType.Number) { mrp = it }
            LabeledField("SP / Selling (₹)", sp, KeyboardType.Number) { sp = it }
            LabeledField("MFG Date", mfg, placeholder = "e.g. 01/2025") { mfg = it }
            LabeledField("EXP Date", exp, placeholder = "e.g. 12/2026") { exp = it }
            LabeledField("Quantity / Weight", qty, placeholder = "e.g. 1, 500g, 1KG1") { qty = it }

            customFields.forEach { field ->
                LabeledField(
                    field.label, customValues[field.key] ?: "",
                    keyboardType = if (field.dataType == FieldDataType.NUMBER) KeyboardType.Number else KeyboardType.Text
                ) { v -> customValues = customValues + (field.key to v) }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Save changes to database", color = Fg, fontSize = 13.sp)
                Switch(checked = saveChangesToDb, onCheckedChange = { saveChangesToDb = it })
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().background(EntryBg.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🏷  No. of Labels to Print", color = Gold, fontWeight = FontWeight.Bold)
                StepperControl(value = copies, onChange = { copies = it }, min = 1)
            }

            Spacer(Modifier.height(8.dp))
            Text("Barcode Number: $barcodeNum" + if (currentItemId != null) " (locked to this item)" else "", color = Gold, fontSize = 13.sp)

            Spacer(Modifier.height(16.dp))
            if (statusMsg.isNotBlank()) Text(statusMsg, color = statusColor, fontSize = 13.sp)
        }

        Column(Modifier.fillMaxWidth().background(EntryBg).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { doPrint() }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Working…" else "🖨  Print Labels") }

                Button(
                    onClick = { doReset() }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = EntryBg),
                    modifier = Modifier.weight(1f)
                ) { Text("↺  Reset") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { doSaveOnly() }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("💾  Save to Database Only (no print)") }
        }
    }
}

@Composable
fun LabeledField(
    label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "", onChange: (String) -> Unit
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label, color = Fg, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, color = Color_Muted) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors()
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = EntryBg, unfocusedContainerColor = EntryBg,
    focusedTextColor = Fg, unfocusedTextColor = Fg,
    focusedBorderColor = Accent, unfocusedBorderColor = EntryBg,
)

val Color_Muted = Color(0xFFAAAAAA)
