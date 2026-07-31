package com.bhavani.barcodeprinter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BhavaniColorScheme = darkColorScheme(
    background = Bg,
    surface = EntryBg,
    primary = Accent,
    onPrimary = Fg,
    onBackground = Fg,
    onSurface = Fg,
    error = ErrorColor,
)

@Composable
fun BhavaniBarcodePrinterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BhavaniColorScheme,
        content = content
    )
}
