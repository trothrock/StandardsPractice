package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.seed.ALL_SONGS
import com.rothrockware.studyjazzstandards.data.seed.PRIORITY_ORDER
import com.rothrockware.studyjazzstandards.data.seed.SEED_VOICINGS
import com.rothrockware.studyjazzstandards.data.seed.VOICING_CATEGORIES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataTest {

    @Test
    fun songCountMatchesWebSeed() {
        assertEquals(272, ALL_SONGS.size)
    }

    @Test
    fun noDuplicateSongTitles() {
        assertEquals(ALL_SONGS.size, ALL_SONGS.map { it.title }.toSet().size)
    }

    @Test
    fun priorityOrderResolvesAgainstSongs() {
        val titles = ALL_SONGS.map { it.title }.toSet()
        val missing = PRIORITY_ORDER.filter { it !in titles }
        assertTrue(missing.isEmpty(), "PRIORITY_ORDER titles not in ALL_SONGS: $missing")
        assertEquals(PRIORITY_ORDER.size, PRIORITY_ORDER.toSet().size, "duplicate priority titles")
    }

    @Test
    fun seedVoicingsMatchWebSeed() {
        assertEquals(15, SEED_VOICINGS.size)
        assertTrue(SEED_VOICINGS.all { it.category in VOICING_CATEGORIES })
        val flatNine = SEED_VOICINGS.first { it.name == "Dominant 7♭9" }
        assertEquals("♭9", flatNine.topNote)
    }
}
