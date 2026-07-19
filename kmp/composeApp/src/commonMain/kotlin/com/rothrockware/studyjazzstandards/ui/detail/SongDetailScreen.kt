package com.rothrockware.studyjazzstandards.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.data.DateInt
import com.rothrockware.studyjazzstandards.data.DefaultJazzRepository
import com.rothrockware.studyjazzstandards.data.store.InMemoryBlobStore
import com.rothrockware.studyjazzstandards.ui.activity.ActivityPeriod
import com.rothrockware.studyjazzstandards.ui.activity.formatActivityEntry
import com.rothrockware.studyjazzstandards.ui.activity.formatEntryMeta
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.IntervalTrack
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.components.StatusBadge
import com.rothrockware.studyjazzstandards.ui.components.StyleBadge
import com.rothrockware.studyjazzstandards.ui.components.TimelineEntryRow
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors
import com.rothrockware.studyjazzstandards.ui.theme.JazzTheme
import kotlinx.datetime.TimeZone

@Composable
fun SongDetailScreen(
    vm: SongDetailViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val zone = TimeZone.currentSystemDefault()

    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text("← Back", color = JazzColors.Text2) }

        val song = state.song
        if (song == null) {
            EmptyState("Song not found.")
            return@Column
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(song.name, style = MaterialTheme.typography.headlineSmall, color = JazzColors.Text)
            StyleBadge(song.style)
            LevelBadge(song.currentLevel)
            StatusBadge(song.status)
        }

        if (song.intervalIdx >= 0) {
            Row(Modifier.padding(top = 10.dp)) {
                IntervalTrack(song.intervalIdx)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MetaField("Next review", song.nextReview?.let { DateInt.formatIntDate(it) } ?: "—")
            MetaField("Composer", song.composer ?: "—")
            MetaField("Year", song.year?.toString() ?: "—")
        }

        SectionHeader("Activity")
        if (state.entries.isEmpty()) {
            EmptyState("No activity for this song yet.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.entries, key = { "${it.ts}-${it.type}" }) { entry ->
                    val formatted = formatActivityEntry(entry)
                    TimelineEntryRow(
                        dot = formatted.dot,
                        label = formatted.label,
                        detail = null,
                        meta = formatEntryMeta(entry.ts, ActivityPeriod.Week, zone),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaField(label: String, value: String) {
    Column {
        Text(label.uppercase(), fontSize = 10.sp, color = JazzColors.Text3, letterSpacing = 1.sp)
        Text(value, fontSize = 13.sp, color = JazzColors.Text)
    }
}

@Preview
@Composable
private fun SongDetailScreenPreview() {
    val vm = remember {
        val repo = DefaultJazzRepository(InMemoryBlobStore())
        val songName = repo.db.value.songs.keys.first()
        SongDetailViewModel(repo, songName)
    }
    JazzTheme {
        SongDetailScreen(vm = vm, onBack = {})
    }
}
