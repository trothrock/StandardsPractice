package com.rothrockware.studyjazzstandards.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.seed.ALL_SONGS
import com.rothrockware.studyjazzstandards.data.seed.SeedSong
import com.rothrockware.studyjazzstandards.ui.JazzViewModel

enum class OnboardingStage { Welcome, Ranker }

class OnboardingViewModel(private val repo: JazzRepository) : JazzViewModel() {

    var stage by mutableStateOf(OnboardingStage.Welcome)
        private set

    var index by mutableIntStateOf(0)
        private set

    private val levels = mutableStateMapOf<String, Int>()

    val totalSongs: Int get() = ALL_SONGS.size

    val currentSong: SeedSong get() = ALL_SONGS[index]

    val currentLevel: Int get() = levels[currentSong.title] ?: 0

    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == ALL_SONGS.size - 1

    fun startRanker() {
        // Web parity: initialize from existing baseLevels so re-ranking edits in place.
        val db = repo.db.value
        ALL_SONGS.forEach { seed ->
            levels[seed.title] = db.songs[seed.title]?.baseLevel ?: 0
        }
        index = 0
        stage = OnboardingStage.Ranker
    }

    fun setLevel(level: Int) {
        levels[currentSong.title] = level
    }

    fun next() {
        if (isLast) {
            finish()
        } else {
            index++
        }
    }

    fun back() {
        if (index > 0) index--
    }

    fun finish() {
        repo.completeOnboarding(levels.toMap())
        val rated = levels.values.count { it > 0 }
        if (rated > 0) {
            toast("Ratings saved — $rated song${if (rated == 1) "" else "s"} added to your schedule.")
        }
    }

    fun skip() {
        repo.skipOnboarding()
    }
}
