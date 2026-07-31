package com.bhavani.barcodeprinter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bhavani.barcodeprinter.AppContainer
import com.bhavani.barcodeprinter.data.CustomFieldDef
import com.bhavani.barcodeprinter.data.FieldDataType
import com.bhavani.barcodeprinter.printing.ElementType
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SchemaEditorScreen(nav: NavHostController) {
    val db = AppContainer.db
    val settings = AppContainer.settings
    val scope = rememberCoroutineScope()
    val fields by db.customFieldDao().all().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<CustomFieldDef?>(null) }

    fun slugFor(label: String, existing: List<CustomFieldDef>): String {
        val base = label.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "field" }
        var candidate = base
        var i = 1
        while (existing.any { it.key == candidate }) { candidate = "${base}_$i"; i++ }
        return candidate
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Custom Fields", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, "Add field", tint = Color.White)
            }
        }

        Text(
            "These extend both the item form and (optionally) the printed label. " +
                "\"Store in DB\" and \"Show on label\" work independently.",
            color = Color_Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp)
        )

        if (fields.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No custom fields yet. Tap + to add one (e.g. Batch No., Supplier).", color = Color_Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(fields, key = { it.id }) { field ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(EntryBg, RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(field.label, color = Fg, fontWeight = FontWeight.Bold)
                                Text("key: ${field.key}  ·  type: ${field.dataType}", color = Color_Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { confirmRemove = field }) {
                                Icon(Icons.Filled.Delete, "Remove", tint = ErrorColor)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Store in DB", color = Fg, fontSize = 12.sp)
                                Switch(checked = field.storeInDb, onCheckedChange = { checked ->
                                    scope.launch { db.customFieldDao().update(field.copy(storeInDb = checked)) }
                                })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Show on label", color = Fg, fontSize = 12.sp)
                                Switch(checked = field.showOnLabel, onCheckedChange = { checked ->
                                    scope.launch { db.customFieldDao().update(field.copy(showOnLabel = checked)) }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var label by remember { mutableStateOf("") }
        var dataType by remember { mutableStateOf(FieldDataType.TEXT) }
        var storeInDb by remember { mutableStateOf(true) }
        var showOnLabel by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New custom field") },
            text = {
                Column {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Field label") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Data type", fontSize = 12.sp)
                    Row {
                        FieldDataType.values().forEach { t ->
                            FilterChip(selected = dataType == t, onClick = { dataType = t }, label = { Text(t.name) }, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = storeInDb, onCheckedChange = { storeInDb = it })
                        Text("Store in database", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showOnLabel, onCheckedChange = { showOnLabel = it })
                        Text("Show on printed label (add to designer manually)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (label.isNotBlank()) {
                        scope.launch {
                            val key = slugFor(label, fields)
                            db.customFieldDao().insert(
                                CustomFieldDef(key = key, label = label, dataType = dataType, storeInDb = storeInDb, showOnLabel = showOnLabel, sortOrder = fields.size)
                            )
                        }
                    }
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    confirmRemove?.let { field ->
        var removeEverywhere by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove \"${field.label}\"?") },
            text = {
                Column {
                    Text("Choose how much to remove:", fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !removeEverywhere, onClick = { removeEverywhere = false })
                        Text("From label only (keep data saved in database)", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = removeEverywhere, onClick = { removeEverywhere = true })
                        Text("From label AND database (deletes the stored values too)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // Always strip this field from the saved PRN template (both TEXT/BARCODE lines
                        // bound to it are removed; other lines, including hand-written raw commands,
                        // are re-emitted unchanged).
                        val fullPrn = settings.currentPrnTemplate()
                        val parsed = com.bhavani.barcodeprinter.printing.PrnCodec.parse(fullPrn)
                        val strippedElements = parsed.elements.filterNot {
                            (it.type == ElementType.TEXT || it.type == ElementType.BARCODE) && it.fieldKey == "custom.${field.key}"
                        }
                        if (strippedElements.size != parsed.elements.size) {
                            val newPrn = com.bhavani.barcodeprinter.printing.PrnCodec.render(parsed.widthMm, parsed.heightMm, strippedElements)
                            settings.savePrnTemplate(newPrn)
                            db.prnHistoryDao().insert(
                                com.bhavani.barcodeprinter.data.PrnHistoryEntry(content = newPrn, source = "designer", note = "Removed field '${field.key}' from label")
                            )
                        }

                        if (removeEverywhere) {
                            // Strip the value from every item's custom-fields JSON, then delete the definition.
                            val allItems = db.itemDao().allActiveOnce()
                            allItems.forEach { item ->
                                val obj = try { JSONObject(item.customFieldsJson) } catch (_: Exception) { JSONObject() }
                                if (obj.has(field.key)) {
                                    obj.remove(field.key)
                                    db.itemDao().update(item.copy(customFieldsJson = obj.toString()))
                                }
                            }
                            db.customFieldDao().delete(field.id)
                        } else {
                            db.customFieldDao().update(field.copy(showOnLabel = false))
                        }
                    }
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null } ) { Text("Cancel") } }
        )
    }
}
