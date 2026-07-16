package com.rothrockware.studyjazzstandards.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.IntervalTrack
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.StatusBadge

@Composable
fun QueueScreen(
    vm: QueueViewModel,
    onOpenDetail: (String) -> Unit,
) {
    val songs by vm.songs.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Search queue…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueSort.entries.forEach { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { vm.sort.value = s },
                    label = { Text(s.label) },
                )
            }
        }
        if (songs.isEmpty()) {
            EmptyState("No songs in the review queue yet.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(songs, key = { it.name }) { sg ->
                    ListRow(
                        onClick = { onOpenDetail(sg.name) },
                        leading = { LevelBadge(sg.currentLevel) },
                        name = sg.name,
                        nameBadge = {
                            if (sg.status == SongStatus.GRADUATED) StatusBadge(sg.status)
                        },
                        trailing = { IntervalTrack(sg.intervalIdx) },
                    )
                }
            }
        }
    }
}
