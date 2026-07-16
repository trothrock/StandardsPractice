package com.rothrockware.studyjazzstandards.ui.detail

import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.model.ActivityEntry
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SongDetailUiState(
    val song: Song? = null,
    val entries: List<ActivityEntry> = emptyList(),
)

class SongDetailViewModel(
    repo: JazzRepository,
    private val songName: String,
) : JazzViewModel() {

    val state: StateFlow<SongDetailUiState> = repo.db.map { db ->
        SongDetailUiState(
            song = db.songs[songName],
            // Web parity: include entries logged under the old name after a rename.
            entries = db.activity
                .filter { it.name == songName || it.oldName == songName }
                .sortedByDescending { it.ts },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SongDetailUiState(),
    )
}
