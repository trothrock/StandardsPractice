package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityType
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningLifecycleTest {

    @Test
    fun startLearningSetsStatusAndCurrentSong() {
        val env = testEnv()
        val result = env.repo.startLearning("Stella by Starlight")
        assertIs<StartLearningResult.Started>(result)
        assertEquals(SongStatus.LEARNING, env.song("Stella by Starlight").status)
        assertEquals("Stella by Starlight", env.db.currentNewSong)
        assertEquals(ActivityType.STARTED_LEARNING, env.db.activity.last().type)
        assertEquals("Stella by Starlight", env.db.activity.last().name)
    }

    @Test
    fun switchingSongsRequiresConfirmation() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        val result = env.repo.startLearning("Body and Soul")
        assertIs<StartLearningResult.NeedsConfirmation>(result)
        assertEquals("Stella by Starlight", result.currentName)
        // Nothing changed without confirmation
        assertEquals("Stella by Starlight", env.db.currentNewSong)
        assertEquals(SongStatus.UNTOUCHED, env.song("Body and Soul").status)
    }

    @Test
    fun confirmedSwitchRevertsOldSongToUntouched() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        val result = env.repo.startLearning("Body and Soul", confirmSwitch = true)
        assertIs<StartLearningResult.Started>(result)
        assertEquals(SongStatus.UNTOUCHED, env.song("Stella by Starlight").status)
        assertEquals(SongStatus.LEARNING, env.song("Body and Soul").status)
        assertEquals("Body and Soul", env.db.currentNewSong)
        val types = env.db.activity.map { it.type }
        assertEquals(
            listOf(ActivityType.STARTED_LEARNING, ActivityType.STOPPED_LEARNING, ActivityType.STARTED_LEARNING),
            types,
        )
        assertEquals("Stella by Starlight", env.db.activity[1].name)
    }

    @Test
    fun restartingSameSongDoesNotNeedConfirmation() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        val result = env.repo.startLearning("Stella by Starlight")
        assertIs<StartLearningResult.Started>(result)
    }

    @Test
    fun markPracticedIsOncePerDay() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        env.repo.markPracticed("Stella by Starlight")
        assertTrue(env.repo.practicedToday("Stella by Starlight"))
        env.repo.markPracticed("Stella by Starlight")
        assertEquals(1, env.db.activity.count { it.type == ActivityType.PRACTICED })
        assertEquals(1, env.db.streak)

        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        assertFalse(env.repo.practicedToday("Stella by Starlight"))
        env.repo.markPracticed("Stella by Starlight")
        assertEquals(2, env.db.activity.count { it.type == ActivityType.PRACTICED })
        assertEquals(2, env.db.streak)
    }

    @Test
    fun markLearnedActivatesSongAndSchedulesReview() {
        val env = testEnv()
        env.repo.startLearning("Stella by Starlight")
        env.repo.markLearned("Stella by Starlight")
        val sg = env.song("Stella by Starlight")
        assertEquals(SongStatus.ACTIVE, sg.status)
        assertEquals(2, sg.currentLevel)
        assertEquals(0, sg.intervalIdx)
        assertEquals(TODAY, sg.learnedDate)
        assertEquals(DateInt.addDays(TODAY, 1), sg.nextReview)
        assertNull(env.db.currentNewSong)
        assertEquals(ActivityType.LEARNED, env.db.activity.last().type)
        assertEquals(1, env.db.streak)
    }

    @Test
    fun markLearnedKeepsHigherCurrentLevel() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(activeSong("Graduated Song", status = SongStatus.LEARNING).copy(currentLevel = 3)),
            ),
        )
        env.repo.markLearned("Graduated Song")
        assertEquals(3, env.song("Graduated Song").currentLevel)
    }
}
