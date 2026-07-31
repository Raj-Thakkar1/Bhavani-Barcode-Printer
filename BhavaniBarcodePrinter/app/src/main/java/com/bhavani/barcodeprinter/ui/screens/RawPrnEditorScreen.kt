package com.bhavani.barcodeprinter.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.bhavani.barcodeprinter.AppContainer
import com.bhavani.barcodeprinter.data.PrnHistoryEntry
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RawPrnEditorScreen(nav: NavHostController) {
    val context = LocalContext.current
    val db = AppContainer.db
    val settings = AppContainer.settings
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color_Muted) }

    LaunchedEffect(Unit) {
        text = settings.currentPrnTemplate()
        loaded = true
    }

    fun save() {
        scope.launch {
            settings.savePrnTemplate(text)
            db.prnHistoryDao().insert(PrnHistoryEntry(content = text, source = "raw_editor"))
            statusMsg = "Saved as the current template."; statusColor = SuccessColor
        }
    }

    fun export() {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "bhavani_label_template.prn")
            file.writeText(text)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PRN template"))
        } catch (e: Exception) {
            statusMsg = "Export failed: ${e.message}"; statusColor = ErrorColor
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
                Text("Raw PRN Template", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { save() }) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        Text(
            "This is the exact TSPL sent to the printer. Use {{item.name}}, {{item.mrp}}, {{item.sp}}, " +
                "{{item.qty}}, {{item.mfg}}, {{item.exp}}, {{item.barcodeNum}}, {{custom.<key>}} and {{copies}} " +
                "as placeholders — they're filled in right before printing. The visual Label Designer edits this " +
                "same text; changes made there or here both become the current template.",
            color = Color_Muted, fontSize = 11.sp, modifier = Modifier.padding(16.dp)
        )

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Fg),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                colors = fieldColors()
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { export() }, modifier = Modifier.weight(1f)) { Text("Export .prn") }
            OutlinedButton(onClick = { nav.navigate("prnHistory") }, modifier = Modifier.weight(1f)) { Text("History") }
        }
        if (statusMsg.isNotBlank()) Text(statusMsg, color = statusColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Spacer(Modifier.height(8.dp))
    }
}
