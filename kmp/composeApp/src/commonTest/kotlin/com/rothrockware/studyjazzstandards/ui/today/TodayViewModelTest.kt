package com.rothrockware.studyjazzstandards.ui.today

import com.rothrockware.studyjazzstandards.data.DateInt
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.ui.VM_TODAY
import com.rothrockware.studyjazzstandards.ui.activeSong
import com.rothrockware.studyjazzstandards.ui.craftedDbJson
import com.rothrockware.studyjazzstandards.ui.vmTestEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initNormalizesQueueAndBuildsState() {
        // Three due songs, reviewMax 1 -> only the most urgent stays due
        val env = vmTestEnv(
            seedJson = craftedDbJson(
                listOf(
                    activeSong("Urgent", intervalIdx = 0, sortSeed = 0.1),
                    activeSong("Middle", intervalIdx = 1, sortSeed = 0.2),
                    activeSong("Calm", intervalIdx = 2, sortSeed = 0.3),
                ),
            ),
        )
        val vm = TodayViewModel(env.repo)
        val state = vm.state.value
        assertEquals(listOf("Urgent"), state.due.map { it.name })
        assertTrue(state.hasUpcoming)
        assertFalse(state.reviewedToday)
        assertEquals(1, state.reviewMax)
    }

    @Test
    fun learningCardShowsCurrentSongAndSuggestionOtherwise() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        assertNull(vm.state.value.learning)
        assertEquals("Anthropology", vm.state.value.suggested?.name)

        vm.startLearning("Anthropology")
        val learning = vm.state.value.learning
        assertNotNull(learning)
        assertEquals("Anthropology", learning.song.name)
        assertFalse(learning.practicedToday)
        assertNull(vm.state.value.suggested)

        vm.markPracticed("Anthropology")
        assertTrue(vm.state.value.learning!!.practicedToday)
        assertEquals(1, vm.state.value.streak)
    }

    @Test
    fun switchingSongsGoesThroughConfirmation() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        vm.startLearning("Anthropology")
        vm.startLearning("Misty")
        val pending = vm.pendingSwitch
        assertNotNull(pending)
        assertEquals("Anthropology", pending.currentName)
        assertEquals("Misty", pending.newName)
        // Still learning the old song until confirmed
        assertEquals("Anthropology", vm.state.value.learning?.song?.name)

        vm.confirmSwitch()
        assertNull(vm.pendingSwitch)
        assertEquals("Misty", vm.state.value.learning?.song?.name)
        assertEquals(
            SongStatus.UNTOUCHED,
            env.repo.db.value.songs.getValue("Anthropology").status,
        )
    }

    @Test
    fun cancelSwitchKeepsCurrentSong() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        vm.startLearning("Anthropology")
        vm.startLearning("Misty")
        vm.cancelSwitch()
        assertNull(vm.pendingSwitch)
        assertEquals("Anthropology", vm.state.value.learning?.song?.name)
    }

    @Test
    fun markLearnedClearsLearningCard() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        vm.startLearning("Anthropology")
        vm.markLearned("Anthropology")
        assertNull(vm.state.value.learning)
        val sg = env.repo.db.value.songs.getValue("Anthropology")
        assertEquals(SongStatus.ACTIVE, sg.status)
    }

    @Test
    fun reviewRemovesSongFromDueListAndEmitsToast() = runTest {
        val env = vmTestEnv(
            seedJson = craftedDbJson(listOf(activeSong("Song A", intervalIdx = 0))),
        )
        val vm = TodayViewModel(env.repo)
        assertEquals(1, vm.state.value.due.size)
        vm.doReview("Song A", passed = true)
        assertTrue(vm.state.value.due.isEmpty())
        assertTrue(vm.state.value.reviewedToday)
        assertEquals("Reviewed — next review in 3 days", vm.toasts.first())
    }

    @Test
    fun reviewAnotherPullsUpcomingSongIn() {
        val env = vmTestEnv(
            seedJson = craftedDbJson(
                listOf(activeSong("Later", intervalIdx = 0, nextReview = DateInt.addDays(VM_TODAY, 3))),
            ),
        )
        val vm = TodayViewModel(env.repo)
        // Queue normalization already pulled it forward (no reviews yet today) —
        // review it, then ask for another when none remain.
        assertEquals(listOf("Later"), vm.state.value.due.map { it.name })
        vm.doReview("Later", passed = true)
        vm.reviewAnother()
        assertTrue(vm.state.value.due.isEmpty(), "nothing left to pull")
    }

    @Test
    fun featuredRepertoireFlowsThroughActions() {
        val env = vmTestEnv()
        env.repo.addRepertoire("My Piece", "Me", null)
        val vm = TodayViewModel(env.repo)
        assertEquals("My Piece", vm.state.value.featuredRepertoire?.name)
        assertFalse(vm.state.value.repertoireDoneToday)

        vm.markRepertoireReviewed("My Piece")
        assertTrue(vm.state.value.repertoireDoneToday)
    }

    @Test
    fun featuredVoicingPracticeRotatesToNextCandidate() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        val voicing = vm.state.value.featuredVoicing
        assertNotNull(voicing, "fresh seed has unfamiliar voicings")
        assertFalse(vm.state.value.voicingDoneToday)
        vm.voicingPracticed(voicing.name)
        // Familiarity incremented, so (web parity) the featured slot recomputes
        // to another least-familiar voicing that hasn't been practiced today.
        assertEquals(1, env.repo.db.value.voicings.first { it.name == voicing.name }.familiarity)
        val next = vm.state.value.featuredVoicing
        assertNotNull(next)
        assertTrue(next.name != voicing.name)
        assertFalse(vm.state.value.voicingDoneToday)
    }

    @Test
    fun setReviewMaxUpdatesStateAndClosesDialog() {
        val env = vmTestEnv()
        val vm = TodayViewModel(env.repo)
        vm.showReviewSettings = true
        vm.setReviewMax(5)
        assertEquals(5, vm.state.value.reviewMax)
        assertFalse(vm.showReviewSettings)
    }
}
