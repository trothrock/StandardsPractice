package com.rothrockware.studyjazzstandards.ui

import com.rothrockware.studyjazzstandards.data.DefaultJazzRepository
import com.rothrockware.studyjazzstandards.data.FixedJazzClock
import com.rothrockware.studyjazzstandards.data.JazzJson
import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import com.rothrockware.studyjazzstandards.data.store.InMemoryBlobStore
import kotlin.random.Random

const val VM_TODAY = 20260715

class VmTestEnv(
    val clock: FixedJazzClock,
    val repo: DefaultJazzRepository,
)

fun vmTestEnv(today: Int = VM_TODAY, seedJson: String? = null): VmTestEnv {
    val clock = FixedJazzClock(today, nowMillis = 1_752_500_000_000)
    return VmTestEnv(clock, DefaultJazzRepository(InMemoryBlobStore(seedJson), clock, Random(42)))
}

fun craftedDbJson(songs: List<Song>): String = JazzJson.encodeToString(
    JazzDb.serializer(),
    JazzDb(
        songs = songs.associateBy { it.name },
        onboardingComplete = true,
        voicingsSeedVersion = VOICINGS_SEED_VERSION,
    ),
)

fun activeSong(
    name: String,
    intervalIdx: Int = 0,
    nextReview: Int? = VM_TODAY,
    sortSeed: Double = 0.5,
    status: String = SongStatus.ACTIVE,
    baseLevel: Int = 2,
    currentLevel: Int = 2,
    style: String = "",
): Song = Song(
    name = name,
    style = style,
    baseLevel = baseLevel,
    currentLevel = currentLevel,
    status = status,
    intervalIdx = intervalIdx,
    nextReview = nextReview,
    learnedDate = 20260101,
    sortSeed = sortSeed,
)
