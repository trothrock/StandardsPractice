package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityType
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongCrudTest {

    @Test
    fun addSongAtLowLevelGoesToBacklog() {
        val env = testEnv()
        assertIs<AddResult.Ok>(env.repo.addSong("My New Tune", "Bop", 1))
        val sg = env.song("My New Tune")
        assertEquals(SongStatus.UNTOUCHED, sg.status)
        assertEquals(1, sg.baseLevel)
        assertEquals(1, sg.currentLevel)
        assertEquals(-1, sg.intervalIdx)
        assertNull(sg.nextReview)
        assertNull(sg.learnedDate)
        assertNull(sg.composer, "dialog-added songs have no composer")
        assertTrue(sg.sortSeed != 0.0, "sortSeed assigned at creation")
        val entry = env.db.activity.last()
        assertEquals(ActivityType.SONG_ADDED, entry.type)
        assertEquals("Bop", entry.style)
        assertEquals(1, entry.level)
    }

    @Test
    fun addSongAtLevelTwoEntersReviewQueue() {
        val env = testEnv()
        env.repo.addSong("Ready Tune", "", 2)
        val sg = env.song("Ready Tune")
        assertEquals(SongStatus.ACTIVE, sg.status)
        assertEquals(0, sg.intervalIdx)
        assertEquals(DateInt.addDays(TODAY, 1), sg.nextReview)
        assertEquals(TODAY, sg.learnedDate)
    }

    @Test
    fun duplicateNamesAreRejectedCaseInsensitively() {
        val env = testEnv()
        assertIs<AddResult.DuplicateName>(env.repo.addSong("sTELLA by sTARLIGHT", "", 0))
        assertEquals(272, env.db.songs.size)
    }

    @Test
    fun renameRekeysMapAndPreservesSchedule() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(activeSong("Old Name", intervalIdx = 3, nextReview = 20260801)),
            ),
        )
        assertIs<AddResult.Ok>(env.repo.editSong("Old Name", "New Name", "Modal", 1))
        assertNull(env.db.songs["Old Name"])
        val sg = env.song("New Name")
        assertEquals("New Name", sg.name)
        assertEquals("Modal", sg.style)
        assertEquals(1, sg.baseLevel)
        // Schedule and progress untouched by edit
        assertEquals(SongStatus.ACTIVE, sg.status)
        assertEquals(3, sg.intervalIdx)
        assertEquals(20260801, sg.nextReview)
        assertEquals(2, sg.currentLevel)
        val entry = env.db.activity.last()
        assertEquals(ActivityType.SONG_EDITED, entry.type)
        assertEquals("Old Name", entry.oldName)
        assertEquals("New Name", entry.name)
        assertEquals(2, entry.oldLevel)
        assertEquals(1, entry.level)
    }

    @Test
    fun editDoesNotApplyLevelStatusMapping() {
        // Raising baseLevel to 2 via edit must NOT activate the song (web parity)
        val env = testEnv()
        env.repo.editSong("Stella by Starlight", "Stella by Starlight", "", 3)
        val sg = env.song("Stella by Starlight")
        assertEquals(3, sg.baseLevel)
        assertEquals(0, sg.currentLevel)
        assertEquals(SongStatus.UNTOUCHED, sg.status)
        assertEquals(-1, sg.intervalIdx)
        assertNull(sg.nextReview)
    }

    @Test
    fun renameUpdatesCurrentNewSong() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        env.repo.editSong("Stella by Starlight", "Stella Renamed", "", 0)
        assertEquals("Stella Renamed", env.db.currentNewSong)
    }

    @Test
    fun renameToExistingNameIsRejected() {
        val env = testEnv()
        assertIs<AddResult.DuplicateName>(
            env.repo.editSong("Stella by Starlight", "body AND soul", "", 0),
        )
    }

    @Test
    fun removeSongClearsCurrentNewSong() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        env.repo.removeSong("Stella by Starlight")
        assertNull(env.db.songs["Stella by Starlight"])
        assertNull(env.db.currentNewSong)
        assertEquals(ActivityType.SONG_REMOVED, env.db.activity.last().type)
    }

    @Test
    fun nextSuggestedFollowsPriorityOrder() {
        val env = testEnv()
        assertEquals("Anthropology", env.repo.nextSuggestedSong()?.name)
        env.repo.startLearning("Anthropology")
        assertEquals("Blues for Alice", env.repo.nextSuggestedSong()?.name)
    }

    @Test
    fun nextSuggestedFallsBackToAnyUntouched() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("Active One"),
                    activeSong("Backlog Tune", status = SongStatus.UNTOUCHED, nextReview = null, intervalIdx = -1),
                ),
            ),
        )
        assertEquals("Backlog Tune", env.repo.nextSuggestedSong()?.name)
    }
}
