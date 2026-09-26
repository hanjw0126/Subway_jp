package io.github.jpsubway.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.ui.common.appContainer

class MapViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.session.state
    val realtime = c.session.realtime
    val from = c.routeSelection.from
    val to = c.routeSelection.to

    /** @return 출발·도착이 모두 정해졌으면 true */
    fun setFrom(group: String): Boolean {
        if (c.routeSelection.to.value == group) c.routeSelection.setTo(null)
        c.routeSelection.setFrom(group)
        return c.routeSelection.isComplete
    }

    fun setTo(group: String): Boolean {
        if (c.routeSelection.from.value == group) c.routeSelection.setFrom(null)
        c.routeSelection.setTo(group)
        return c.routeSelection.isComplete
    }

    fun clearRoute() = c.routeSelection.clear()

    companion object {
        val Factory = viewModelFactory { initializer { MapViewModel(appContainer()) } }
    }
}
