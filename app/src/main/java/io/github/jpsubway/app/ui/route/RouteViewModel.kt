package io.github.jpsubway.app.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.core.i18n.KoreaStrings
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.RouteStrings
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.arrival.ArrivalEstimator
import io.github.jpsubway.app.domain.arrival.minutesLabel
import io.github.jpsubway.app.domain.model.ArrivalSource
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.domain.routing.Journey
import io.github.jpsubway.app.domain.routing.RideLeg
import io.github.jpsubway.app.domain.seoul.LiveArrival
import io.github.jpsubway.app.domain.seoul.SeoulArrival
import io.github.jpsubway.app.domain.seoul.SeoulLive
import io.github.jpsubway.app.domain.transfer.QuickTransfer
import io.github.jpsubway.app.ui.common.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 경로의 승차 구간별 실시간 도착 표시 키: 승차역 | 노선 | 다음 정차역 (같은 방면 열차) */
fun legLiveKey(leg: RideLeg): String =
    "${leg.fromStationId}|${leg.trip.lineId}|${leg.trip.stops.getOrNull(leg.boardIndex + 1)?.stationId}"

/** 환승(앞 승차 구간 a → 다음 승차 구간 b)별 빠른 환승 표시 키 */
fun quickKey(a: RideLeg, b: RideLeg): String = "${a.trip.id}|${a.alightIndex}|${b.trip.id}|${b.boardIndex}"

