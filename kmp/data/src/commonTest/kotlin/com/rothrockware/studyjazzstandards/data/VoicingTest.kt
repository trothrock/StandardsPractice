package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VoicingTest {

    @Test
    fun normTopNoteConvertsAccidentals() {
        assertEquals("♭9", normTopNote("b9"))
        assertEquals("♯5", normTopNote("#5"))
        assertEquals("♭5", normTopNote(" B5 "))
        assertEquals("13", normTopNote("13"))
    }

    @Test
    fun topNoteSortKeyExtractsDigits() {
        assertEquals(9, topNoteSortKey("♭9"))
        assertEquals(13, topNoteSortKey("13"))
        assertEquals(999, topNoteSortKey(null))
        assertEquals(999, topNoteSortKey("x"))
    }

    @Test
    fun addVoicingNormalizesTopNote() {
        val env = testEnv()
        assertIs<AddResult.Ok>(env.repo.addVoicing("My Shell", "Dominant", "b9", null, 1))
        val v = env.db.voicings.first { it.name == "My Shell" }
        assertEquals("♭9", v.topNote)
        assertEquals(1, v.familiarity)
        assertEquals(TODAY, v.addedDate)
        assertEquals(ActivityType.VOICING_ADDED, env.db.activity.last().type)

        assertIs<AddResult.DuplicateName>(env.repo.addVoicing("my shell", "Major", null, null, 0))
        assertIs<AddResult.DuplicateName>(env.repo.addVoicing("MAJOR 7", "Major", null, null, 0), "clashes with seed voicing")
    }

    @Test
    fun editVoicingUpdatesAllFields() {
        val env = testEnv()
        assertIs<AddResult.Ok>(env.repo.editVoicing("Major 7", "Major 7 Shell", "Major", "#11", "maj7.png", 2))
        val v = env.db.voicings.first { it.name == "Major 7 Shell" }
        assertEquals("♯11", v.topNote)
        assertEquals("maj7.png", v.image)
        assertEquals(2, v.familiarity)
    }

    @Test
    fun voicingPracticedIncrementsOncePerDayCappedAtTwo() {
        val env = testEnv()
        env.repo.voicingPracticed("Major 7")
        assertEquals(1, env.db.voicings.first { it.name == "Major 7" }.familiarity)
        // Same day: no-op
        env.repo.voicingPracticed("Major 7")
        assertEquals(1, env.db.voicings.first { it.name == "Major 7" }.familiarity)
        assertEquals(1, env.db.activity.count { it.type == ActivityType.VOICING_PRACTICED })

        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        env.repo.voicingPracticed("Major 7")
        assertEquals(2, env.db.voicings.first { it.name == "Major 7" }.familiarity)

        // Capped at 2
        env.clock.todayInt = DateInt.addDays(TODAY, 2)
        env.repo.voicingPracticed("Major 7")
        assertEquals(2, env.db.voicings.first { it.name == "Major 7" }.familiarity)
    }

    @Test
    fun featuredVoicingRotatesDeterministicallyAmongLeastFamiliar() {
        val env = testEnv()
        val candidates = env.db.voicings.filter { it.familiarity == 0 }.sortedBy { it.sortSeed }
        val dayCount = DateInt.daysBetween(20200101, TODAY)
        val expected = candidates[dayCount % candidates.size]
        assertEquals(expected.name, env.repo.featuredVoicing()?.name)
        // Same day, same answer
        assertEquals(expected.name, env.repo.featuredVoicing()?.name)
        // Next day advances the rotation
        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        val expectedNext = candidates[(dayCount + 1) % candidates.size]
        assertEquals(expectedNext.name, env.repo.featuredVoicing()?.name)
    }

    @Test
    fun featuredVoicingNullWhenAllMastered() {
        val env = testEnv()
        val db = env.db
        val allMastered = db.copy(voicings = db.voicings.map { it.copy(familiarity = 2) })
        val env2 = testEnv(seedJson = craftedDbJson(db = allMastered))
        assertNull(env2.repo.featuredVoicing())
    }

    @Test
    fun removeVoicingLogsActivity() {
        val env = testEnv()
        env.repo.removeVoicing("Major 7")
        assertEquals(14, env.db.voicings.size)
        assertEquals(ActivityType.VOICING_REMOVED, env.db.activity.last().type)
    }
}
