package com.rothrockware.studyjazzstandards.ui.progress

import com.rothrockware.studyjazzstandards.data.seed.PRIORITY_ORDER
import com.rothrockware.studyjazzstandards.ui.vmTestEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshSeedIsAllUnstarted() {
        val env = vmTestEnv()
        val vm = ProgressViewModel(env.repo)
        val s = vm.state.value
        assertEquals(272, s.notStarted)
        assertEquals(0, s.inProgress)
        assertEquals(0, s.atLevelTwoPlus)
        assertEquals(272, s.total)
        assertEquals(0, s.percent)
        assertEquals(PRIORITY_ORDER.take(12), s.nextSongs.map { it.name })
    }

    @Test
    fun statsTrackLearningAndGraduation() {
        val env = vmTestEnv()
        env.repo.startLearning("Anthropology")
        env.repo.markLearned("Anthropology") // active, currentLevel 2
        env.repo.startLearning("Misty")      // learning
        val vm = ProgressViewModel(env.repo)
        val s = vm.state.value
        assertEquals(270, s.notStarted)
        assertEquals(2, s.inProgress)
        assertEquals(1, s.atLevelTwoPlus)
        // 1/272 rounds to 0%
        assertEquals(0, s.percent)
        // Started songs drop out of the next-to-learn list
        assertEquals(PRIORITY_ORDER.filter { it != "Anthropology" && it != "Misty" }.take(12), s.nextSongs.map { it.name })
    }

    @Test
    fun percentRounds() {
        val env = vmTestEnv()
        val vm = ProgressViewModel(env.repo)
        // Rate 136 songs at level 2 => 50%
        env.repo.completeOnboarding(
            env.repo.db.value.songs.keys.take(136).associateWith { 2 },
        )
        assertEquals(50, vm.state.value.percent)
    }
}
