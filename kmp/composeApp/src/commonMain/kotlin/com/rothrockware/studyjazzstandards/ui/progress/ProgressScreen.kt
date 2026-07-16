package com.rothrockware.studyjazzstandards.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.ui.components.EmptyState
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.components.StatCard
import com.rothrockware.studyjazzstandards.ui.components.StyleBadge
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

@Composable
fun ProgressScreen(
    vm: ProgressViewModel,
    onOpenDetail: (String) -> Unit,
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("${state.notStarted}", "Not started", Modifier.weight(1f))
            StatCard("${state.inProgress}", "In progress", Modifier.weight(1f))
            StatCard("${state.atLevelTwoPlus}", "At level 2+", Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.atLevelTwoPlus} of ${state.total} at level 2+",
                fontSize = 12.sp,
                color = JazzColors.Text2,
                modifier = Modifier.weight(1f),
            )
            Text("${state.percent}%", fontSize = 12.sp, color = JazzColors.Gold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(JazzColors.Bg3, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.percent / 100f)
                    .fillMaxHeight()
                    .background(JazzColors.Gold, RoundedCornerShape(3.dp)),
            )
        }

        SectionHeader("Next songs to learn")
        if (state.nextSongs.isEmpty()) {
            EmptyState("All priority songs started!")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(state.nextSongs, key = { _, sg -> sg.name }) { i, sg ->
                    ListRow(
                        onClick = { onOpenDetail(sg.name) },
                        leading = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}",
                                    fontSize = 11.sp,
                                    color = JazzColors.Text3,
                                    modifier = Modifier.width(22.dp),
                                )
                                LevelBadge(sg.baseLevel)
                            }
                        },
                        name = sg.name,
                        nameBadge = { StyleBadge(sg.style) },
                    )
                }
            }
        }
    }
}
