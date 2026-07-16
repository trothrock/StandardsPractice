package com.rothrockware.studyjazzstandards.ui.onboarding

import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.model.isOnboardingComplete
import com.rothrockware.studyjazzstandards.data.seed.ALL_SONGS
import com.rothrockware.studyjazzstandards.ui.vmTestEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsAtWelcomeThenWalksAllSongs() {
        val env = vmTestEnv()
        val vm = OnboardingViewModel(env.repo)
        assertEquals(OnboardingStage.Welcome, vm.stage)

        vm.startRanker()
        assertEquals(OnboardingStage.Ranker, vm.stage)
        assertEquals(0, vm.index)
        assertEquals(ALL_SONGS[0].title, vm.currentSong.title)
        assertTrue(vm.isFirst)
        assertFalse(vm.isLast)
    }

    @Test
    fun backIsBoundedAtZero() {
        val env = vmTestEnv()
        val vm = OnboardingViewModel(env.repo)
        vm.startRanker()
        vm.back()
        assertEquals(0, vm.index)
        vm.next()
        assertEquals(1, vm.index)
        vm.back()
        assertEquals(0, vm.index)
    }

    @Test
    fun nextOnLastSongFinishes() {
        val env = vmTestEnv()
        val vm = OnboardingViewModel(env.repo)
        vm.startRanker()
        repeat(ALL_SONGS.size - 1) { vm.next() }
        assertTrue(vm.isLast)
        vm.setLevel(2)
        vm.next() // finishes
        assertTrue(env.repo.db.value.isOnboardingComplete)
        val lastSong = env.repo.db.value.songs.getValue(ALL_SONGS.last().title)
        assertEquals(2, lastSong.baseLevel)
        assertEquals(SongStatus.ACTIVE, lastSong.status)
    }

    @Test
    fun finishAnytimeAppliesRatedLevels() {
        val env = vmTestEnv()
        val vm = OnboardingViewModel(env.repo)
        vm.startRanker()
        vm.setLevel(3)
        vm.next()
        vm.setLevel(1)
        vm.finish()
        assertTrue(env.repo.db.value.isOnboardingComplete)
        assertEquals(3, env.repo.db.value.songs.getValue(ALL_SONGS[0].title).baseLevel)
        assertEquals(1, env.repo.db.value.songs.getValue(ALL_SONGS[1].title).baseLevel)
        assertEquals(0, env.repo.db.value.songs.getValue(ALL_SONGS[2].title).baseLevel)
    }

    @Test
    fun skipCompletesWithoutRating() {
        val env = vmTestEnv()
        val vm = OnboardingViewModel(env.repo)
        vm.skip()
        assertTrue(env.repo.db.value.isOnboardingComplete)
        assertTrue(env.repo.db.value.songs.values.all { it.baseLevel == 0 })
    }

    @Test
    fun reRankInitializesFromExistingBaseLevels() {
        val env = vmTestEnv()
        env.repo.completeOnboarding(mapOf(ALL_SONGS[0].title to 2))
        val vm = OnboardingViewModel(env.repo)
        vm.startRanker()
        assertEquals(2, vm.currentLevel, "ranker shows previously saved level")
    }
}
