package com.rothrockware.studyjazzstandards.ui.voicings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.data.seed.VOICING_CATEGORIES
import com.rothrockware.studyjazzstandards.data.seed.VOICING_FAM_DESCS
import com.rothrockware.studyjazzstandards.ui.components.CollectToasts
import com.rothrockware.studyjazzstandards.ui.components.ConfirmDialog
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.LevelPicker
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.MenuAction
import com.rothrockware.studyjazzstandards.ui.components.RowMenu
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.components.SelectField
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

@Composable
fun VoicingsScreen(vm: VoicingsViewModel) {
    CollectToasts(vm)
    val sections by vm.sections.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = vm::openAdd) { Text("+ Add voicing", fontSize = 12.sp) }
        }
        if (sections.isEmpty()) {
            EmptyState("No voicings yet. Add your first voicing to get started.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                sections.forEach { section ->
                    item(key = "header-${section.category}") {
                        SectionHeader(section.category, section.voicings.size)
                    }
                    items(section.voicings, key = { "${section.category}-${it.name}" }) { v ->
                        ListRow(
                            leading = {
                                // Voicing image thumbnails ship later; web renders
                                // a "no img" placeholder for missing files too.
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(JazzColors.Bg3, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("no img", fontSize = 8.sp, color = JazzColors.Text3)
                                }
                            },
                            name = v.name,
                            meta = v.topNote?.let { "top: $it" },
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    LevelBadge(v.familiarity)
                                    RowMenu(
                                        listOf(
                                            MenuAction("Mark as practiced") { vm.practiced(v.name) },
                                            MenuAction("Edit") { vm.openEdit(v.name) },
                                            MenuAction("Remove", danger = true) { vm.openRemove(v.name) },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    when (val d = vm.dialog) {
        is VoicingDialog.Add -> VoicingFormDialog(
            title = "Add voicing",
            name = d.name, category = d.category, topNote = d.topNote,
            image = d.image, familiarity = d.familiarity, showError = d.showError,
            onName = { v -> vm.updateAdd { it.copy(name = v) } },
            onCategory = { v -> vm.updateAdd { it.copy(category = v) } },
            onTopNote = { v -> vm.updateAdd { it.copy(topNote = v) } },
            onImage = { v -> vm.updateAdd { it.copy(image = v) } },
            onFamiliarity = { v -> vm.updateAdd { it.copy(familiarity = v) } },
            onSubmit = vm::submitAdd,
            onDismiss = vm::closeDialog,
        )
        is VoicingDialog.Edit -> VoicingFormDialog(
            title = "Edit voicing",
            name = d.name, category = d.category, topNote = d.topNote,
            image = d.image, familiarity = d.familiarity, showError = d.showError,
            onName = { v -> vm.updateEdit { it.copy(name = v) } },
            onCategory = { v -> vm.updateEdit { it.copy(category = v) } },
            onTopNote = { v -> vm.updateEdit { it.copy(topNote = v) } },
            onImage = { v -> vm.updateEdit { it.copy(image = v) } },
            onFamiliarity = { v -> vm.updateEdit { it.copy(familiarity = v) } },
            onSubmit = vm::submitEdit,
            onDismiss = vm::closeDialog,
        )
        is VoicingDialog.Remove -> ConfirmDialog(
            title = "Remove voicing?",
            text = "\"${d.name}\" will be removed from your voicings.",
            onConfirm = vm::confirmRemove,
            onDismiss = vm::closeDialog,
        )
        null -> Unit
    }
}

@Composable
private fun VoicingFormDialog(
    title: String,
    name: String,
    category: String,
    topNote: String,
    image: String,
    familiarity: Int,
    showError: Boolean,
    onName: (String) -> Unit,
    onCategory: (String) -> Unit,
    onTopNote: (String) -> Unit,
    onImage: (String) -> Unit,
    onFamiliarity: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = showError,
                )
                if (showError) {
                    Text("A voicing with that name already exists.", color = JazzColors.Red, fontSize = 12.sp)
                }
                SelectField(
                    label = "Category",
                    value = category,
                    options = VOICING_CATEGORIES,
                    onSelect = onCategory,
                )
                OutlinedTextField(
                    value = topNote,
                    onValueChange = onTopNote,
                    label = { Text("Top note (e.g. 7, b9)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = image,
                    onValueChange = onImage,
                    label = { Text("Image filename (optional)") },
                    singleLine = true,
                )
                Text("Familiarity", color = JazzColors.Text2, fontSize = 12.sp)
                LevelPicker(
                    selected = familiarity,
                    descriptions = VOICING_FAM_DESCS,
                    onSelect = onFamiliarity,
                    levels = 0..2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = name.isNotBlank()) {
                Text("Save", color = JazzColors.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JazzColors.Text2) }
        },
    )
}
