package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityType
import com.rothrockware.studyjazzstandards.data.model.Review
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewTest {

    private fun envWith(intervalIdx: Int) = testEnv(
        seedJson = craftedDbJson(songs = listOf(activeSong("Song A", intervalIdx = intervalIdx))),
    )

    @Test
    fun passAdvancesIntervalLadder() {
        // INTERVALS = [1,3,7,21,60,180]
        for ((idx, expectedDays) in listOf(0 to 3, 1 to 7, 2 to 21, 3 to 60)) {
            val env = envWith(idx)
            val outcome = env.repo.doReview("Song A", passed = true)
            assertIs<ReviewOutcome.NextInDays>(outcome)
            assertEquals(expectedDays, outcome.days)
            val sg = env.song("Song A")
            assertEquals(idx + 1, sg.intervalIdx)
            assertEquals(DateInt.addDays(TODAY, expectedDays), sg.nextReview)
            assertEquals(SongStatus.ACTIVE, sg.status)
            assertEquals(listOf(Review(TODAY, passed = true)), sg.reviews)
        }
    }

    @Test
    fun passAtIndexFourGraduates() {
        val env = envWith(4)
        val outcome = env.repo.doReview("Song A", passed = true)
        assertIs<ReviewOutcome.Graduated>(outcome)
        val sg = env.song("Song A")
        assertEquals(5, sg.intervalIdx)
        assertEquals(SongStatus.GRADUATED, sg.status)
        assertEquals(3, sg.currentLevel)
        assertEquals(DateInt.addDays(TODAY, 180), sg.nextReview)
    }

    @Test
    fun passAtMaxIndexStaysGraduated() {
        val env = envWith(5)
        val outcome = env.repo.doReview("Song A", passed = true)
        assertIs<ReviewOutcome.Graduated>(outcome)
        val sg = env.song("Song A")
        assertEquals(5, sg.intervalIdx)
        assertEquals(DateInt.addDays(TODAY, 180), sg.nextReview)
    }

    @Test
    fun failResetsToStartOfLadder() {
        val env = envWith(3)
        val outcome = env.repo.doReview("Song A", passed = false)
        assertIs<ReviewOutcome.BackTomorrow>(outcome)
        val sg = env.song("Song A")
        assertEquals(0, sg.intervalIdx)
        assertEquals(DateInt.addDays(TODAY, 1), sg.nextReview)
        assertEquals(SongStatus.ACTIVE, sg.status)
        assertEquals(listOf(Review(TODAY, passed = false)), sg.reviews)
    }

    @Test
    fun reviewLogsActivityAndUpdatesStreak() {
        val env = envWith(0)
        env.repo.doReview("Song A", passed = true)
        val entry = env.db.activity.last()
        assertEquals(ActivityType.REVIEW, entry.type)
        assertEquals("Song A", entry.name)
        assertEquals(true, entry.passed)
        assertEquals(1, env.db.streak)
        assertEquals(TODAY, env.db.lastPracticeDay)
    }

    @Test
    fun reviewOfUnknownSongIsNoOp() {
        val env = envWith(0)
        assertNull(env.repo.doReview("Nope", passed = true))
        assertTrue(env.db.activity.isEmpty())
    }
}
