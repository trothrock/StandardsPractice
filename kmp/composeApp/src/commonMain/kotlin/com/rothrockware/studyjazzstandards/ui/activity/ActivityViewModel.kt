package com.rothrockware.studyjazzstandards.ui.activity

import androidx.lifecycle.viewModelScope
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.ui.JazzViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

class ActivityViewModel(
    repo: JazzRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : JazzViewModel() {

    val period = MutableStateFlow(ActivityPeriod.Week)

    val groups: StateFlow<List<ActivityGroup>> = combine(repo.db, period) { db, p ->
        groupActivity(db.activity, p, zone)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun timeZone(): TimeZone = zone
}
