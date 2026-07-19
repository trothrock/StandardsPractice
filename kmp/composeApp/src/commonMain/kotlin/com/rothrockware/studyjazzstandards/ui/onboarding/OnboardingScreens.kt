package com.rothrockware.studyjazzstandards.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rothrockware.studyjazzstandards.data.DefaultJazzRepository
import com.rothrockware.studyjazzstandards.data.seed.LEVEL_DESCS
import com.rothrockware.studyjazzstandards.data.store.InMemoryBlobStore
import com.rothrockware.studyjazzstandards.ui.components.CollectToasts
import com.rothrockware.studyjazzstandards.ui.components.LevelPicker
import com.rothrockware.studyjazzstandards.ui.components.LocalSnackbarHost
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors
import com.rothrockware.studyjazzstandards.ui.theme.JazzTheme

/** Welcome + ranker flow shown over the app until onboarding completes. */
@Composable
fun OnboardingFlow(vm: OnboardingViewModel) {
    CollectToasts(vm)
    when (vm.stage) {
        OnboardingStage.Welcome -> WelcomeDialog(
            onStart = vm::startRanker,
            onSkip = vm::skip,
        )
        OnboardingStage.Ranker -> RankerDialog(vm)
    }
}

@Composable
private fun WelcomeDialog(onStart: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rate your familiarity with each standard so the app can build " +
                        "your practice schedule. The four levels:",
                    color = JazzColors.Text2,
                )
                LEVEL_DESCS.forEachIndexed { i, desc ->
                    Text("$i — $desc", fontSize = 13.sp, color = JazzColors.Text2)
                }
            }
        },
        confirmButton = {
            Button(onClick = onStart) { Text("Get started") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Do this later", color = JazzColors.Text2) }
        },
    )
}

@Composable
private fun RankerDialog(vm: OnboardingViewModel) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .background(JazzColors.Bg2, RoundedCornerShape(8.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Song ${vm.index + 1} of ${vm.totalSongs}",
                fontSize = 12.sp,
                color = JazzColors.Text3,
            )
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(JazzColors.Bg3, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((vm.index + 1).toFloat() / vm.totalSongs)
                        .fillMaxHeight()
                        .background(JazzColors.Gold, RoundedCornerShape(2.dp)),
                )
            }

            val song = vm.currentSong
            Text(song.title, style = MaterialTheme.typography.headlineSmall, color = JazzColors.Text)
            Text(
                listOf(song.composer, song.year.toString(), song.style)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                fontSize = 12.sp,
                color = JazzColors.Text2,
            )

            LevelPicker(
                selected = vm.currentLevel,
                descriptions = LEVEL_DESCS,
                onSelect = vm::setLevel,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = vm::back, enabled = !vm.isFirst) { Text("← Back") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::finish) { Text("Finish & save", color = JazzColors.Text2) }
                Button(onClick = vm::next) { Text(if (vm.isLast) "Done ✓" else "Next →") }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingFlowPreview() {
    val vm = remember {
        val repo = DefaultJazzRepository(InMemoryBlobStore())
        OnboardingViewModel(repo)
    }
    JazzTheme {
        CompositionLocalProvider(LocalSnackbarHost provides remember { SnackbarHostState() }) {
            OnboardingFlow(vm = vm)
        }
    }
}
