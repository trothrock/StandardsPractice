package com.rothrockware.studyjazzstandards.data.model

import kotlinx.serialization.Serializable

/**
 * The full persisted database. Field names and shapes match the web app's
 * localStorage JSON (key "jazz_sr_v3") exactly so backups round-trip between
 * the two implementations.
 */
@Serializable
data class JazzDb(
    val songs: Map<String, Song> = emptyMap(),
    val currentNewSong: String? = null,
    val streak: Int = 0,
    val lastPracticeDay: Int? = null,
    val activity: List<ActivityEntry> = emptyList(),
    /**
     * Nullable to mirror the web migration: a legacy blob missing this key is
     * backfilled to TRUE (don't re-onboard existing users); only a fresh DB
     * starts at false. Always non-null after DefaultJazzRepository loads.
     */
    val onboardingComplete: Boolean? = null,
    val repertoire: List<RepertoirePiece> = emptyList(),
    val voicings: List<Voicing> = emptyList(),
    val voicingsSeedVersion: Int? = null,
    val reviewMax: Int = 1,
    val extraReviewsToday: ExtraReviews? = null,
)

/** Status values are plain strings (not an enum) to stay tolerant of JSON from other versions. */
object SongStatus {
    const val UNTOUCHED = "untouched"
    const val LEARNING = "learning"
    const val ACTIVE = "active"
    const val GRADUATED = "graduated"
}

@Serializable
data class Song(
    val name: String,
    val style: String = "",
    val composer: String? = null,
    val year: Int? = null,
    val baseLevel: Int = 0,
    val currentLevel: Int = 0,
    val status: String = SongStatus.UNTOUCHED,
    val intervalIdx: Int = -1,
    val nextReview: Int? = null,
    val learnedDate: Int? = null,
    val reviews: List<Review> = emptyList(),
    val sortSeed: Double = 0.0,
)

@Serializable
data class Review(
    val date: Int,
    val passed: Boolean,
)

@Serializable
data class RepertoirePiece(
    val name: String,
    val composer: String? = null,
    val notes: String? = null,
    val addedDate: Int,
    val lastReviewed: Int? = null,
    val pinnedDate: Int? = null,
)

@Serializable
data class Voicing(
    val name: String,
    val category: String,
    @Serializable(with = TopNoteSerializer::class)
    val topNote: String? = null,
    val image: String? = null,
    val familiarity: Int = 0,
    val addedDate: Int,
    val sortSeed: Double = 0.0,
)

object ActivityType {
    const val STARTED_LEARNING = "started_learning"
    const val PRACTICED = "practiced"
    const val LEARNED = "learned"
    const val REVIEW = "review"
    const val SONG_ADDED = "song_added"
    const val SONG_EDITED = "song_edited"
    const val STOPPED_LEARNING = "stopped_learning"
    const val SONG_REMOVED = "song_removed"
    const val REPERTOIRE_ADDED = "repertoire_added"
    const val REPERTOIRE_REVIEWED = "repertoire_reviewed"
    const val REPERTOIRE_DEFERRED = "repertoire_deferred"
    const val REPERTOIRE_REMOVED = "repertoire_removed"
    const val VOICING_ADDED = "voicing_added"
    const val VOICING_PRACTICED = "voicing_practiced"
    const val VOICING_REMOVED = "voicing_removed"
}

@Serializable
data class ActivityEntry(
    val ts: Long,
    val date: Int,
    val type: String,
    val name: String? = null,
    val passed: Boolean? = null,
    val oldName: String? = null,
    val oldLevel: Int? = null,
    val level: Int? = null,
    val style: String? = null,
    val familiarity: Int? = null,
)

@Serializable
data class ExtraReviews(
    val date: Int,
    val count: Int,
)

val JazzDb.isOnboardingComplete: Boolean
    get() = onboardingComplete != false
