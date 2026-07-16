package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.model.isOnboardingComplete
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun freshDbNeedsOnboarding() {
        val env = testEnv()
        assertFalse(env.db.isOnboardingComplete)
        env.repo.skipOnboarding()
        assertTrue(env.db.isOnboardingComplete)
    }

    @Test
    fun completeOnboardingAppliesLevelMapping() {
        val env = testEnv()
        env.repo.completeOnboarding(
            mapOf(
                "Stella by Starlight" to 2,
                "Body and Soul" to 1,
                "Nature Boy" to 0,
            ),
        )
        assertTrue(env.db.isOnboardingComplete)

        val stella = env.song("Stella by Starlight")
        assertEquals(2, stella.baseLevel)
        assertEquals(2, stella.currentLevel)
        assertEquals(SongStatus.ACTIVE, stella.status)
        assertEquals(0, stella.intervalIdx)
        assertEquals(DateInt.addDays(TODAY, 1), stella.nextReview)
        assertEquals(TODAY, stella.learnedDate)

        val body = env.song("Body and Soul")
        assertEquals(1, body.baseLevel)
        assertEquals(SongStatus.UNTOUCHED, body.status)
        assertEquals(-1, body.intervalIdx)
        assertNull(body.nextReview)
        assertNull(body.learnedDate)
    }

    @Test
    fun reRankPreservesExistingLearnedDate() {
        val env = testEnv(
            seedJson = craftedDbJson(songs = listOf(activeSong("Song A").copy(learnedDate = 20260101))),
        )
        env.repo.completeOnboarding(mapOf("Song A" to 3))
        val sg = env.song("Song A")
        assertEquals(20260101, sg.learnedDate, "existing learnedDate preserved")
        assertEquals(3, sg.currentLevel)
        assertEquals(SongStatus.ACTIVE, sg.status)
        assertEquals(0, sg.intervalIdx, "re-rank resets scheduling")
        assertEquals(DateInt.addDays(TODAY, 1), sg.nextReview)
    }

    @Test
    fun reRankBelowTwoDeactivatesSong() {
        val env = testEnv(
            seedJson = craftedDbJson(songs = listOf(activeSong("Song A", intervalIdx = 4))),
        )
        env.repo.completeOnboarding(mapOf("Song A" to 1))
        val sg = env.song("Song A")
        assertEquals(SongStatus.UNTOUCHED, sg.status)
        assertEquals(-1, sg.intervalIdx)
        assertNull(sg.nextReview)
        assertNull(sg.learnedDate)
        assertEquals(1, sg.currentLevel)
    }

    @Test
    fun unknownNamesAreIgnored() {
        val env = testEnv()
        env.repo.completeOnboarding(mapOf("Does Not Exist" to 3))
        assertNull(env.db.songs["Does Not Exist"])
        assertTrue(env.db.isOnboardingComplete)
    }
}
