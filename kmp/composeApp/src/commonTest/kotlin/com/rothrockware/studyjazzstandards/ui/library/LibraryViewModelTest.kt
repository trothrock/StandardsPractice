package com.rothrockware.studyjazzstandards.ui.library

import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.ui.vmTestEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listsAllSongsAlphabetically() {
        val env = vmTestEnv()
        val vm = LibraryViewModel(env.repo)
        val songs = vm.songs.value
        assertEquals(272, songs.size)
        assertEquals(songs.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER), songs.map { it.name })
    }

    @Test
    fun queryFilterIsCaseInsensitiveSubstring() {
        val env = vmTestEnv()
        val vm = LibraryViewModel(env.repo)
        vm.setQuery("sTELLA")
        assertEquals(listOf("Stella by Starlight"), vm.songs.value.map { it.name })
    }

    @Test
    fun levelFilterMatchesBaseLevelNotCurrentLevel() {
        val env = vmTestEnv()
        // markLearned raises currentLevel to 2 but baseLevel stays 0
        env.repo.startLearning("Misty")
        env.repo.markLearned("Misty")
        val vm = LibraryViewModel(env.repo)

        vm.setLevel(0)
        assertTrue(vm.songs.value.any { it.name == "Misty" }, "baseLevel 0 song stays in level-0 filter")

        vm.setLevel(2)
        assertTrue(vm.songs.value.none { it.name == "Misty" })
    }

    @Test
    fun statusAndStyleFiltersCombine() {
        val env = vmTestEnv()
        env.repo.startLearning("Rhythm-a-ning") // a Bop tune
        val vm = LibraryViewModel(env.repo)
        vm.setStyle("Bop")
        vm.setStatus(SongStatus.LEARNING)
        assertEquals(listOf("Rhythm-a-ning"), vm.songs.value.map { it.name })

        vm.setStatus(null)
        assertTrue(vm.songs.value.size > 1, "all Bop songs return when status cleared")
        assertTrue(vm.songs.value.all { it.style == "Bop" })
    }

    @Test
    fun userAddedSongsAppearInList() {
        val env = vmTestEnv()
        env.repo.addSong("AAA Custom Tune", "Modal", 1)
        val vm = LibraryViewModel(env.repo)
        assertEquals(273, vm.songs.value.size)
        assertTrue(vm.songs.value.any { it.name == "AAA Custom Tune" })
        // Alphabetical placement, not appended at the end
        assertTrue(
            vm.songs.value.indexOfFirst { it.name == "AAA Custom Tune" } <
                vm.songs.value.indexOfFirst { it.name == "Misty" },
        )
    }

    @Test
    fun addDialogRejectsDuplicatesWithError() {
        val env = vmTestEnv()
        val vm = LibraryViewModel(env.repo)
        vm.openAdd()
        vm.updateAdd { it.copy(name = "Misty") }
        vm.submitAdd()
        val d = vm.dialog
        assertIs<SongDialog.Add>(d)
        assertTrue(d.showError)
        assertEquals(272, vm.songs.value.size)
    }

    @Test
    fun editDialogRenamesSong() {
        val env = vmTestEnv()
        val vm = LibraryViewModel(env.repo)
        vm.openEdit("Misty")
        val d = vm.dialog
        assertIs<SongDialog.Edit>(d)
        assertEquals("Misty", d.name)
        vm.updateEdit { it.copy(name = "Misty (Garner)", level = 2) }
        vm.submitEdit()
        assertEquals(null, vm.dialog)
        val sg = env.repo.db.value.songs["Misty (Garner)"]
        assertNotNull(sg)
        assertEquals(2, sg.baseLevel)
        // Edit never activates a song (web parity)
        assertEquals(SongStatus.UNTOUCHED, sg.status)
    }

    @Test
    fun removeDialogRemovesSong() {
        val env = vmTestEnv()
        val vm = LibraryViewModel(env.repo)
        vm.openRemove("Misty")
        vm.confirmRemove()
        assertEquals(271, vm.songs.value.size)
        assertTrue(vm.songs.value.none { it.name == "Misty" })
    }
}
