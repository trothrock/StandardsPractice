package com.rothrockware.studyjazzstandards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rothrockware.studyjazzstandards.data.model.isOnboardingComplete
import com.rothrockware.studyjazzstandards.di.AppContainer
import com.rothrockware.studyjazzstandards.di.LocalAppContainer
import com.rothrockware.studyjazzstandards.navigation.AppViewModel
import com.rothrockware.studyjazzstandards.navigation.Route
import com.rothrockware.studyjazzstandards.navigation.Tab as NavTab
import com.rothrockware.studyjazzstandards.ui.activity.ActivityScreen
import com.rothrockware.studyjazzstandards.ui.activity.ActivityViewModel
import com.rothrockware.studyjazzstandards.ui.components.ConfirmDialog
import com.rothrockware.studyjazzstandards.ui.components.LocalSnackbarHost
import com.rothrockware.studyjazzstandards.ui.components.MenuAction
import com.rothrockware.studyjazzstandards.ui.components.RowMenu
import com.rothrockware.studyjazzstandards.ui.detail.SongDetailScreen
import com.rothrockware.studyjazzstandards.ui.detail.SongDetailViewModel
import com.rothrockware.studyjazzstandards.ui.library.LibraryScreen
import com.rothrockware.studyjazzstandards.ui.library.LibraryViewModel
import com.rothrockware.studyjazzstandards.ui.onboarding.OnboardingFlow
import com.rothrockware.studyjazzstandards.ui.onboarding.OnboardingViewModel
import com.rothrockware.studyjazzstandards.ui.progress.ProgressScreen
import com.rothrockware.studyjazzstandards.ui.progress.ProgressViewModel
import com.rothrockware.studyjazzstandards.ui.queue.QueueScreen
import com.rothrockware.studyjazzstandards.ui.queue.QueueViewModel
import com.rothrockware.studyjazzstandards.ui.repertoire.RepertoireScreen
import com.rothrockware.studyjazzstandards.ui.repertoire.RepertoireViewModel
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors
import com.rothrockware.studyjazzstandards.ui.theme.JazzTheme
import com.rothrockware.studyjazzstandards.ui.today.TodayScreen
import com.rothrockware.studyjazzstandards.ui.today.TodayViewModel
import com.rothrockware.studyjazzstandards.ui.voicings.VoicingsScreen
import com.rothrockware.studyjazzstandards.ui.voicings.VoicingsViewModel

@Composable
fun App(container: AppContainer) {
    JazzTheme {
        val snackbarHost = remember { SnackbarHostState() }
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalSnackbarHost provides snackbarHost,
        ) {
            val repo = container.repository
            val nav: AppViewModel = viewModel { AppViewModel() }
            val db by repo.db.collectAsState()

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHost) },
                containerColor = JazzColors.Bg,
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    var showResetConfirm by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Jazz Standards Practice",
                            style = MaterialTheme.typography.titleMedium,
                            color = JazzColors.Gold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                        )
                        RowMenu(
                            listOf(
                                MenuAction("Reset all data", danger = true) { showResetConfirm = true },
                            ),
                        )
                    }
                    if (showResetConfirm) {
                        ConfirmDialog(
                            title = "Reset all data?",
                            text = "All progress, activity, repertoire, and voicings will be erased and the library re-seeded.",
                            confirmLabel = "Reset",
                            onConfirm = {
                                showResetConfirm = false
                                repo.resetAll()
                            },
                            onDismiss = { showResetConfirm = false },
                        )
                    }
                    TabBar(selected = nav.currentTab, onSelect = nav::selectTab)
                    HorizontalDivider(color = JazzColors.Border)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Box(Modifier.widthIn(max = 800.dp).align(Alignment.TopCenter)) {
                            when (val route = nav.route) {
                                is Route.TabRoute -> TabContent(route.tab, nav)
                                is Route.SongDetail -> {
                                    val vm: SongDetailViewModel = viewModel(key = "detail-${route.name}") {
                                        SongDetailViewModel(repo, route.name)
                                    }
                                    SongDetailScreen(vm, onBack = { nav.back() })
                                }
                            }
                        }
                    }
                }
            }

            if (!db.isOnboardingComplete) {
                val vm: OnboardingViewModel = viewModel { OnboardingViewModel(repo) }
                OnboardingFlow(vm)
            }
        }
    }
}

@Composable
private fun TabBar(selected: NavTab, onSelect: (NavTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JazzColors.Bg2)
            .horizontalScroll(rememberScrollState()),
    ) {
        NavTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        tab.title,
                        fontSize = 13.sp,
                        color = if (tab == selected) JazzColors.Gold else JazzColors.Text2,
                    )
                },
            )
        }
    }
}

@Composable
private fun TabContent(tab: NavTab, nav: AppViewModel) {
    val repo = LocalAppContainer.current.repository
    when (tab) {
        NavTab.Today -> {
            val vm: TodayViewModel = viewModel { TodayViewModel(repo) }
            TodayScreen(
                vm,
                onOpenDetail = nav::openSongDetail,
                onOpenVoicings = { nav.selectTab(NavTab.Voicings) },
            )
        }
        NavTab.Library -> {
            val vm: LibraryViewModel = viewModel { LibraryViewModel(repo) }
            LibraryScreen(vm, onOpenDetail = nav::openSongDetail)
        }
        NavTab.Repertoire -> {
            val vm: RepertoireViewModel = viewModel { RepertoireViewModel(repo) }
            RepertoireScreen(vm)
        }
        NavTab.Voicings -> {
            val vm: VoicingsViewModel = viewModel { VoicingsViewModel(repo) }
            VoicingsScreen(vm)
        }
        NavTab.Queue -> {
            val vm: QueueViewModel = viewModel { QueueViewModel(repo) }
            QueueScreen(vm, onOpenDetail = nav::openSongDetail)
        }
        NavTab.Progress -> {
            val vm: ProgressViewModel = viewModel { ProgressViewModel(repo) }
            ProgressScreen(vm, onOpenDetail = nav::openSongDetail)
        }
        NavTab.Activity -> {
            val vm: ActivityViewModel = viewModel { ActivityViewModel(repo) }
            ActivityScreen(vm)
        }
    }
}
