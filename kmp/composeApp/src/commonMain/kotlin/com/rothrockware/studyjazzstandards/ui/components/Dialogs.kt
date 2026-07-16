package com.rothrockware.studyjazzstandards.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Remove",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = JazzColors.Text2) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) JazzColors.Red else JazzColors.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JazzColors.Text2) }
        },
    )
}

/** The 0–3 level selector used by add/edit song and the ranker. */
@Composable
fun LevelPicker(
    selected: Int,
    descriptions: List<String>,
    onSelect: (Int) -> Unit,
    levels: IntRange = 0..3,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { lvl ->
                val isSel = lvl == selected
                val color = when (lvl) {
                    0 -> JazzColors.Text3
                    1 -> JazzColors.Blue
                    2 -> JazzColors.Gold
                    else -> JazzColors.Green
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isSel) color.copy(alpha = 0.18f) else JazzColors.Bg3,
                            RoundedCornerShape(4.dp),
                        )
                        .border(
                            1.dp,
                            if (isSel) color else JazzColors.Border2,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { onSelect(lvl) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$lvl",
                        color = if (isSel) color else JazzColors.Text2,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Text(
            descriptions.getOrElse(selected) { "" },
            fontSize = 12.sp,
            color = JazzColors.Text2,
        )
    }
}

/** Simple labeled dropdown used for style and voicing-category selection. */
@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    emptyOptionLabel: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value.ifEmpty { emptyOptionLabel ?: "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true },
            enabled = false,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = JazzColors.Text,
                disabledBorderColor = JazzColors.Border2,
                disabledLabelColor = JazzColors.Text2,
            ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (emptyOptionLabel != null) {
                DropdownMenuItem(
                    text = { Text(emptyOptionLabel) },
                    onClick = { open = false; onSelect("") },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
fun FieldSpacer() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
