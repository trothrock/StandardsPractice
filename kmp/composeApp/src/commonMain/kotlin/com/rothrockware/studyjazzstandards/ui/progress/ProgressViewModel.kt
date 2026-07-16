package com.rothrockware.studyjazzstandards.ui.progress

import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.seed.PRIORITY_ORDER
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val notStarted: Int = 0,
    val inProgress: Int = 0,
    val atLevelTwoPlus: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val nextSongs: List<Song> = emptyList(),
)

class ProgressViewModel(repo: JazzRepository) : JazzViewModel() {

    val state: StateFlow<ProgressUiState> = repo.db.map { db ->
        val all = db.songs.values
        val grad = all.count { it.currentLevel >= 2 }
        ProgressUiState(
            notStarted = all.count { it.status == SongStatus.UNTOUCHED },
            inProgress = all.count { it.status == SongStatus.LEARNING || it.status == SongStatus.ACTIVE },
            atLevelTwoPlus = grad,
            total = all.size,
            percent = if (all.isEmpty()) 0 else (grad * 100.0 / all.size).roundToInt(),
            nextSongs = PRIORITY_ORDER
                .mapNotNull { db.songs[it] }
                .filter { it.status == SongStatus.UNTOUCHED }
                .take(12),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProgressUiState())
}
