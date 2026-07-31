package com.bhavani.barcodeprinter.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import com.bhavani.barcodeprinter.data.PrnHistoryEntry
import com.bhavani.barcodeprinter.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrnHistoryScreen(nav: NavHostController) {
    val context = LocalContext.current
    val db = AppContainer.db
    val settings = AppContainer.settings
    val scope = rememberCoroutineScope()
    val history by db.prnHistoryDao().all().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var statusMsg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Text("PRN History", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Every saved template version, kept forever unless you delete it here.",
            color = Color_Muted, fontSize = 11.sp, modifier = Modifier.padding(16.dp)
        )
        if (statusMsg.isNotBlank()) Text(statusMsg, color = SuccessColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No saved versions yet.", color = Color_Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(history, key = { it.id }) { h ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(EntryBg, RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        Text(fmt.format(Date(h.savedAt)), color = Fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("source: ${h.source}", color = Color_Muted, fontSize = 11.sp)
                        Text(
                            h.content.lines().filter { it.isNotBlank() }.take(2).joinToString("  ·  "),
                            color = Color_Muted, fontSize = 10.sp, maxLines = 1
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    settings.savePrnTemplate(h.content)
                                    db.prnHistoryDao().insert(PrnHistoryEntry(content = h.content, source = "restored"))
                                    statusMsg = "Restored as current template."
                                }
                            }) { Text("Restore") }
                            TextButton(onClick = {
                                try {
                                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                    val file = File(dir, "bhavani_label_template_${h.id}.prn")
                                    file.writeText(h.content)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share PRN version"))
                                } catch (_: Exception) { }
                            }) { Text("Export") }
                            TextButton(onClick = { confirmDelete = h.id }) {
                                Icon(Icons.Filled.Delete, "Delete", tint = ErrorColor)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this version?") },
            text = { Text("This permanently removes it from PRN history. It won't affect the current template if it's already saved elsewhere.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.prnHistoryDao().delete(id) }
                    confirmDelete = null
                }) { Text("Delete", color = ErrorColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}
