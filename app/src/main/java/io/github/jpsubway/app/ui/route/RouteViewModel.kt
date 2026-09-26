package io.github.jpsubway.app.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.domain.routing.Journey
import io.github.jpsubway.app.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class RouteViewModel(private val c: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = false,
        val from: String? = null,
        val to: String? = null,
        val fromName: String = "",
        val toName: String = "",
        val departSec: Int = 0,
        val offsetMin: Int = 0,
        val journeys: List<Journey> = emptyList(),
        val message: String? = null,
        val network: Network? = null,
        val isDemo: Boolean = false,
    )

    private val offsetMin = MutableStateFlow(0)
    private val minuteTick = c.session.tick.map { it.seconds / 60 }.distinctUntilChanged()

    val ui: StateFlow<Ui> = combine(c.session.state, c.routeSelection.from, c.routeSelection.to, offsetMin, minuteTick) { s, f, t, off, _ ->
        compute(s, f, t, off)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui(loading = true))

    private fun compute(s: RegionSession.State, f: String?, t: String?, off: Int): Ui {
        val d = (s as? RegionSession.State.Ready)?.data
            ?: return Ui(loading = s is RegionSession.State.Loading, message = (s as? RegionSession.State.Error)?.message, from = f, to = t, offsetMin = off)
        val net = d.network
        val base = Ui(
            from = f, to = t,
            fromName = f?.let { net.groupName(it).display() }.orEmpty(),
            toName = t?.let { net.groupName(it).display() }.orEmpty(),
            offsetMin = off, network = net, isDemo = d.timetable.isDemo,
        )
        if (f == null || t == null) return base.copy(message = "출발역과 도착역을 선택하세요")
        if (f == t) return base.copy(message = "출발역과 도착역이 같습니다")
        val depart = ServiceClock.now().seconds + off * 60
        val fromIds = net.stationsByGroup[f].orEmpty().map { it.id }
        val toIds = net.stationsByGroup[t].orEmpty().map { it.id }
        val found = d.router.searchAlternatives(fromIds, toIds, depart, count = 3)
        return base.copy(
            departSec = depart,
            journeys = found,
            message = if (found.isEmpty()) "탈 수 있는 열차가 없습니다 (막차 이후이거나 연결되지 않은 구간)" else null,
        )
    }

    fun setOffset(min: Int) {
        offsetMin.value = min
    }

    fun swap() = c.routeSelection.swap()

    companion object {
        val Factory = viewModelFactory { initializer { RouteViewModel(appContainer()) } }
    }
}
