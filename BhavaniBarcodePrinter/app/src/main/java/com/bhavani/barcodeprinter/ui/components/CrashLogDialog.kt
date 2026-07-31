package com.bhavani.barcodeprinter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown once, on the launch right after a crash. Lets the person copy the full stack trace
 * (with device/Android version info) to the clipboard so it can be pasted into a message —
 * no computer or adb needed, works the same on the POS tablet as on any test device.
 */
@Composable
fun CrashLogDialog(log: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("The app closed unexpectedly last time") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(if (copied) "Copied ✓ — paste it wherever you need to send it." else
                    "Tap \"Copy log\" and share the text so this can be fixed.", fontSize = 12.sp)
                Text("\n$log", fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(log))
                copied = true
            }) { Text("Copy log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}
