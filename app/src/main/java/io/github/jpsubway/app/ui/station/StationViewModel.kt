package io.github.jpsubway.app.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.arrival.ArrivalEstimator
import io.github.jpsubway.app.domain.model.*
import io.github.jpsubway.app.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class StationViewModel(private val c: AppContainer, private val group: String) : ViewModel() {
    private val estimator = ArrivalEstimator(perDirection = 2)
    private val selectedLine = MutableStateFlow<String?>(null)

    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val name: LocalizedName = LocalizedName(),
        val lines: List<Line> = emptyList(),
        val line: Line? = null,
        val station: Station? = null,
        val boards: List<DirectionBoard> = emptyList(),
        val status: LineStatus? = null,
        val sourceLabel: String = "",
        val realtimeError: String? = null,
        val network: Network? = null,
        val nowSec: Int = 0,
    )

    val ui: StateFlow<Ui> = combine(c.session.state, c.session.realtime, c.session.tick, selectedLine) { s, rt, now, sel ->
        when (s) {
            RegionSession.State.Loading -> Ui()
            is RegionSession.State.Error -> Ui(loading = false, error = s.message)
            is RegionSession.State.Ready -> build(s.data, rt, now.seconds, sel)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    private fun build(d: RegionSession.Data, rt: RealtimeSnapshot, now: Int, sel: String?): Ui {
        val net = d.network
        val stations = net.stationsByGroup[group].orEmpty()
        val lines = stations.mapNotNull { net.lineById[it.lineId] }.distinctBy { it.id }
        if (lines.isEmpty()) return Ui(loading = false, error = "역 정보를 찾을 수 없습니다")
        val line = lines.firstOrNull { it.id == sel } ?: lines.first()
        val station = stations.first { it.lineId == line.id }
        val boards = estimator.boards(line, station.id, d.tripsByLine[line.id].orEmpty(), rt, now)
        val label = when {
            d.timetable.isDemo -> "데모 시간표 기준 (실제 운행과 다를 수 있음)"
            rt.available -> "시간표 + 실시간 지연 반영"
            else -> "시간표 기준"
        }
        return Ui(
            loading = false, name = station.name, lines = lines, line = line, station = station, boards = boards,
            status = rt.statuses[line.id], sourceLabel = label, realtimeError = rt.error, network = net, nowSec = now,
        )
    }

    fun selectLine(id: String) {
        selectedLine.value = id
    }

    /** @return 출발·도착이 모두 정해졌으면 true */
    fun setFrom(): Boolean {
        if (c.routeSelection.to.value == group) c.routeSelection.setTo(null)
        c.routeSelection.setFrom(group)
        return c.routeSelection.isComplete
    }

    fun setTo(): Boolean {
        if (c.routeSelection.from.value == group) c.routeSelection.setFrom(null)
        c.routeSelection.setTo(group)
        return c.routeSelection.isComplete
    }

    companion object {
        fun factory(group: String) = viewModelFactory { initializer { StationViewModel(appContainer(), group) } }
    }
}
