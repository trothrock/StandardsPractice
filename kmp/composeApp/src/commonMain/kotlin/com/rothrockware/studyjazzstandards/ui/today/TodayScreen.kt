package com.rothrockware.studyjazzstandards.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.data.DefaultJazzRepository
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.seed.INTERVALS
import com.rothrockware.studyjazzstandards.data.store.InMemoryBlobStore
import com.rothrockware.studyjazzstandards.ui.components.CollectToasts
import com.rothrockware.studyjazzstandards.ui.components.ConfirmDialog
import com.rothrockware.studyjazzstandards.ui.components.LevelBadge
import com.rothrockware.studyjazzstandards.ui.components.ListRow
import com.rothrockware.studyjazzstandards.ui.components.LocalSnackbarHost
import com.rothrockware.studyjazzstandards.ui.components.MenuAction
import com.rothrockware.studyjazzstandards.ui.components.RowMenu
import com.rothrockware.studyjazzstandards.ui.components.SectionHeader
import com.rothrockware.studyjazzstandards.ui.components.StyleBadge
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors
import com.rothrockware.studyjazzstandards.ui.theme.JazzTheme

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpenDetail: (String) -> Unit,
    onOpenVoicings: () -> Unit,
) {
    CollectToasts(vm)
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(state.dateLabel, style = MaterialTheme.typography.headlineSmall, color = JazzColors.Text)
        Text(
            "${state.streak} day streak",
            fontSize = 12.sp,
            color = JazzColors.Text3,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Currently learning / next suggested
        val learning = state.learning
        if (learning != null) {
            FeatureCard(
                dotColor = JazzColors.Gold,
                pulsing = true,
                actions = {
                    if (learning.practicedToday) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) { Text("✓ Practiced", color = JazzColors.Green) }
                    } else {
                        OutlinedButton(
                            onClick = { vm.markPracticed(learning.song.name) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Mark as practiced") }
                    }
                    Button(
                        onClick = { vm.markLearned(learning.song.name) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Mark as level 2") }
                },
            ) {
                CardInfo(
                    label = "Currently learning",
                    name = learning.song.name,
                    sub = "${learning.song.style.ifEmpty { "Standard" }} · Work on this all week",
                    onClick = { onOpenDetail(learning.song.name) },
                )
                LevelBadge(learning.song.baseLevel)
            }
        } else {
            val next = state.suggested
            if (next != null) {
                FeatureCard(dotColor = JazzColors.Text3) {
                    CardInfo(
                        label = "Next suggested song",
                        name = next.name,
                        sub = "${next.style.ifEmpty { "Standard" }} · " +
                            if (next.baseLevel == 1) "Already at level 1 — push to 2" else "Starting from level 0",
                    )
                    LevelBadge(next.baseLevel)
                    OutlinedButton(onClick = { vm.startLearning(next.name) }) { Text("Start learning") }
                }
            }
        }

        // Today's repertoire piece
        val rep = state.featuredRepertoire
        if (rep != null) {
            Spacer(Modifier.size(10.dp))
            FeatureCard(dotColor = JazzColors.Gold2) {
                CardInfo(
                    label = "Today's repertoire piece",
                    name = rep.name,
                    sub = listOfNotNull(rep.composer, rep.notes).joinToString(" · ").ifEmpty { null },
                )
                if (state.repertoireDoneToday) {
                    OutlinedButton(onClick = {}, enabled = false) { Text("✓ Reviewed", color = JazzColors.Green) }
                } else {
                    PassButton("Reviewed") { vm.markRepertoireReviewed(rep.name) }
                    FailButton("Continue tomorrow") { vm.repertoireContinueTomorrow(rep.name) }
                }
            }
        }

        // Today's voicing
        val voicing = state.featuredVoicing
        if (voicing != null) {
            Spacer(Modifier.size(10.dp))
            FeatureCard(dotColor = JazzColors.Gold2) {
                CardInfo(
                    label = "Today's voicing",
                    name = voicing.name,
                    sub = listOfNotNull(
                        voicing.category,
                        voicing.topNote?.let { "top note $it" },
                    ).joinToString(" · "),
                    onClick = onOpenVoicings,
                )
                LevelBadge(voicing.familiarity)
                if (state.voicingDoneToday) {
                    OutlinedButton(onClick = {}, enabled = false) { Text("✓ Practiced", color = JazzColors.Green) }
                } else {
                    Button(onClick = { vm.voicingPracticed(voicing.name) }) { Text("Mark as practiced") }
                }
            }
        }

        // Reviews due today
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Reviews due today", state.due.size.takeIf { it > 0 })
            Spacer(Modifier.weight(1f))
            RowMenu(
                listOf(MenuAction("Review settings") { vm.showReviewSettings = true }),
            )
        }
        if (state.due.isEmpty()) {
            AllClear(
                title = if (state.reviewedToday) "Reviews done" else "All clear",
                sub = if (state.reviewedToday) {
                    "Nothing left to review today · check back tomorrow"
                } else {
                    "Check back tomorrow · or start a new song above"
                },
                showReviewAnother = state.reviewedToday && state.hasUpcoming,
                onReviewAnother = vm::reviewAnother,
            )
        } else {
            state.due.forEach { sg ->
                DueReviewRow(
                    song = sg,
                    onOpenDetail = { onOpenDetail(sg.name) },
                    onPass = { vm.doReview(sg.name, true) },
                    onFail = { vm.doReview(sg.name, false) },
                )
            }
        }
    }

    // Switch-learning confirmation (replaces the web confirm())
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

    if (vm.showReviewSettings) {
        ReviewSettingsDialog(
            current = state.reviewMax,
            onSubmit = vm::setReviewMax,
            onDismiss = { vm.showReviewSettings = false },
        )
    }
}

@Composable
private fun FeatureCard(
    dotColor: androidx.compose.ui.graphics.Color,
    pulsing: Boolean = false,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JazzColors.Bg2, RoundedCornerShape(8.dp))
            .border(1.dp, JazzColors.Border, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            content()
        }
        if (actions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CardInfo(
    label: String,
    name: String,
    sub: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Text(label.uppercase(), fontSize = 10.sp, color = JazzColors.Text3, letterSpacing = 1.sp)
        Text(name, fontSize = 16.sp, color = JazzColors.Text, fontWeight = FontWeight.Medium)
        if (!sub.isNullOrEmpty()) Text(sub, fontSize = 12.sp, color = JazzColors.Text2)
    }
}

@Composable
fun PassButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = JazzColors.GreenDim,
            contentColor = JazzColors.Green,
        ),
    ) { Text(label) }
}

