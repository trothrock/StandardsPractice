package com.rothrockware.studyjazzstandards.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.AddResult
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.StartLearningResult
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import com.rothrockware.studyjazzstandards.ui.today.PendingSwitch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

val SONG_STYLES = listOf("Bop", "Blues", "Bossa", "Modal", "Trad")

data class LibraryFilters(
    val query: String = "",
    val level: Int? = null,
    val status: String? = null,
    val style: String? = null,
)

/** Dialog state for the add/edit/remove song modals. */
sealed interface SongDialog {
    data class Add(val name: String = "", val style: String = "", val level: Int = 0, val showError: Boolean = false) : SongDialog
    data class Edit(val originalName: String, val name: String, val style: String, val level: Int, val showError: Boolean = false) : SongDialog
    data class Remove(val name: String) : SongDialog
}

class LibraryViewModel(private val repo: JazzRepository) : JazzViewModel() {

    val filters = MutableStateFlow(LibraryFilters())

    var dialog by mutableStateOf<SongDialog?>(null)

    var pendingSwitch by mutableStateOf<PendingSwitch?>(null)
        private set

    val songs: StateFlow<List<Song>> = combine(repo.db, filters) { db, f ->
        // Web parity: all songs (seeded + user-added) alphabetically; the level
        // filter matches baseLevel even though rows display currentLevel.
        db.songs.values
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .filter { f.query.isEmpty() || it.name.lowercase().contains(f.query.lowercase()) }
            .filter { f.level == null || it.baseLevel == f.level }
            .filter { f.status == null || it.status == f.status }
            .filter { f.style == null || it.style == f.style }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setLevel(level: Int?) = filters.update { it.copy(level = level) }
    fun setStatus(status: String?) = filters.update { it.copy(status = status) }
    fun setStyle(style: String?) = filters.update { it.copy(style = style) }

    private inline fun MutableStateFlow<LibraryFilters>.update(f: (LibraryFilters) -> LibraryFilters) {
        value = f(value)
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

    fun markLearned(name: String) {
        repo.markLearned(name)
        toast("$name → Level 2. Review scheduled for tomorrow.")
    }

    fun openAdd() {
        dialog = SongDialog.Add()
    }

    fun openEdit(name: String) {
        val sg = repo.db.value.songs[name] ?: return
        dialog = SongDialog.Edit(originalName = name, name = sg.name, style = sg.style, level = sg.baseLevel)
    }

    fun openRemove(name: String) {
        dialog = SongDialog.Remove(name)
    }

    fun closeDialog() {
        dialog = null
    }

    fun updateAdd(update: (SongDialog.Add) -> SongDialog.Add) {
        (dialog as? SongDialog.Add)?.let { dialog = update(it).copy(showError = false) }
    }

    fun updateEdit(update: (SongDialog.Edit) -> SongDialog.Edit) {
        (dialog as? SongDialog.Edit)?.let { dialog = update(it).copy(showError = false) }
    }

    fun submitAdd() {
        val d = dialog as? SongDialog.Add ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.addSong(name, d.style, d.level)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" added to ${if (d.level >= 2) "review queue" else "backlog"}.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun submitEdit() {
        val d = dialog as? SongDialog.Edit ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.editSong(d.originalName, name, d.style, d.level)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" updated.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun confirmRemove() {
        val d = dialog as? SongDialog.Remove ?: return
        repo.removeSong(d.name)
        dialog = null
        toast("\"${d.name}\" removed.")
    }
}
