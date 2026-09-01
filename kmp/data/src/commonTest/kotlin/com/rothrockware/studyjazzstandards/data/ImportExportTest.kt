package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportTest {

    @Test
    fun exportedBackupRoundTripsIntoAFreshRepository() {
        val source = testEnv()
        source.repo.addSong("Extra Song", "Bop", 2)
        source.repo.markPracticed("Extra Song")
        val backup = source.repo.exportBackup()

        val target = testEnv(today = TODAY + 1, seedJson = null)
        val result = target.repo.importBackup(backup)

        assertEquals(ImportBackupResult.Ok, result)
        assertEquals(source.db.songs.keys, target.db.songs.keys)
        assertEquals(2, target.song("Extra Song").currentLevel)
        assertEquals(source.db.activity.size, target.db.activity.size)
    }

    @Test
    fun importReplacesExistingDataEntirely() {
        val env = testEnv()
        env.repo.addSong("Will Be Erased", "Bop", 0)
        val backup = craftedDbJson(songs = emptyList())

        val result = env.repo.importBackup(backup)

        assertEquals(ImportBackupResult.Ok, result)
        assertTrue(env.db.songs.none { it.key == "Will Be Erased" })
    }

    @Test
    fun importRunsTheSameMigrationsAFreshLoadWould() {
        // Same fixture as InitAndMigrationTest.legacyWebBackupIsMigrated: a real
        // pre-voicings web backup missing composer/year, voicings, onboardingComplete.
        val legacy = """
            {"songs":{"Stella by Starlight":{"name":"Stella by Starlight","baseLevel":2,"currentLevel":2,"style":"","status":"active","intervalIdx":1,"nextReview":20260508,"learnedDate":20260419,"reviews":[{"date":20260425,"passed":true}],"sortSeed":0.653581928917298}},"currentNewSong":"Anthropology","streak":1,"lastPracticeDay":20260419,"activity":[]}
        """.trimIndent()
        val env = testEnv()

        val result = env.repo.importBackup(legacy)

        assertEquals(ImportBackupResult.Ok, result)
        assertEquals("Victor Young", env.song("Stella by Starlight").composer)
        assertEquals(VOICINGS_SEED_VERSION, env.db.voicingsSeedVersion)
        assertEquals(true, env.db.onboardingComplete)
        val persisted = JazzJson.decodeFromString<com.rothrockware.studyjazzstandards.data.model.JazzDb>(env.store.read()!!)
        assertEquals(true, persisted.onboardingComplete)
    }

    @Test
    fun garbageJsonIsRejectedWithoutTouchingExistingData() {
        val env = testEnv()
        env.repo.addSong("Keep Me", "Bop", 0)

        val result = env.repo.importBackup("{not valid json")

        assertEquals(ImportBackupResult.InvalidFormat, result)
        assertTrue(env.db.songs.containsKey("Keep Me"))
    }

    @Test
    fun jsonWithoutSongsKeyIsRejected() {
        val env = testEnv()
        env.repo.addSong("Keep Me", "Bop", 0)

        val result = env.repo.importBackup("""{"streak":5}""")

        assertEquals(ImportBackupResult.InvalidFormat, result)
        assertTrue(env.db.songs.containsKey("Keep Me"))
    }
}