@Composable
fun FailButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = JazzColors.Bg3,
            contentColor = JazzColors.Text2,
        ),
    ) { Text(label) }
}

@Composable
private fun AllClear(
    title: String,
    sub: String,
    showReviewAnother: Boolean,
    onReviewAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 16.sp, color = JazzColors.Text2, fontWeight = FontWeight.Medium)
        Text(sub, fontSize = 12.sp, color = JazzColors.Text3, modifier = Modifier.padding(top = 4.dp))
        if (showReviewAnother) {
            OutlinedButton(onClick = onReviewAnother, modifier = Modifier.padding(top = 14.dp)) {
                Text("Review another song", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DueReviewRow(
    song: Song,
    onOpenDetail: () -> Unit,
    onPass: () -> Unit,
    onFail: () -> Unit,
) {
    ListRow(
        onClick = onOpenDetail,
        leading = { LevelBadge(song.currentLevel) },
        name = song.name,
        nameBadge = { StyleBadge(song.style) },
        meta = if (song.intervalIdx >= 0) "${INTERVALS[song.intervalIdx]}d interval" else null,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PassButton("Reviewed", onPass)
                FailButton("Continue tomorrow", onFail)
            }
        },
    )
}

@Composable
private fun ReviewSettingsDialog(
    current: Int,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review settings") },
        text = {
            Column {
                Text("Max review songs per day (1–20)", color = JazzColors.Text2, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onSubmit) },
                enabled = value != null && value in 1..20,
            ) { Text("Save", color = JazzColors.Gold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = JazzColors.Text2) }
        },
    )
}

@Preview
@Composable
private fun TodayScreenPreview() {
    val vm = remember {
        val repo = DefaultJazzRepository(InMemoryBlobStore())
        repo.db.value.songs.keys.firstOrNull()?.let { repo.startLearning(it) }
        TodayViewModel(repo)
    }
    JazzTheme {
        CompositionLocalProvider(LocalSnackbarHost provides remember { SnackbarHostState() }) {
            TodayScreen(vm = vm, onOpenDetail = {}, onOpenVoicings = {})
        }
    }
}
