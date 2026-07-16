package com.rothrockware.studyjazzstandards.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {

    @Test
    fun streakIncrementsOnConsecutiveDaysAndResetsAfterGap() {
        val env = testEnv(seedJson = craftedDbJson(songs = listOf(activeSong("Song A", nextReview = TODAY))))

        env.repo.markPracticed("Song A")
        assertEquals(1, env.db.streak)
        assertEquals(TODAY, env.db.lastPracticeDay)

        // Same day: second action doesn't change streak
        env.repo.doReview("Song A", passed = true)
        assertEquals(1, env.db.streak)

        // Next day
        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        env.repo.markPracticed("Song A")
        assertEquals(2, env.db.streak)

        // Skip a day: reset to 1
        env.clock.todayInt = DateInt.addDays(TODAY, 3)
        env.repo.markPracticed("Song A")
        assertEquals(1, env.db.streak)
    }

    @Test
    fun voicingPracticeCountsTowardStreak() {
        val env = testEnv()
        env.repo.voicingPracticed("Major 7")
        assertEquals(1, env.db.streak)
        assertEquals(TODAY, env.db.lastPracticeDay)
    }
}
