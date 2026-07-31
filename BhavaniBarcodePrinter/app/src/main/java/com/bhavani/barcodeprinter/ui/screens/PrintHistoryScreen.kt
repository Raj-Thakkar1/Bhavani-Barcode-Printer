package com.bhavani.barcodeprinter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.bhavani.barcodeprinter.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrintHistoryScreen(nav: NavHostController) {
    val db = AppContainer.db
    val history by db.printHistoryDao().recent().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Text("Print History", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No print jobs yet.", color = Color_Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(history, key = { it.id }) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(EntryBg, RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${h.itemName}  ·  ${h.copies} label(s)", color = Fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Barcode: ${h.barcodeNum}", color = Color_Muted, fontSize = 12.sp)
                            Text(fmt.format(Date(h.printedAt)), color = Color_Muted, fontSize = 11.sp)
                        }
                        Text(
                            if (h.success) "✓ Sent" else "✕ Failed",
                            color = if (h.success) SuccessColor else ErrorColor,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
