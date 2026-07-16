package com.rothrockware.studyjazzstandards.ui.queue

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun env() = vmTestEnv(
        seedJson = craftedDbJson(
            listOf(
                activeSong("Charlie", nextReview = DateInt.addDays(VM_TODAY, 5)),
                activeSong("Alpha", nextReview = DateInt.addDays(VM_TODAY, 9)),
                activeSong("Bravo", nextReview = DateInt.addDays(VM_TODAY, 1)),
                activeSong("Grad", nextReview = null, status = SongStatus.GRADUATED, intervalIdx = 5, currentLevel = 3),
                activeSong("Backlog", nextReview = null, status = SongStatus.UNTOUCHED, intervalIdx = -1),
            ),
        ),
    )

    @Test
    fun includesOnlyActiveAndGraduated() {
        val vm = QueueViewModel(env().repo)
        assertEquals(setOf("Charlie", "Alpha", "Bravo", "Grad"), vm.songs.value.map { it.name }.toSet())
    }

    @Test
    fun sortsByDueDateWithMissingLast() {
        val vm = QueueViewModel(env().repo)
        assertEquals(listOf("Bravo", "Charlie", "Alpha", "Grad"), vm.songs.value.map { it.name })
    }

    @Test
    fun sortsByTitle() {
        val vm = QueueViewModel(env().repo)
        vm.sort.value = QueueSort.Title
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Grad"), vm.songs.value.map { it.name })
    }

    @Test
    fun searchFilters() {
        val vm = QueueViewModel(env().repo)
        vm.query.value = "RAV"
        assertEquals(listOf("Bravo"), vm.songs.value.map { it.name })
    }
}
