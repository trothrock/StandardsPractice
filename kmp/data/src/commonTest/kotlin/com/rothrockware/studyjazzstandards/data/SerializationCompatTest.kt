package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityEntry
import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.Voicing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerializationCompatTest {

    @Test
    fun decodeEncodeDecodeIsStable() {
        val webJson = """
            {"songs":{"After You've Gone":{"name":"After You've Gone","baseLevel":2,"currentLevel":2,"style":"Trad","status":"active","intervalIdx":1,"nextReview":20260508,"learnedDate":20260419,"reviews":[{"date":20260425,"passed":true}],"sortSeed":0.653581928917298}},"currentNewSong":null,"streak":1,"lastPracticeDay":20260419,"activity":[{"ts":1776634938073,"date":20260419,"type":"practiced","name":"Anthropology"}],"onboardingComplete":true,"repertoire":[],"voicings":[],"voicingsSeedVersion":2,"reviewMax":1}
        """.trimIndent()
        val first = JazzJson.decodeFromString<JazzDb>(webJson)
        val encoded = JazzJson.encodeToString(JazzDb.serializer(), first)
        val second = JazzJson.decodeFromString<JazzDb>(encoded)
        assertEquals(first, second)
        val sg = first.songs.getValue("After You've Gone")
        assertEquals(1, sg.intervalIdx)
        assertEquals(20260508, sg.nextReview)
        assertEquals(true, sg.reviews.single().passed)
    }

    @Test
    fun explicitNullsFromWebAreAccepted() {
        val webJson = """
            {"songs":{"X":{"name":"X","baseLevel":0,"currentLevel":0,"style":"","status":"untouched","intervalIdx":-1,"nextReview":null,"learnedDate":null,"reviews":[],"sortSeed":0.5}},"currentNewSong":null,"streak":0,"lastPracticeDay":null,"activity":[]}
        """.trimIndent()
        val db = JazzJson.decodeFromString<JazzDb>(webJson)
        assertNull(db.songs.getValue("X").nextReview)
        assertNull(db.currentNewSong)
    }

    @Test
    fun nullFieldsAreOmittedOnEncode() {
        // The web app checks `entry.oldLevel !== undefined`, so null fields must
        // be omitted (not written as JSON null) for round-tripped backups.
        val db = JazzDb(
            activity = listOf(ActivityEntry(ts = 1L, date = TODAY, type = "practiced", name = "X")),
            onboardingComplete = true,
        )
        val encoded = JazzJson.encodeToString(JazzDb.serializer(), db)
        assertFalse(encoded.contains("oldLevel"))
        assertFalse(encoded.contains("null"))
        assertTrue(encoded.contains("\"reviewMax\":1"), "defaults are encoded like the web app writes them")
    }

    @Test
    fun topNoteRoundTripsNumbersAndStrings() {
        val db = JazzDb(
            onboardingComplete = true,
            voicings = listOf(
                Voicing(name = "Major 7", category = "Major", topNote = "7", addedDate = TODAY, sortSeed = 0.1),
                Voicing(name = "Dominant 7♭9", category = "Dominant", topNote = "♭9", addedDate = TODAY, sortSeed = 0.2),
                Voicing(name = "No Top", category = "Minor", topNote = null, addedDate = TODAY, sortSeed = 0.3),
            ),
        )
        val encoded = JazzJson.encodeToString(JazzDb.serializer(), db)
        assertTrue(encoded.contains("\"topNote\":7"), "all-digit top notes encode as JSON numbers: $encoded")
        assertTrue(encoded.contains("\"topNote\":\"♭9\""))
        val decoded = JazzJson.decodeFromString<JazzDb>(encoded)
        assertEquals("7", decoded.voicings[0].topNote)
        assertEquals("♭9", decoded.voicings[1].topNote)
        assertNull(decoded.voicings[2].topNote)
    }

    @Test
    fun webSeedNumericTopNoteDecodes() {
        val json = """{"onboardingComplete":true,"voicings":[{"name":"Major 7","category":"Major","topNote":7,"image":null,"familiarity":0,"addedDate":20260419,"sortSeed":0.4}]}"""
        val db = JazzJson.decodeFromString<JazzDb>(json)
        assertEquals("7", db.voicings.single().topNote)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val json = """{"songs":{},"someFutureField":42,"onboardingComplete":true}"""
        val db = JazzJson.decodeFromString<JazzDb>(json)
        assertEquals(0, db.songs.size)
    }
}
