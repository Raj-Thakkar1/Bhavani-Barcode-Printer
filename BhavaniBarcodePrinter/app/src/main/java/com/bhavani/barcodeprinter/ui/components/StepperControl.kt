package com.bhavani.barcodeprinter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhavani.barcodeprinter.ui.theme.Accent
import com.bhavani.barcodeprinter.ui.theme.Gold

/**
 * A +/- stepper with a full 48dp tap target on each button (Material's minimum recommended
 * touch size). The default OutlinedButton-with-tiny-icon pattern used before this was easy
 * to mis-tap, especially with a thumb on a small phone — this fixes that everywhere it's used.
 */
@Composable
fun StepperControl(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    min: Int = 1,
    max: Int = Int.MAX_VALUE
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { if (value - step >= min) onChange(value - step) },
            modifier = Modifier.size(48.dp).background(Accent.copy(alpha = 0.25f), CircleShape)
        ) { Icon(Icons.Filled.Remove, "Decrease", tint = Gold) }

        Text(
            "$value",
            color = Gold, fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        IconButton(
            onClick = { if (value + step <= max) onChange(value + step) },
            modifier = Modifier.size(48.dp).background(Accent.copy(alpha = 0.25f), CircleShape)
        ) { Icon(Icons.Filled.Add, "Increase", tint = Gold) }
    }
}
