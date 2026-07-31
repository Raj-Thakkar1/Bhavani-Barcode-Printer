package com.bhavani.barcodeprinter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
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
import kotlinx.coroutines.launch

@Composable
fun DatabaseScreen(nav: NavHostController) {
    val db = AppContainer.db
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val items by db.itemDao().search(query).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Item Database", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { nav.navigate("print") }) {
                    Icon(Icons.Filled.Add, "Add item", tint = Color.White)
                }
                IconButton(onClick = { nav.navigate("recycleBin") }) {
                    Icon(Icons.Filled.Delete, "Recycle Bin", tint = Color.White)
                }
                IconButton(onClick = { nav.navigate("printHistory") }) {
                    Icon(Icons.Filled.History, "Print History", tint = Color.White)
                }
            }
        }

        Box(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search item name…", color = Color_Muted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "No items yet. Print a label to add your first item."
                    else "No items match \"$query\".",
                    color = Color_Muted, fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(EntryBg, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val mrpText = if (item.mrp.isBlank()) "—" else item.mrp
                        val spText = if (item.sp.isBlank()) "—" else item.sp
                        Column(
                            Modifier.weight(1f).clickable { nav.navigate("print/${item.id}") }
                        ) {
                            Text(item.name, color = Fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Barcode: ${item.barcodeNum}", color = Color_Muted, fontSize = 12.sp)
                            Text("MRP \u20B9$mrpText  ·  SP \u20B9$spText", color = Gold, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            scope.launch { db.itemDao().softDelete(item.id) }
                        }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = ErrorColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecycleBinScreen(nav: NavHostController) {
    val db = AppContainer.db
    val scope = rememberCoroutineScope()
    val deleted by db.itemDao().allDeleted().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Accent).padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Text("Recycle Bin  ·  auto-deletes after 30 days", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (deleted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Recycle bin is empty.", color = Color_Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(deleted, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(EntryBg, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Fg, fontWeight = FontWeight.Bold)
                            Text("Barcode: ${item.barcodeNum}", color = Color_Muted, fontSize = 12.sp)
                        }
                        Row {
                            IconButton(onClick = { scope.launch { db.itemDao().restore(item.id) } }) {
                                Icon(Icons.Filled.Restore, "Restore", tint = SuccessColor)
                            }
                            IconButton(onClick = { scope.launch { db.itemDao().hardDelete(item.id) } }) {
                                Icon(Icons.Filled.Delete, "Delete permanently", tint = ErrorColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
