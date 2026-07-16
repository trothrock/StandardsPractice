package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.model.Voicing
import com.rothrockware.studyjazzstandards.data.model.isOnboardingComplete
import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InitAndMigrationTest {

    @Test
    fun freshDbSeedsAllSongsAndVoicings() {
        val env = testEnv()
        val db = env.db
        assertEquals(272, db.songs.size)
        assertEquals(15, db.voicings.size)
        assertEquals(VOICINGS_SEED_VERSION, db.voicingsSeedVersion)
        assertEquals(false, db.onboardingComplete)
        assertFalse(db.isOnboardingComplete)
        assertEquals(1, db.reviewMax)
        assertEquals(0, db.streak)
        val stella = env.song("Stella by Starlight")
        assertEquals("Victor Young", stella.composer)
        assertEquals(1944, stella.year)
        assertEquals(SongStatus.UNTOUCHED, stella.status)
        assertEquals(-1, stella.intervalIdx)
        assertTrue(db.songs.values.all { it.sortSeed != 0.0 })
        assertNotNull(env.store.read(), "fresh db must be persisted")
    }

    @Test
    fun corruptBlobFallsBackToFreshSeed() {
        val env = testEnv(seedJson = "{definitely not json")
        assertEquals(272, env.db.songs.size)
        assertEquals(false, env.db.onboardingComplete)
    }

    @Test
    fun legacyWebBackupIsMigrated() {
        // Shape of a real pre-voicings web backup (see web/backups/jazz-tracker-2026-04-19.json):
        // no composer/year on songs, no repertoire/voicings/onboardingComplete/reviewMax keys,
        // explicit nulls written by JSON.stringify.
        val legacy = """
            {"songs":{"Stella by Starlight":{"name":"Stella by Starlight","baseLevel":2,"currentLevel":2,"style":"","status":"active","intervalIdx":1,"nextReview":20260508,"learnedDate":20260419,"reviews":[{"date":20260425,"passed":true}],"sortSeed":0.653581928917298},"A Child Is Born":{"name":"A Child Is Born","baseLevel":0,"currentLevel":0,"style":"","status":"untouched","intervalIdx":-1,"nextReview":null,"learnedDate":null,"reviews":[],"sortSeed":0.0253985339997036}},"currentNewSong":"Anthropology","streak":1,"lastPracticeDay":20260419,"activity":[{"ts":1776634938073,"date":20260419,"type":"practiced","name":"Anthropology"}]}
        """.trimIndent()
        val env = testEnv(seedJson = legacy)
        val db = env.db

        // onboardingComplete backfilled to TRUE for existing DBs
        assertEquals(true, db.onboardingComplete)
        // composer/year backfilled from seed data
        assertEquals("Victor Young", env.song("Stella by Starlight").composer)
        assertEquals(1944, env.song("Stella by Starlight").year)
        // voicings seeded via version migration
        assertEquals(15, db.voicings.size)
        assertEquals(VOICINGS_SEED_VERSION, db.voicingsSeedVersion)
        // existing scheduling untouched
        assertEquals(1, env.song("Stella by Starlight").intervalIdx)
        assertEquals(20260508, env.song("Stella by Starlight").nextReview)
        assertEquals("Anthropology", db.currentNewSong)
        assertEquals(1, db.activity.size)
        // migration result persisted
        val persisted = JazzJson.decodeFromString<JazzDb>(env.store.read()!!)
        assertEquals(true, persisted.onboardingComplete)
    }

    @Test
    fun voicingSeedMergeKeepsFamiliarityImageAndCustoms() {
        val old = JazzDb(
            onboardingComplete = true,
            voicingsSeedVersion = 1,
            voicings = listOf(
                Voicing(name = "Major 7 (drop 2)", category = "Major", topNote = "7", image = "maj7.png", familiarity = 2, addedDate = 20250101, sortSeed = 0.1),
                Voicing(name = "My Custom Thing (rootless)", category = "Dominant", topNote = "♭9", familiarity = 1, addedDate = 20250202, sortSeed = 0.2),
            ),
        )
        val env = testEnv(seedJson = craftedDbJson(db = old))
        val db = env.db
        assertEquals(16, db.voicings.size, "15 seed + 1 custom")
        val maj7 = db.voicings.first { it.name == "Major 7" }
        assertEquals(2, maj7.familiarity)
        assertEquals("maj7.png", maj7.image)
        val custom = db.voicings.first { it.name == "My Custom Thing" }
        assertEquals(1, custom.familiarity)
        assertEquals("♭9", custom.topNote)
        assertNull(db.voicings.firstOrNull { it.name.contains("(") })
    }

    @Test
    fun resetAllReseedsFromScratch() {
        val env = testEnv()
        env.repo.addSong("Extra Song", "Bop", 0)
        assertEquals(273, env.db.songs.size)
        env.repo.resetAll()
        assertEquals(272, env.db.songs.size)
        assertEquals(false, env.db.onboardingComplete)
        assertTrue(env.db.activity.isEmpty())
    }
}
