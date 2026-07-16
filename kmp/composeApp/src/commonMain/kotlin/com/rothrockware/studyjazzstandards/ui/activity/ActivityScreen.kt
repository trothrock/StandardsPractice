package com.rothrockware.studyjazzstandards.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.components.TimelineEntryRow

@Composable
fun ActivityScreen(vm: ActivityViewModel) {
    val groups by vm.groups.collectAsState()
    val period by vm.period.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { vm.period.value = p },
                    label = { Text(p.label) },
                )
            }
        }
        if (groups.isEmpty()) {
            EmptyState("No activity yet.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                groups.forEach { group ->
                    item(key = "header-${group.label}") {
                        SectionHeader(group.label, group.entries.size)
                    }
                    items(group.entries, key = { "${it.ts}-${it.type}-${it.name}" }) { entry ->
                        val formatted = formatActivityEntry(entry)
                        TimelineEntryRow(
                            dot = formatted.dot,
                            label = formatted.label,
                            detail = formatted.detail,
                            meta = formatEntryMeta(entry.ts, period, vm.timeZone()),
                        )
                    }
                }
            }
        }
    }
}
