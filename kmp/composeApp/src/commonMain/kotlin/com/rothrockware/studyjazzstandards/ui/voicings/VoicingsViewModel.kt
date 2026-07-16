package com.rothrockware.studyjazzstandards.ui.voicings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.AddResult
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.model.Voicing
import com.rothrockware.studyjazzstandards.data.seed.VOICING_CATEGORIES
import com.rothrockware.studyjazzstandards.data.topNoteSortKey
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class VoicingSection(val category: String, val voicings: List<Voicing>)

sealed interface VoicingDialog {
    data class Add(
        val name: String = "",
        val category: String = "Major",
        val topNote: String = "",
        val image: String = "",
        val familiarity: Int = 0,
        val showError: Boolean = false,
    ) : VoicingDialog

    data class Edit(
        val originalName: String,
        val name: String,
        val category: String,
        val topNote: String,
        val image: String,
        val familiarity: Int,
        val showError: Boolean = false,
    ) : VoicingDialog

    data class Remove(val name: String) : VoicingDialog
}

class VoicingsViewModel(private val repo: JazzRepository) : JazzViewModel() {

    val sections: StateFlow<List<VoicingSection>> = repo.db.map { db ->
        VOICING_CATEGORIES.mapNotNull { cat ->
            val items = db.voicings
                .filter { it.category == cat }
                .sortedWith(
                    compareBy<Voicing> { topNoteSortKey(it.topNote) }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
            if (items.isEmpty()) null else VoicingSection(cat, items)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var dialog by mutableStateOf<VoicingDialog?>(null)

    fun practiced(name: String) {
        repo.voicingPracticed(name)
        val fam = repo.db.value.voicings.firstOrNull { it.name == name }?.familiarity ?: 0
        toast(if (fam >= 2) "\"$name\" mastered!" else "\"$name\" — familiarity now $fam.")
    }

    fun openAdd() {
        dialog = VoicingDialog.Add()
    }

    fun openEdit(name: String) {
        val v = repo.db.value.voicings.firstOrNull { it.name == name } ?: return
        dialog = VoicingDialog.Edit(
            originalName = name,
            name = v.name,
            category = v.category,
            topNote = v.topNote.orEmpty(),
            image = v.image.orEmpty(),
            familiarity = v.familiarity,
        )
    }

    fun openRemove(name: String) {
        dialog = VoicingDialog.Remove(name)
    }

    fun closeDialog() {
        dialog = null
    }

    fun updateAdd(update: (VoicingDialog.Add) -> VoicingDialog.Add) {
        (dialog as? VoicingDialog.Add)?.let { dialog = update(it).copy(showError = false) }
    }

    fun updateEdit(update: (VoicingDialog.Edit) -> VoicingDialog.Edit) {
        (dialog as? VoicingDialog.Edit)?.let { dialog = update(it).copy(showError = false) }
    }

    fun submitAdd() {
        val d = dialog as? VoicingDialog.Add ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.addVoicing(name, d.category, d.topNote, d.image, d.familiarity)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" added to voicings.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun submitEdit() {
        val d = dialog as? VoicingDialog.Edit ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        when (repo.editVoicing(d.originalName, name, d.category, d.topNote, d.image, d.familiarity)) {
            is AddResult.Ok -> {
                dialog = null
                toast("\"$name\" updated.")
            }
            is AddResult.DuplicateName -> dialog = d.copy(showError = true)
        }
    }

    fun confirmRemove() {
        val d = dialog as? VoicingDialog.Remove ?: return
        repo.removeVoicing(d.name)
        dialog = null
        toast("\"${d.name}\" removed from voicings.")
    }
}
