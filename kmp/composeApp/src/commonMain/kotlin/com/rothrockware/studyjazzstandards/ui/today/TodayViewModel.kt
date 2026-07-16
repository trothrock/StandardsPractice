package com.rothrockware.studyjazzstandards.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.ReviewOutcome
import com.rothrockware.studyjazzstandards.data.StartLearningResult
import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.RepertoirePiece
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.Voicing
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import com.rothrockware.studyjazzstandards.ui.activity.formatFullDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LearningCard(val song: Song, val practicedToday: Boolean)

data class PendingSwitch(val currentName: String, val newName: String)

data class TodayUiState(
    val dateLabel: String = "",
    val streak: Int = 0,
    val learning: LearningCard? = null,
    val suggested: Song? = null,
    val featuredRepertoire: RepertoirePiece? = null,
    val repertoireDoneToday: Boolean = false,
    val featuredVoicing: Voicing? = null,
    val voicingDoneToday: Boolean = false,
    val due: List<Song> = emptyList(),
    val reviewedToday: Boolean = false,
    val hasUpcoming: Boolean = false,
    val reviewMax: Int = 1,
)

class TodayViewModel(private val repo: JazzRepository) : JazzViewModel() {

    init {
        // Web parity: renderToday() normalizes the queue before showing it.
        repo.normalizeReviewQueue()
    }

    val state: StateFlow<TodayUiState> = repo.db
        .map { buildState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(repo.db.value))

    /** Non-null while the "switch learning songs?" confirmation dialog is up. */
    var pendingSwitch by mutableStateOf<PendingSwitch?>(null)
        private set

    var showReviewSettings by mutableStateOf(false)

    private fun buildState(db: JazzDb): TodayUiState {
        val tod = repo.today()
        val learningSong = db.currentNewSong?.let { db.songs[it] }
        val featured = repo.featuredRepertoire()
        val voicing = repo.featuredVoicing()
        return TodayUiState(
            dateLabel = formatFullDate(tod),
            streak = db.streak,
            learning = learningSong?.let { LearningCard(it, repo.practicedToday(it.name)) },
            suggested = if (learningSong == null) repo.nextSuggestedSong() else null,
            featuredRepertoire = featured,
            repertoireDoneToday = featured?.lastReviewed == tod,
            featuredVoicing = voicing,
            voicingDoneToday = voicing?.let { repo.voicingPracticedToday(it.name) } ?: false,
            due = repo.dueReviews(),
            reviewedToday = repo.reviewedToday(),
            hasUpcoming = repo.hasUpcomingReviews(),
            reviewMax = db.reviewMax,
        )
    }

    fun startLearning(name: String) {
        when (val result = repo.startLearning(name)) {
            is StartLearningResult.NeedsConfirmation ->
                pendingSwitch = PendingSwitch(result.currentName, name)
            is StartLearningResult.Started -> toast("Now learning: $name")
        }
    }

    fun confirmSwitch() {
        val pending = pendingSwitch ?: return
        repo.startLearning(pending.newName, confirmSwitch = true)
        toast("Now learning: ${pending.newName}")
        pendingSwitch = null
    }

    fun cancelSwitch() {
        pendingSwitch = null
    }

    fun markPracticed(name: String) {
        repo.markPracticed(name)
        toast("Practiced: $name")
    }

    fun markLearned(name: String) {
        repo.markLearned(name)
        toast("$name → Level 2. Review scheduled for tomorrow.")
    }

    fun doReview(name: String, passed: Boolean) {
        when (val outcome = repo.doReview(name, passed)) {
            is ReviewOutcome.Graduated -> toast("$name graduated! Level 3.")
            is ReviewOutcome.NextInDays -> toast("Reviewed — next review in ${outcome.days} days")
            is ReviewOutcome.BackTomorrow -> toast("Continue tomorrow — back in 1 day")
            null -> Unit
        }
    }

    fun reviewAnother() {
        if (!repo.reviewAnotherSong()) toast("No more songs available to review.")
    }

    fun setReviewMax(value: Int) {
        if (value < 1) return
        repo.setReviewMax(value)
        showReviewSettings = false
        toast("Max reviews per day set to $value.")
    }

    fun markRepertoireReviewed(name: String) {
        repo.markRepertoireReviewed(name)
        toast("\"$name\" marked as reviewed.")
    }

    fun repertoireContinueTomorrow(name: String) {
        repo.repertoireContinueTomorrow(name)
        toast("\"$name\" queued for tomorrow.")
    }

    fun voicingPracticed(name: String) {
        repo.voicingPracticed(name)
        val fam = repo.db.value.voicings.firstOrNull { it.name == name }?.familiarity ?: 0
        toast(if (fam >= 2) "\"$name\" mastered!" else "\"$name\" — familiarity now $fam.")
    }
}
