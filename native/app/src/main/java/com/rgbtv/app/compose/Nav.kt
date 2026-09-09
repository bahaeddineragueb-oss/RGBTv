package com.rgbtv.app.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data class Browse(val mode: Int) : Screen
    data object Sports : Screen
    data object Guide : Screen
    data object Search : Screen
    data object MyList : Screen
    data object Settings : Screen
    data object Profiles : Screen
    data class Detail(val kind: String, val json: String) : Screen
    data class AccountEdit(val id: String?) : Screen
    data object Cloud : Screen

    companion object {
        const val LIVE = 0
        const val VOD = 1
        const val SERIES = 2
    }
}

/** Simple stack navigator with sidebar key tracking. */
class Navigator(initial: Screen = Screen.Home) {
    var stack by mutableStateOf(listOf(initial))
        private set

    val current: Screen get() = stack.last()

    val sideKey: String
        get() = when (val c = current) {
            is Screen.Home -> "home"
            is Screen.Browse -> when (c.mode) {
                Screen.VOD -> "movies"
                Screen.SERIES -> "series"
                else -> "live"
            }
            is Screen.Sports -> "sports"
            is Screen.Guide -> "guide"
            is Screen.Search -> "search"
            is Screen.MyList -> "mylist"
            is Screen.Settings -> "settings"
            is Screen.Profiles -> "profiles"
            else -> sideKeyOf(stack.dropLast(1).lastOrNull())
        }

    private fun sideKeyOf(s: Screen?): String = when (s) {
        is Screen.Home -> "home"
        is Screen.Browse -> when (s.mode) {
            Screen.VOD -> "movies"
            Screen.SERIES -> "series"
            else -> "live"
        }
        is Screen.Sports -> "sports"
        is Screen.Guide -> "guide"
        is Screen.Search -> "search"
        is Screen.MyList -> "mylist"
        is Screen.Settings -> "settings"
        is Screen.Profiles -> "profiles"
        is Screen.Detail -> sideKeyOf(stack.dropLast(1).lastOrNull())
        else -> "home"
    }

    /** Top-level: clears the stack. */
    fun nav(s: Screen) {
        stack = listOf(s)
    }

    /** Drill-in: pushes. */
    fun open(s: Screen) {
        stack = stack + s
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }
}
