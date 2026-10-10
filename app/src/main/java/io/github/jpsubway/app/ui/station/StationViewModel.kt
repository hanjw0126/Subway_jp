package io.github.jpsubway.app.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.core.i18n.KoreaStrings
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.arrival.ArrivalEstimator
import io.github.jpsubway.app.domain.model.*
import io.github.jpsubway.app.domain.seoul.LiveBoard
import io.github.jpsubway.app.domain.seoul.SeoulLive
import io.github.jpsubway.app.ui.common.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/**
 * 역 도착정보. 표시 중인 역(currentGroup)은 화면 안에서 바뀔 수 있다 (좌우 스와이프로 이웃 역 이동).
 * initialLine: 처음 선택할 노선 (노선 전체 지도에서 역을 눌러 들어온 경우 등)
 * 일본: 시간표 + ODPT 실시간 → boards / 한국(서울): 역별 실시간 도착 API(15초) → live
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
        val nowEpochSec: Long = 0,
        /** 한국(서울): 시간표 대신 실시간 도착정보(live)를 보여 준다 */
        val isLive: Boolean = false,
    )

    /** 서울 실시간 도착정보 */
    data class Live(
        val boards: List<LiveBoard> = emptyList(),
        val error: String? = null,
        val loading: Boolean = false,
        val fetchedEpochSec: Long = 0,
    )

    val ui: StateFlow<Ui> = combine(
        c.session.state, c.session.realtime, c.session.tick, currentGroup, selectedLine,
    ) { s, rt, now, g, sel ->
        when (s) {
            RegionSession.State.Loading -> Ui()
            is RegionSession.State.Error -> Ui(loading = false, error = s.message)
            is RegionSession.State.Ready -> build(s.data, rt, now.seconds, now.epochSec, g, sel)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    private data class LiveKey(val station: String, val lineKey: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<Live> = ui
        .map { u ->
            val st = u.station
            val ln = u.line
            if (u.isLive && st != null && ln != null) LiveKey(SeoulLive.apiStationName(st.name.ko), SeoulLive.lineKey(ln.id)) else null
        }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(Live())
            } else {
                flow {
                    emit(Live(loading = true))
                    var last = Live()
                    while (true) {
                        val fetched = System.currentTimeMillis() / 1000
                        last = try {
                            val rows = c.seoul.arrivals(key.station)
                            Live(boards = SeoulLive.boards(rows, key.lineKey, fetched), fetchedEpochSec = fetched)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 일시 오류면 직전 도착정보는 그대로 두고 오류만 표시
                            last.copy(error = e.message ?: e.javaClass.simpleName)
                        }
                        emit(last)
                        delay(LIVE_INTERVAL_MS)
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Live())

    private fun build(d: RegionSession.Data, rt: RealtimeSnapshot, now: Int, nowEpoch: Long, group: String, sel: String?): Ui {
        val str = Strings.current
        val net = d.network
        val stations = net.stationsByGroup[group].orEmpty()
        val lines = stations.mapNotNull { net.lineById[it.lineId] }.distinctBy { it.id }
        if (lines.isEmpty()) return Ui(loading = false, error = str.stationNotFound, group = group)
        val line = lines.firstOrNull { it.id == sel } ?: lines.first()
        val station = stations.first { it.lineId == line.id }
        val isLive = d.isLiveArrivals
        val boards = if (isLive) emptyList() else estimator.boards(line, station.id, d.tripsByLine[line.id].orEmpty(), rt, now)
        // 역 띠·스와이프의 좌우는 열차 방향과 관계없이 노선 순번으로 고정 (순환선은 끝↔처음 연결)
        val i = line.stations.indexOf(station.id)
        val n = line.stations.size
        fun at(k: Int): String? = when {
            i < 0 -> null
            line.loop -> line.stations[((k % n) + n) % n]
            k in 0 until n -> line.stations[k]
            else -> null
        }
        val label = when {
            isLive -> KoreaStrings.sourceLive(Lang.ui.value)
            d.timetable.isDemo -> str.sourceDemo
            rt.available -> str.sourceRealtime
            else -> str.sourceSchedule
        }
        return Ui(
            loading = false, group = group, name = station.name, lines = lines, line = line, station = station, boards = boards,
            leftStationId = at(i - 1), rightStationId = at(i + 1),
            status = rt.statuses[line.id], sourceLabel = label, realtimeError = if (isLive) null else rt.error,
            network = net, nowSec = now, nowEpochSec = nowEpoch, isLive = isLive,
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
        private const val LIVE_INTERVAL_MS = 15_000L

        fun factory(group: String, line: String? = null) = viewModelFactory {
            initializer { StationViewModel(appContainer(), group, line) }
        }
    }
}
