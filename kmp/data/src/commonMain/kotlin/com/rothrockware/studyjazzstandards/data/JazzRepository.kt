package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.RepertoirePiece
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.Voicing
import kotlinx.coroutines.flow.StateFlow

sealed interface StartLearningResult {
    /** Another song is in progress; call again with confirmSwitch=true to switch. */
    data class NeedsConfirmation(val currentName: String) : StartLearningResult
    data object Started : StartLearningResult
}

sealed interface AddResult {
    data object Ok : AddResult
    data object DuplicateName : AddResult
}

sealed interface ReviewOutcome {
    data object Graduated : ReviewOutcome
    data class NextInDays(val days: Int) : ReviewOutcome
    data object BackTomorrow : ReviewOutcome
}

interface JazzRepository {
    val db: StateFlow<JazzDb>

    fun today(): Int

    // Learning lifecycle
    fun startLearning(name: String, confirmSwitch: Boolean = false): StartLearningResult
    fun markPracticed(name: String)
    fun markLearned(name: String)
    fun practicedToday(name: String): Boolean

    // Review queue
    fun doReview(name: String, passed: Boolean): ReviewOutcome?
    fun normalizeReviewQueue(): Boolean
    fun reviewAnotherSong(): Boolean
    fun setReviewMax(value: Int)
    fun dueReviews(): List<Song>
    fun reviewedToday(): Boolean
    fun hasUpcomingReviews(): Boolean

    // Songs
    fun addSong(name: String, style: String, level: Int): AddResult
    fun editSong(oldName: String, newName: String, style: String, baseLevel: Int): AddResult
    fun removeSong(name: String)
    fun nextSuggestedSong(): Song?

    // Repertoire
    fun addRepertoire(name: String, composer: String?, notes: String?): AddResult
    fun editRepertoire(oldName: String, newName: String, composer: String?, notes: String?): AddResult
    fun removeRepertoire(name: String)
    fun markRepertoireReviewed(name: String)
    fun repertoireContinueTomorrow(name: String)
    fun featuredRepertoire(): RepertoirePiece?

    // Voicings
    fun addVoicing(name: String, category: String, topNote: String?, image: String?, familiarity: Int): AddResult
    fun editVoicing(oldName: String, newName: String, category: String, topNote: String?, image: String?, familiarity: Int): AddResult
    fun removeVoicing(name: String)
    fun voicingPracticed(name: String)
    fun voicingPracticedToday(name: String): Boolean
    fun featuredVoicing(): Voicing?

    // Onboarding / lifecycle
    fun completeOnboarding(levels: Map<String, Int>)
    fun skipOnboarding()
    fun resetAll()
}

/** Web parity: normalize flat/sharp shorthand ("b9" -> "♭9", "#5" -> "♯5"). */
fun normTopNote(s: String): String =
    s.trim().replace("b", "♭").replace("B", "♭").replace("#", "♯")

/** Web parity: numeric portion of a top note, or 999 when there is none. */
fun topNoteSortKey(topNote: String?): Int {
    val digits = (topNote ?: "").filter { it.isDigit() }
    return digits.toIntOrNull() ?: 999
}
