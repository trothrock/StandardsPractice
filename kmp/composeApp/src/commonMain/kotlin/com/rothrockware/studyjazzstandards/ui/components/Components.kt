package com.rothrockware.studyjazzstandards.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.INTERVALS
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

private val levelColors = listOf(
    JazzColors.Text3,
    JazzColors.Blue,
    JazzColors.Gold,
    JazzColors.Green,
)

@Composable
fun LevelBadge(level: Int) {
    val color = levelColors[level.coerceIn(0, 3)]
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$level", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, color = color)
    }
}

@Composable
fun StyleBadge(style: String) {
    if (style.isNotEmpty()) PillBadge(style, JazzColors.Text2)
}

@Composable
fun StatusBadge(status: String) {
    when (status) {
        SongStatus.GRADUATED -> PillBadge("Graduated", JazzColors.Green)
        SongStatus.ACTIVE -> PillBadge("In review", JazzColors.Gold)
        SongStatus.LEARNING -> PillBadge("Learning", JazzColors.Blue)
    }
}

/** The 1d/3d/7d/21d/60d/180d pill track from the queue and detail views. */
@Composable
fun IntervalTrack(intervalIdx: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        INTERVALS.forEachIndexed { i, days ->
            val (bg, fg) = when {
                i < intervalIdx -> JazzColors.Green.copy(alpha = 0.18f) to JazzColors.Green
                i == intervalIdx -> JazzColors.Gold.copy(alpha = 0.18f) to JazzColors.Gold
                else -> JazzColors.Bg3 to JazzColors.Text3
            }
            Box(
                modifier = Modifier
                    .background(bg, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("${days}d", fontSize = 10.sp, color = fg)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = JazzColors.Text,
        )
        if (count != null) {
            Text("$count", fontSize = 11.sp, color = JazzColors.Text3)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = JazzColors.Text3, fontSize = 13.sp)
    }
}

data class MenuAction(
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** The shared "···" row menu. */
@Composable
fun RowMenu(actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("···", color = JazzColors.Text2, fontSize = 14.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            action.label,
                            color = if (action.danger) JazzColors.Red else JazzColors.Text,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(JazzColors.Bg2, RoundedCornerShape(8.dp))
            .border(1.dp, JazzColors.Border, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Text(value, fontSize = 24.sp, color = JazzColors.Gold, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 11.sp, color = JazzColors.Text2)
    }
}

enum class TimelineDot(val color: Color) {
    Blue(JazzColors.Blue),
    Gold(JazzColors.Gold),
    Green(JazzColors.Green),
    Red(JazzColors.Red),
    Dim(JazzColors.Text3),
}

@Composable
fun TimelineEntryRow(dot: TimelineDot, label: String, detail: String?, meta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(dot.color, CircleShape),
        )
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, fontSize = 13.sp, color = JazzColors.Text)
                if (!detail.isNullOrEmpty()) {
                    Text("— $detail", fontSize = 13.sp, color = JazzColors.Text2)
                }
            }
            Text(meta, fontSize = 11.sp, color = JazzColors.Text3)
        }
    }
}
