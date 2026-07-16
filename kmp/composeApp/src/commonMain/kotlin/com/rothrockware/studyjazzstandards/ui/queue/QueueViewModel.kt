package com.rothrockware.studyjazzstandards.ui.queue

import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class QueueSort(val label: String) { Due("Due date"), Title("Title") }

class QueueViewModel(repo: JazzRepository) : JazzViewModel() {

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(QueueSort.Due)

    val songs: StateFlow<List<Song>> = combine(repo.db, query, sort) { db, q, s ->
        db.songs.values
            .filter { it.status == SongStatus.ACTIVE || it.status == SongStatus.GRADUATED }
            .filter { q.isEmpty() || it.name.lowercase().contains(q.lowercase()) }
            .sortedWith(
                when (s) {
                    QueueSort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    // Web parity: missing nextReview sorts last via 99999999
                    QueueSort.Due -> compareBy { it.nextReview ?: 99999999 }
                },
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
