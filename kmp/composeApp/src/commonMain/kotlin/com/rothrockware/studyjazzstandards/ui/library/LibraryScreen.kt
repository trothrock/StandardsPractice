package com.rothrockware.studyjazzstandards.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.LEVEL_DESCS
import com.rothrockware.studyjazzstandards.ui.components.CollectToasts
import com.rothrockware.studyjazzstandards.ui.components.ConfirmDialog
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.LevelPicker
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.MenuAction
import com.rothrockware.studyjazzstandards.ui.components.RowMenu
import com.rothrockware.studyjazzstandards.ui.components.SelectField
import com.rothrockware.studyjazzstandards.ui.components.StatusBadge
import com.rothrockware.studyjazzstandards.ui.components.StyleBadge
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenDetail: (String) -> Unit,
) {
    CollectToasts(vm)
    val songs by vm.songs.collectAsState()
    val filters by vm.filters.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search songs…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                SelectField(
                    label = "Level",
                    value = filters.level?.toString() ?: "",
                    options = listOf("0", "1", "2", "3"),
                    onSelect = { vm.setLevel(it.toIntOrNull()) },
                    emptyOptionLabel = "All levels",
                )
            }
            Column(Modifier.weight(1f)) {
                SelectField(
                    label = "Status",
                    value = filters.status ?: "",
                    options = listOf(
                        SongStatus.UNTOUCHED, SongStatus.LEARNING,
                        SongStatus.ACTIVE, SongStatus.GRADUATED,
                    ),
                    onSelect = { vm.setStatus(it.ifEmpty { null }) },
                    emptyOptionLabel = "All statuses",
                )
            }
            Column(Modifier.weight(1f)) {
                SelectField(
                    label = "Style",
                    value = filters.style ?: "",
                    options = SONG_STYLES,
                    onSelect = { vm.setStyle(it.ifEmpty { null }) },
                    emptyOptionLabel = "All styles",
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = vm::openAdd) { Text("+ Add song", fontSize = 12.sp) }
        }

        if (songs.isEmpty()) {
            EmptyState("No songs match.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(songs, key = { it.name }) { sg ->
                    ListRow(
                        onClick = { onOpenDetail(sg.name) },
                        leading = { LevelBadge(sg.currentLevel) },
                        name = sg.name,
                        nameBadge = { StyleBadge(sg.style) },
                        trailing = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                StatusBadge(sg.status)
                                Spacer(Modifier.width(4.dp))
                                RowMenu(
                                    buildList {
                                        when (sg.status) {
                                            SongStatus.UNTOUCHED ->
                                                add(MenuAction("Start learning") { vm.startLearning(sg.name) })
                                            SongStatus.LEARNING ->
                                                add(MenuAction("Mark as level 2") { vm.markLearned(sg.name) })
                                        }
                                        add(MenuAction("Edit") { vm.openEdit(sg.name) })
                                        add(MenuAction("Remove", danger = true) { vm.openRemove(sg.name) })
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    SongDialogs(vm)

    vm.pendingSwitch?.let { pending ->
        ConfirmDialog(
            title = "Switch songs?",
            text = "You're currently working on \"${pending.currentName}\". Switch to \"${pending.newName}\"?",
            confirmLabel = "Switch",
            danger = false,
            onConfirm = vm::confirmSwitch,
            onDismiss = vm::cancelSwitch,
        )
    }
}

@Composable
fun SongDialogs(vm: LibraryViewModel) {
    when (val d = vm.dialog) {
        is SongDialog.Add -> SongFormDialog(
            title = "Add song",
            name = d.name,
            style = d.style,
            level = d.level,
            showError = d.showError,
            onName = { n -> vm.updateAdd { it.copy(name = n) } },
            onStyle = { s -> vm.updateAdd { it.copy(style = s) } },
            onLevel = { l -> vm.updateAdd { it.copy(level = l) } },
            onSubmit = vm::submitAdd,
            onDismiss = vm::closeDialog,
        )
        is SongDialog.Edit -> SongFormDialog(
            title = "Edit song",
            name = d.name,
            style = d.style,
            level = d.level,
            showError = d.showError,
            onName = { n -> vm.updateEdit { it.copy(name = n) } },
            onStyle = { s -> vm.updateEdit { it.copy(style = s) } },
            onLevel = { l -> vm.updateEdit { it.copy(level = l) } },
            onSubmit = vm::submitEdit,
            onDismiss = vm::closeDialog,
        )
        is SongDialog.Remove -> ConfirmDialog(
            title = "Remove song?",
            text = "\"${d.name}\" and its review history will be removed.",
            onConfirm = vm::confirmRemove,
            onDismiss = vm::closeDialog,
        )
        null -> Unit
    }
}

@Composable
private fun SongFormDialog(
    title: String,
    name: String,
    style: String,
    level: Int,
    showError: Boolean,
    onName: (String) -> Unit,
    onStyle: (String) -> Unit,
    onLevel: (Int) -> Unit,
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
                    label = { Text("Song name") },
                    singleLine = true,
                    isError = showError,
                )
                if (showError) {
                    Text("A song with that name already exists.", color = JazzColors.Red, fontSize = 12.sp)
                }
                SelectField(
                    label = "Style",
                    value = style,
                    options = SONG_STYLES,
                    onSelect = onStyle,
                    emptyOptionLabel = "Standard",
                )
                Text("Familiarity", color = JazzColors.Text2, fontSize = 12.sp)
                LevelPicker(selected = level, descriptions = LEVEL_DESCS, onSelect = onLevel)
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
