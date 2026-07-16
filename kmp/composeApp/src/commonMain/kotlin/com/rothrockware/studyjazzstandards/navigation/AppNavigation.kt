package com.rothrockware.studyjazzstandards.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class Tab(val title: String) {
    Today("Today"),
    Library("Library"),
    Repertoire("Repertoire"),
    Voicings("Voicings"),
    Queue("Review Queue"),
    Progress("Progress"),
    Activity("Activity"),
}

sealed interface Route {
    data class TabRoute(val tab: Tab) : Route

    /** origin mirrors the web app's detailOriginPanel — back returns there. */
    data class SongDetail(val name: String, val origin: Tab) : Route
}

class AppViewModel : ViewModel() {
    var route by mutableStateOf<Route>(Route.TabRoute(Tab.Today))
        private set

    val currentTab: Tab
        get() = when (val r = route) {
            is Route.TabRoute -> r.tab
            is Route.SongDetail -> r.origin
        }

    fun selectTab(tab: Tab) {
        route = Route.TabRoute(tab)
    }

    fun openSongDetail(name: String) {
        route = Route.SongDetail(name, currentTab)
    }

    fun back(): Boolean {
        val r = route
        if (r is Route.SongDetail) {
            route = Route.TabRoute(r.origin)
            return true
        }
        return false
    }
}
