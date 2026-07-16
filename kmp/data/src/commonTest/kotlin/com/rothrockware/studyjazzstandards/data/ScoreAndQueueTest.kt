package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ExtraReviews
import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.Review
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreAndQueueTest {

    @Test
    fun scoreCombinesOverdueFragilityAndFailRate() {
        val env = testEnv()
        // overdue 4 days * 0.5 = 2; fragility (5-1)*10 = 40; failRate 2/5 * 20 = 8
        val sg = activeSong(
            "Song A",
            intervalIdx = 1,
            nextReview = DateInt.addDays(TODAY, -4),
            reviews = listOf(
                Review(20260601, true), Review(20260605, false), Review(20260608, true),
                Review(20260610, false), Review(20260612, true),
            ),
        )
        assertEquals(50.0, env.repo.scoreSong(sg, TODAY))
    }

    @Test
    fun scoreUsesOnlyLastFiveReviews() {
        val env = testEnv()
        // 6 reviews, first is a fail that falls out of the 5-review window
        val reviews = listOf(Review(20260601, false)) + (2..6).map { Review(20260600 + it, true) }
        val sg = activeSong("Song A", intervalIdx = 0, nextReview = TODAY, reviews = reviews)
        // overdue 0; fragility 5*10 = 50; failRate 0
        assertEquals(50.0, env.repo.scoreSong(sg, TODAY))
    }

    @Test
    fun negativeIntervalIdxClampedInFragility() {
        val env = testEnv()
        val sg = activeSong("Song A", intervalIdx = -1, nextReview = TODAY)
        assertEquals(50.0, env.repo.scoreSong(sg, TODAY))
    }

    @Test
    fun overflowDueSongsAreStaggeredForward() {
        // reviewMax 1; three due songs with descending urgency by intervalIdx
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("Urgent", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.1),
                    activeSong("Middle", intervalIdx = 1, nextReview = TODAY, sortSeed = 0.2),
                    activeSong("Calm", intervalIdx = 2, nextReview = TODAY, sortSeed = 0.3),
                ),
            ),
        )
        val changed = env.repo.normalizeReviewQueue()
        assertTrue(changed)
        assertEquals(TODAY, env.song("Urgent").nextReview)
        assertEquals(DateInt.addDays(TODAY, 1), env.song("Middle").nextReview)
        assertEquals(DateInt.addDays(TODAY, 2), env.song("Calm").nextReview)
        assertEquals(listOf("Urgent"), env.repo.dueReviews().map { it.name })
    }

    @Test
    fun underCapPullsUpcomingForward() {
        val env = testEnv(
            seedJson = craftedDbJson(
                db = JazzDb(
                    songs = listOf(
                        activeSong("Soon", intervalIdx = 0, nextReview = DateInt.addDays(TODAY, 3), sortSeed = 0.1),
                        activeSong("Later", intervalIdx = 1, nextReview = DateInt.addDays(TODAY, 5), sortSeed = 0.2),
                    ).associateBy { it.name },
                    onboardingComplete = true,
                    voicingsSeedVersion = VOICINGS_SEED_VERSION,
                    reviewMax = 2,
                ),
            ),
        )
        assertTrue(env.repo.normalizeReviewQueue())
        assertEquals(TODAY, env.song("Soon").nextReview)
        assertEquals(TODAY, env.song("Later").nextReview)
    }

    @Test
    fun noPullForwardAfterReviewingToday() {
        val env = testEnv(
            seedJson = craftedDbJson(
                db = JazzDb(
                    songs = listOf(
                        activeSong(
                            "Done", intervalIdx = 1, nextReview = DateInt.addDays(TODAY, 3),
                            reviews = listOf(Review(TODAY, true)), sortSeed = 0.1,
                        ),
                        activeSong("Upcoming", intervalIdx = 0, nextReview = DateInt.addDays(TODAY, 2), sortSeed = 0.2),
                    ).associateBy { it.name },
                    onboardingComplete = true,
                    voicingsSeedVersion = VOICINGS_SEED_VERSION,
                    reviewMax = 2,
                ),
            ),
        )
        assertFalse(env.repo.normalizeReviewQueue())
        assertEquals(DateInt.addDays(TODAY, 2), env.song("Upcoming").nextReview)
    }

    @Test
    fun capIncludesExtraReviewsGrantedToday() {
        val env = testEnv(
            seedJson = craftedDbJson(
                db = JazzDb(
                    songs = listOf(
                        activeSong("A", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.1),
                        activeSong("B", intervalIdx = 1, nextReview = TODAY, sortSeed = 0.2),
                    ).associateBy { it.name },
                    onboardingComplete = true,
                    voicingsSeedVersion = VOICINGS_SEED_VERSION,
                    reviewMax = 1,
                    extraReviewsToday = ExtraReviews(TODAY, 1),
                ),
            ),
        )
        // max = 1 + 1 extra = 2, both due songs fit
        assertFalse(env.repo.normalizeReviewQueue())
        assertEquals(2, env.repo.dueReviews().size)
    }

    @Test
    fun staleExtraReviewsDoNotCount() {
        val env = testEnv(
            seedJson = craftedDbJson(
                db = JazzDb(
                    songs = listOf(
                        activeSong("A", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.1),
                        activeSong("B", intervalIdx = 1, nextReview = TODAY, sortSeed = 0.2),
                    ).associateBy { it.name },
                    onboardingComplete = true,
                    voicingsSeedVersion = VOICINGS_SEED_VERSION,
                    reviewMax = 1,
                    extraReviewsToday = ExtraReviews(DateInt.addDays(TODAY, -1), 3),
                ),
            ),
        )
        assertTrue(env.repo.normalizeReviewQueue())
        assertEquals(1, env.repo.dueReviews().size)
    }

    @Test
    fun overCapAfterLoweringReviewMaxPushesAllDueForward() {
        // Deviation from JS negative-slice: reviewedCount (2) > max (1) pushes ALL due songs.
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("R1", intervalIdx = 1, nextReview = DateInt.addDays(TODAY, 3), reviews = listOf(Review(TODAY, true)), sortSeed = 0.1),
                    activeSong("R2", intervalIdx = 1, nextReview = DateInt.addDays(TODAY, 3), reviews = listOf(Review(TODAY, true)), sortSeed = 0.2),
                    activeSong("Due", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.3),
                ),
            ),
        )
        assertTrue(env.repo.normalizeReviewQueue())
        assertEquals(DateInt.addDays(TODAY, 1), env.song("Due").nextReview)
        assertTrue(env.repo.dueReviews().isEmpty())
    }

    @Test
    fun sortSeedBreaksScoreTies() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("Zeta", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.9),
                    activeSong("Alpha", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.2),
                ),
            ),
        )
        // Equal scores: lower sortSeed first
        assertEquals(listOf("Alpha", "Zeta"), env.repo.dueReviews().map { it.name })
    }

    @Test
    fun reviewAnotherSongPullsHighestScoringUpcoming() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("Fragile", intervalIdx = 0, nextReview = DateInt.addDays(TODAY, 2), sortSeed = 0.1),
                    activeSong("Solid", intervalIdx = 3, nextReview = DateInt.addDays(TODAY, 2), sortSeed = 0.2),
                ),
            ),
        )
        assertTrue(env.repo.reviewAnotherSong())
        assertEquals(TODAY, env.song("Fragile").nextReview)
        assertEquals(ExtraReviews(TODAY, 1), env.db.extraReviewsToday)

        assertTrue(env.repo.reviewAnotherSong())
        assertEquals(TODAY, env.song("Solid").nextReview)
        assertEquals(ExtraReviews(TODAY, 2), env.db.extraReviewsToday)

        assertFalse(env.repo.reviewAnotherSong(), "no upcoming songs left")
    }

    @Test
    fun setReviewMaxRejectsBelowOne() {
        val env = testEnv()
        env.repo.setReviewMax(0)
        assertEquals(1, env.db.reviewMax)
        env.repo.setReviewMax(5)
        assertEquals(5, env.db.reviewMax)
    }

    @Test
    fun dueReviewsExcludesGraduatedAndReviewedToday() {
        val env = testEnv(
            seedJson = craftedDbJson(
                songs = listOf(
                    activeSong("Grad", intervalIdx = 5, nextReview = TODAY, status = SongStatus.GRADUATED, sortSeed = 0.1),
                    activeSong("DoneToday", intervalIdx = 0, nextReview = TODAY, reviews = listOf(Review(TODAY, true)), sortSeed = 0.2),
                    activeSong("Due", intervalIdx = 0, nextReview = TODAY, sortSeed = 0.3),
                ),
            ),
        )
        assertEquals(listOf("Due"), env.repo.dueReviews().map { it.name })
        assertTrue(env.repo.reviewedToday())
    }
}
