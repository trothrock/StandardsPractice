package com.rothrockware.studyjazzstandards.ui.repertoire

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.AddResult
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.model.RepertoirePiece
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RepertoireDialog {
    data class Add(val name: String = "", val composer: String = "", val notes: String = "", val showError: Boolean = false) : RepertoireDialog
    data class Edit(val originalName: String, val name: String, val composer: String, val notes: String, val showError: Boolean = false) : RepertoireDialog
    data class Remove(val name: String) : RepertoireDialog
}

class RepertoireViewModel(private val repo: JazzRepository) : JazzViewModel() {

    val pieces: StateFlow<List<RepertoirePiece>> = repo.db
        .map { db -> db.repertoire.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var dialog by mutableStateOf<RepertoireDialog?>(null)

    fun openAdd() {
        dialog = RepertoireDialog.Add()
    }

    fun openEdit(name: String) {
        val p = repo.db.value.repertoire.firstOrNull { it.name == name } ?: return
        dialog = RepertoireDialog.Edit(
            originalName = name,
            name = p.name,
            composer = p.composer.orEmpty(),
            notes = p.notes.orEmpty(),
        )
    }

    fun openRemove(name: String) {
        dialog = RepertoireDialog.Remove(name)
    }

    fun closeDialog() {
        dialog = null
    }

    fun updateAdd(update: (RepertoireDialog.Add) -> RepertoireDialog.Add) {
        (dialog as? RepertoireDialog.Add)?.let { dialog = update(it).copy(showError = false) }
    }

    fun updateEdit(update: (RepertoireDialog.Edit) -> RepertoireDialog.Edit) {
        (dialog as? RepertoireDialog.Edit)?.let { dialog = update(it).copy(showError = false) }
    }

    fun submitAdd() {
        val d = dialog as? RepertoireDialog.Add ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.addRepertoire(name, d.composer, d.notes)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" added to repertoire.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun submitEdit() {
        val d = dialog as? RepertoireDialog.Edit ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.editRepertoire(d.originalName, name, d.composer, d.notes)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" updated.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun confirmRemove() {
        val d = dialog as? RepertoireDialog.Remove ?: return
        repo.removeRepertoire(d.name)
        dialog = null
        toast("\"${d.name}\" removed from repertoire.")
    }
}
