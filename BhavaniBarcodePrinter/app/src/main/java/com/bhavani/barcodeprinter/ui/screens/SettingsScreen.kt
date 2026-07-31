package com.bhavani.barcodeprinter.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.bhavani.barcodeprinter.AppContainer
import com.bhavani.barcodeprinter.data.CustomFieldDef
import com.bhavani.barcodeprinter.data.FieldDataType
import com.bhavani.barcodeprinter.data.Item
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val db = AppContainer.db
    val settings = AppContainer.settings
    val usbPrinter = AppContainer.usbPrinter
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf(usbPrinter.findCandidateDevices()) }
    var statusMsg by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color_Muted) }
    val savedDeviceName by settings.savedPrinterDeviceName.collectAsState(initial = null)
    var defaultCopies by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) { settings.defaultCopies.collect { defaultCopies = it } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
                val root = JSONObject(text)
                val itemsArr = root.optJSONArray("items") ?: JSONArray()
                var imported = 0
                for (i in 0 until itemsArr.length()) {
                    val o = itemsArr.getJSONObject(i)
                    val name = o.optString("name")
                    if (name.isBlank()) continue
                    val existing = db.itemDao().getByExactName(name)
                    val item = Item(
                        id = existing?.id ?: 0,
                        name = name,
                        barcodeNum = existing?.barcodeNum ?: o.optString("barcodeNum"),
                        mrp = o.optString("mrp"), sp = o.optString("sp"), qty = o.optString("qty"),
                        mfg = o.optString("mfg"), exp = o.optString("exp"),
                        customFieldsJson = o.optString("customFieldsJson", "{}"),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    if (existing == null) db.itemDao().insert(item) else db.itemDao().update(item)
                    imported++
                }
                statusMsg = "Imported $imported item(s)."; statusColor = SuccessColor
            } catch (e: Exception) {
                statusMsg = "Import failed: ${e.message}"; statusColor = ErrorColor
            }
        }
    }

    fun refreshDevices() { devices = usbPrinter.findCandidateDevices() }

    fun exportBackup() {
        scope.launch {
            try {
                val items = db.itemDao().allActiveOnce()
                val arr = JSONArray()
                items.forEach { item ->
                    val o = JSONObject()
                    o.put("name", item.name); o.put("barcodeNum", item.barcodeNum)
                    o.put("mrp", item.mrp); o.put("sp", item.sp); o.put("qty", item.qty)
                    o.put("mfg", item.mfg); o.put("exp", item.exp)
                    o.put("customFieldsJson", item.customFieldsJson)
                    arr.put(o)
                }
                val root = JSONObject()
                root.put("items", arr)
                root.put("exportedAt", System.currentTimeMillis())

                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "bhavani_backup.json")
                file.writeText(root.toString())

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share database backup"))
                statusMsg = "Exported ${items.size} item(s)."; statusColor = SuccessColor
            } catch (e: Exception) {
                statusMsg = "Export failed: ${e.message}"; statusColor = ErrorColor
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            SectionTitle("Printer")
            devices.forEach { d ->
                val selected = d.deviceName == savedDeviceName
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(if (selected) Accent.copy(alpha = 0.3f) else EntryBg, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.deviceName, color = Fg, fontSize = 13.sp)
                    TextButton(onClick = {
                        scope.launch {
                            settings.savePrinterSelection(d.vendorId, d.productId, d.deviceName)
                            usbPrinter.forgetCalibration(d) // new/changed selection -> recalibrate on next print
                            statusMsg = "Selected ${d.deviceName}."; statusColor = SuccessColor
                        }
                    }) { Text(if (selected) "Selected" else "Select") }
                }
            }
            if (devices.isEmpty()) {
                Text("No USB printer detected. Plug in the TSC printer via OTG, then refresh.", color = Color_Muted, fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { refreshDevices() }) { Text("Refresh") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val d = devices.firstOrNull { it.deviceName == savedDeviceName } ?: devices.firstOrNull()
                    if (d == null) { statusMsg = "No printer to calibrate."; statusColor = ErrorColor; return@OutlinedButton }
                    scope.launch {
                        val full = settings.currentPrnTemplate()
                        val (header, _) = com.bhavani.barcodeprinter.printing.TsplBuilder.splitHeaderAndBody(full)
                        if (!usbPrinter.hasPermission(d)) {
                            usbPrinter.requestPermission(d) { granted ->
                                if (granted) scope.launch {
                                    val (ok, msg) = usbPrinter.calibrate(d, header)
                                    statusMsg = msg; statusColor = if (ok) SuccessColor else ErrorColor
                                }
                            }
                        } else {
                            val (ok, msg) = usbPrinter.calibrate(d, header)
                            statusMsg = msg; statusColor = if (ok) SuccessColor else ErrorColor
                        }
                    }
                }) { Text("Calibrate Now") }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Default label count")
            Row(
                Modifier.fillMaxWidth().background(EntryBg, RoundedCornerShape(8.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Copies suggested on new labels", color = Fg, fontSize = 13.sp)
                com.bhavani.barcodeprinter.ui.components.StepperControl(
                    value = defaultCopies, min = 1,
                    onChange = { defaultCopies = it; scope.launch { settings.saveDefaultCopies(it) } }
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Label Design")
            SettingsRow("Edit Label Design", "Move/resize text, barcode & images freely") { nav.navigate("labelDesigner") }
            SettingsRow("Raw PRN Template", "Edit the exact TSPL text directly") { nav.navigate("rawPrnEditor") }
            SettingsRow("PRN History", "Every saved version — nothing auto-deleted") { nav.navigate("prnHistory") }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Database")
            SettingsRow("Custom Fields", "Add or remove fields on items and labels") { nav.navigate("schemaEditor") }
            SettingsRow("Print History", "See everything printed recently") { nav.navigate("printHistory") }
            SettingsRow("Recycle Bin", "Restore or permanently delete items") { nav.navigate("recycleBin") }

            Spacer(Modifier.height(20.dp))
            SectionTitle("Backup")
            Row(Modifier.padding(vertical = 4.dp)) {
                Button(onClick = { exportBackup() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Export") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { importLauncher.launch("application/json") }) { Text("Import") }
            }

            Spacer(Modifier.height(20.dp))
            if (statusMsg.isNotBlank()) Text(statusMsg, color = statusColor, fontSize = 13.sp)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(EntryBg, RoundedCornerShape(8.dp))
            .clickableSettings(onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color_Muted, fontSize = 11.sp)
        }
        Text("›", color = Color_Muted, fontSize = 18.sp)
    }
}

private fun Modifier.clickableSettings(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
