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

/**
 * 역 도착정보. 표시 중인 역(currentGroup)은 화면 안에서 바뀔 수 있다 (좌우 스와이프로 이웃 역 이동).
 * initialLine: 처음 선택할 노선 (노선 전체 지도에서 역을 눌러 들어온 경우 등)
 */
class StationViewModel(
    private val c: AppContainer,
    initialGroup: String,
    initialLine: String?,
) : ViewModel() {
    private val estimator = ArrivalEstimator(perDirection = 2)
    private val currentGroup = MutableStateFlow(initialGroup)
    private val selectedLine = MutableStateFlow(initialLine)

    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val group: String = "",
        val name: LocalizedName = LocalizedName(),
        val lines: List<Line> = emptyList(),
        val line: Line? = null,
        val station: Station? = null,
        val boards: List<DirectionBoard> = emptyList(),
        /** 역 띠 왼쪽(노선 순번 이전) / 오른쪽(다음) 이웃 역. 종점이면 null, 순환선은 이어짐 */
        val leftStationId: String? = null,
        val rightStationId: String? = null,
        val status: LineStatus? = null,
        val sourceLabel: String = "",
        val realtimeError: String? = null,
        val network: Network? = null,
        val nowSec: Int = 0,
    )

    val ui: StateFlow<Ui> = combine(
        c.session.state, c.session.realtime, c.session.tick, currentGroup, selectedLine,
    ) { s, rt, now, g, sel ->
        when (s) {
            RegionSession.State.Loading -> Ui()
            is RegionSession.State.Error -> Ui(loading = false, error = s.message)
            is RegionSession.State.Ready -> build(s.data, rt, now.seconds, g, sel)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    private fun build(d: RegionSession.Data, rt: RealtimeSnapshot, now: Int, group: String, sel: String?): Ui {
        val net = d.network
        val stations = net.stationsByGroup[group].orEmpty()
        val lines = stations.mapNotNull { net.lineById[it.lineId] }.distinctBy { it.id }
        if (lines.isEmpty()) return Ui(loading = false, error = "역 정보를 찾을 수 없습니다", group = group)
        val line = lines.firstOrNull { it.id == sel } ?: lines.first()
        val station = stations.first { it.lineId == line.id }
        val boards = estimator.boards(line, station.id, d.tripsByLine[line.id].orEmpty(), rt, now)
        val asc = boards.getOrNull(1) // asc 방향: prev = 순번 이전 역, next = 다음 역
        val label = when {
            d.timetable.isDemo -> "데모 시간표 기준 (실제 운행과 다를 수 있음)"
            rt.available -> "시간표 + 실시간 지연 반영"
            else -> "시간표 기준"
        }
        return Ui(
            loading = false, group = group, name = station.name, lines = lines, line = line, station = station, boards = boards,
            leftStationId = asc?.prevStationId, rightStationId = asc?.nextStationId,
            status = rt.statuses[line.id], sourceLabel = label, realtimeError = rt.error, network = net, nowSec = now,
        )
    }

    fun selectLine(id: String) {
        selectedLine.value = id
    }

    /** 같은 노선의 이웃 역(역 ID)으로 이동. 지금 보고 있는 노선을 그대로 유지한다 */
    fun moveTo(stationId: String) {
        val cur = ui.value
        val g = cur.network?.stationById?.get(stationId)?.group ?: return
        cur.line?.let { selectedLine.value = it.id }
        currentGroup.value = g
    }

    /** @return 출발·도착이 모두 정해졌으면 true */
    fun setFrom(): Boolean {
        val group = currentGroup.value
        if (c.routeSelection.to.value == group) c.routeSelection.setTo(null)
        c.routeSelection.setFrom(group)
        return c.routeSelection.isComplete
    }

    fun setTo(): Boolean {
        val group = currentGroup.value
        if (c.routeSelection.from.value == group) c.routeSelection.setFrom(null)
        c.routeSelection.setTo(group)
        return c.routeSelection.isComplete
    }

    companion object {
        fun factory(group: String, line: String? = null) = viewModelFactory {
            initializer { StationViewModel(appContainer(), group, line) }
        }
    }
}
