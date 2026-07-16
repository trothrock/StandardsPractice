package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import com.rothrockware.studyjazzstandards.data.store.InMemoryBlobStore
import kotlin.random.Random

const val TODAY = 20260715

class TestEnv(
    val store: InMemoryBlobStore,
    val clock: FixedJazzClock,
    val repo: DefaultJazzRepository,
) {
    val db get() = repo.db.value
    fun song(name: String): Song = db.songs.getValue(name)
}

fun testEnv(
    today: Int = TODAY,
    nowMillis: Long = 1_752_500_000_000,
    seedJson: String? = null,
    random: Random = Random(42),
): TestEnv {
    val store = InMemoryBlobStore(seedJson)
    val clock = FixedJazzClock(today, nowMillis)
    return TestEnv(store, clock, DefaultJazzRepository(store, clock, random))
}

/** Builds an already-migrated db blob so tests control state exactly. */
fun craftedDbJson(
    songs: List<Song> = emptyList(),
    db: JazzDb = JazzDb(
        songs = songs.associateBy { it.name },
        onboardingComplete = true,
        voicingsSeedVersion = VOICINGS_SEED_VERSION,
    ),
): String = JazzJson.encodeToString(JazzDb.serializer(), db)

fun activeSong(
    name: String,
    intervalIdx: Int = 0,
    nextReview: Int? = TODAY,
    reviews: List<com.rothrockware.studyjazzstandards.data.model.Review> = emptyList(),
    sortSeed: Double = 0.5,
    status: String = SongStatus.ACTIVE,
): Song = Song(
    name = name,
    baseLevel = 2,
    currentLevel = 2,
    status = status,
    intervalIdx = intervalIdx,
    nextReview = nextReview,
    learnedDate = 20260101,
    reviews = reviews,
    sortSeed = sortSeed,
)
