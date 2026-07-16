package com.rothrockware.studyjazzstandards.ui.repertoire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.rothrockware.studyjazzstandards.data.DateInt
import com.rothrockware.studyjazzstandards.ui.components.CollectToasts
import com.rothrockware.studyjazzstandards.ui.components.ConfirmDialog
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.MenuAction
import com.rothrockware.studyjazzstandards.ui.components.RowMenu
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

@Composable
fun RepertoireScreen(vm: RepertoireViewModel) {
    CollectToasts(vm)
    val pieces by vm.pieces.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("All pieces", pieces.size.takeIf { it > 0 })
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = vm::openAdd) { Text("+ Add repertoire", fontSize = 12.sp) }
        }
        if (pieces.isEmpty()) {
            EmptyState("No pieces yet. Add your first repertoire entry to get started.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(pieces, key = { it.name }) { p ->
                    ListRow(
                        name = p.name,
                        meta = listOfNotNull(p.composer, p.notes).joinToString(" · ").ifEmpty { null },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.lastReviewed?.let { "Last reviewed ${DateInt.formatIntDate(it)}" }
                                        ?: "Never reviewed",
                                    fontSize = 11.sp,
                                    color = JazzColors.Text3,
                                )
                                RowMenu(
                                    listOf(
                                        MenuAction("Edit") { vm.openEdit(p.name) },
                                        MenuAction("Remove", danger = true) { vm.openRemove(p.name) },
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    when (val d = vm.dialog) {
        is RepertoireDialog.Add -> RepertoireFormDialog(
            title = "Add repertoire",
            name = d.name, composer = d.composer, notes = d.notes, showError = d.showError,
            onName = { v -> vm.updateAdd { it.copy(name = v) } },
            onComposer = { v -> vm.updateAdd { it.copy(composer = v) } },
            onNotes = { v -> vm.updateAdd { it.copy(notes = v) } },
            onSubmit = vm::submitAdd,
            onDismiss = vm::closeDialog,
        )
        is RepertoireDialog.Edit -> RepertoireFormDialog(
            title = "Edit repertoire",
            name = d.name, composer = d.composer, notes = d.notes, showError = d.showError,
            onName = { v -> vm.updateEdit { it.copy(name = v) } },
            onComposer = { v -> vm.updateEdit { it.copy(composer = v) } },
            onNotes = { v -> vm.updateEdit { it.copy(notes = v) } },
            onSubmit = vm::submitEdit,
            onDismiss = vm::closeDialog,
        )
        is RepertoireDialog.Remove -> ConfirmDialog(
            title = "Remove repertoire?",
            text = "\"${d.name}\" will be removed from your repertoire.",
            onConfirm = vm::confirmRemove,
            onDismiss = vm::closeDialog,
        )
        null -> Unit
    }
}

@Composable
private fun RepertoireFormDialog(
    title: String,
    name: String,
    composer: String,
    notes: String,
    showError: Boolean,
    onName: (String) -> Unit,
    onComposer: (String) -> Unit,
    onNotes: (String) -> Unit,
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
                    Text("A piece with that name already exists.", color = JazzColors.Red, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = composer,
                    onValueChange = onComposer,
                    label = { Text("Composer (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotes,
                    label = { Text("Notes (optional)") },
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