/** 서울 실시간 도착 한 건을 짧게 (예: "3분 후", "곧 도착") */
fun liveShort(a: LiveArrival, nowEpochSec: Long, s: Strings): String {
    val remain = a.etaEpochSec?.let { it - nowEpochSec }
    return when {
        a.code == "1" -> s.arrived
        a.code == "0" -> s.arrivingSoon
        remain != null && remain > 30 -> s.minutesLater(((remain + 59) / 60).toInt())
        remain != null -> s.arrivingSoon
        a.code == "3" || a.code == "4" || a.code == "5" -> s.arrivingSoon
        else -> a.stopsAway?.let { s.phaseStopsAway(it) } ?: s.phaseEnRoute
    }
}

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
        /** 승차 구간(legLiveKey)별 실시간 도착 요약 (예: "실시간 · 3분 후 · 9분 후") */
        val legLive: Map<String, String> = emptyMap(),
        /** 마지막으로 경로·실시간 정보를 갱신한 시각 (epoch 초) */
        val updatedEpochSec: Long = 0,
        val refreshing: Boolean = false,
        /** 시간표 출처·범위 안내 (서울) */
        val sourceNote: String? = null,
        /** 환승(quickKey)별 빠른 환승 위치 (예: "빠른 환승 6-1 (6번 칸 1번 문)") — 데이터가 있는 지역만 */
        val quick: Map<String, String> = emptyMap(),
    )

    private val estimator = ArrivalEstimator(perDirection = 2)
    private val offsetMin = MutableStateFlow(0)
    private val refreshTick = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)
    private val minuteTick = c.session.tick.map { it.seconds / 60 }.distinctUntilChanged()
    private val languages = combine(Lang.ui, Lang.names) { u, n -> u to n }

    private val base: StateFlow<Ui> = combine(
        c.session.state, c.routeSelection.from, c.routeSelection.to, offsetMin,
        combine(minuteTick, languages, refreshTick) { m, _, r -> m to r },
    ) { s, f, t, off, _ ->
        compute(s, f, t, off)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui(loading = true))

    /** 서울: 승차역 실시간 도착정보 (경로가 바뀌거나 새로고침하면 즉시, 그 뒤 30초마다) */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val koreaLive: StateFlow<Pair<Map<String, String>, Long>> = combine(
        base.map { it.journeys to it.network }.distinctUntilChanged(), refreshTick, languages,
    ) { jn, _, _ -> jn }
        .flatMapLatest { (journeys, net) ->
            val korea = (c.session.state.value as? RegionSession.State.Ready)?.data?.isLiveArrivals == true
            if (!korea || net == null || journeys.isEmpty()) {
                flowOf(emptyMap<String, String>() to 0L)
            } else {
                flow {
                    while (true) {
                        emit(fetchKorea(journeys, net))
                        delay(30_000)
                    }
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap<String, String>() to 0L)

    val ui: StateFlow<Ui> = combine(base, c.session.realtime, c.session.tick, koreaLive, refreshing) { b, rt, now, kr, r ->
        val d = (c.session.state.value as? RegionSession.State.Ready)?.data
        when {
            d == null || b.journeys.isEmpty() -> b.copy(refreshing = r)
            // 한국: 시간표 예측(실시간 API 가 없는 인천 노선 등) 위에 실시간 도착정보를 덮어쓴다
            d.isLiveArrivals -> b.copy(
                legLive = japanLive(d, b.journeys, rt, now.seconds) + kr.first,
                updatedEpochSec = maxOf(b.updatedEpochSec, kr.second),
                refreshing = r,
            )
            else -> b.copy(
                legLive = japanLive(d, b.journeys, rt, now.seconds),
                updatedEpochSec = maxOf(b.updatedEpochSec, rt.fetchedAtEpochSec),
                refreshing = r,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui(loading = true))

    private fun compute(s: RegionSession.State, f: String?, t: String?, off: Int): Ui {
        val str = Strings.current
        val d = (s as? RegionSession.State.Ready)?.data
            ?: return Ui(loading = s is RegionSession.State.Loading, message = (s as? RegionSession.State.Error)?.message, from = f, to = t, offsetMin = off)
        val net = d.network
        val lang = Lang.ui.value
        val base = Ui(
            from = f, to = t,
            fromName = f?.let { net.groupName(it).display() }.orEmpty(),
            toName = t?.let { net.groupName(it).display() }.orEmpty(),
            offsetMin = off, network = net, isDemo = d.timetable.isDemo,
            updatedEpochSec = ServiceClock.now().epochSec,
            sourceNote = if (d.isLiveArrivals) RouteStrings.seoulSource(lang) else null,
        )
        // 한국: 앱에 열차운행시각표가 없으면 경로 검색 불가
        if (d.isLiveArrivals && d.timetable.trips.isEmpty()) return base.copy(message = KoreaStrings.routeNotSupported(lang))
        if (f == null || t == null) return base.copy(message = str.chooseFromTo)
        if (f == t) return base.copy(message = str.sameFromTo)
        val depart = ServiceClock.now().seconds + off * 60
        val fromIds = net.stationsByGroup[f].orEmpty().map { it.id }
        val toIds = net.stationsByGroup[t].orEmpty().map { it.id }
        val found = d.router.searchAlternatives(fromIds, toIds, depart, count = 3)
        // 빠른 환승: 연속한 두 승차 구간마다 (사이의 도보 환승과 무관하게)
        val rows = c.quickTransfers.rows(d.region.id)
        val quick = HashMap<String, String>()
        if (rows.isNotEmpty()) {
            for (j in found) {
                val rides = j.legs.filterIsInstance<RideLeg>()
                for (k in 0 until rides.size - 1) {
                    QuickTransfer.find(rows, net, rides[k], rides[k + 1])?.let {
                        quick[quickKey(rides[k], rides[k + 1])] = RouteStrings.quickTransfer(lang, it.car, it.door)
                    }
                }
            }
        }
        return base.copy(
            departSec = depart,
            journeys = found,
            message = if (found.isEmpty()) str.noJourney else null,
            quick = quick,
        )
    }

    /** 시간표 기반 승차역·노선·방면별 도착 예측 (역 화면과 같은 계산 — 시간표 + 실시간 지연이 있으면 반영) */
    private fun japanLive(d: RegionSession.Data, journeys: List<Journey>, rt: RealtimeSnapshot, now: Int): Map<String, String> {
        val s = Strings.current
        val out = HashMap<String, String>()
        for (j in journeys) for (leg in j.legs) {
            if (leg !is RideLeg) continue
            val key = legLiveKey(leg)
            if (key in out) continue
            val line = d.network.lineById[leg.trip.lineId] ?: continue
            val next = leg.trip.stops.getOrNull(leg.boardIndex + 1)?.stationId
            val boards = estimator.boards(line, leg.fromStationId, d.tripsByLine[line.id].orEmpty(), rt, now)
            val b = boards.firstOrNull { it.nextStationId == next }
                ?: boards.firstOrNull { it.direction.id == leg.trip.directionId }
                ?: continue
            if (b.arrivals.isEmpty()) continue
            val live = b.arrivals.any { it.source == ArrivalSource.REALTIME }
            out[key] = (if (live) s.realtime else s.timetable) + " · " + b.arrivals.take(2).joinToString(" · ") { it.minutesLabel() }
        }
        return out
    }

    /** 한국: 승차역 실시간 도착정보 API → 같은 노선·방면(다음 정차역) 열차 2대 */
    private suspend fun fetchKorea(journeys: List<Journey>, net: Network): Pair<Map<String, String>, Long> {
        val s = Strings.current
        val fetched = System.currentTimeMillis() / 1000
        val legs = journeys.flatMap { j -> j.legs.filterIsInstance<RideLeg>() }
            .filter { SeoulLive.lineKeyBySubwayId.containsValue(SeoulLive.lineKey(it.trip.lineId)) }
            .distinctBy { legLiveKey(it) }
        val byStation = HashMap<String, List<SeoulArrival>>()
        val out = HashMap<String, String>()
        for (leg in legs) {
            val st = net.stationById[leg.fromStationId] ?: continue
            val api = SeoulLive.apiStationName(st.name.ko)
            val rows = byStation.getOrPut(api) {
                try {
                    c.seoul.arrivals(api)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val boards = SeoulLive.boards(rows, SeoulLive.lineKey(leg.trip.lineId), fetched)
            val next = leg.trip.stops.getOrNull(leg.boardIndex + 1)?.let { net.stationById[it.stationId]?.name?.ko }
                ?.let(SeoulLive::apiStationName)
            val dest = net.stationById[leg.trip.destinationId]?.name?.ko?.let(SeoulLive::apiStationName)
            val b = boards.firstOrNull { SeoulLive.apiStationName(it.heading) == next }
                ?: boards.firstOrNull { bd -> bd.arrivals.any { SeoulLive.apiStationName(it.destination) == dest } }
                ?: continue
            if (b.arrivals.isEmpty()) continue
            out[legLiveKey(leg)] = s.realtime + " · " + b.arrivals.take(2).joinToString(" · ") { liveShort(it, fetched, s) }
        }
        return out to fetched
    }

    fun setOffset(min: Int) {
        offsetMin.value = min
    }

    fun swap() = c.routeSelection.swap()

    /** 당겨서 새로고침: 실시간 정보를 다시 받고 경로를 현재 시각으로 다시 계산한다 */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                c.session.refreshRealtime()
            } finally {
                refreshTick.value += 1
                delay(700)
                refreshing.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { RouteViewModel(appContainer()) } }
    }
}
