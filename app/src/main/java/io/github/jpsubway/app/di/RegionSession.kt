package io.github.jpsubway.app.di

import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.data.demo.DemoTimetableSource
import io.github.jpsubway.app.domain.model.MapLayout
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.RegionInfo
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip
import io.github.jpsubway.app.domain.routing.RaptorIndex
import io.github.jpsubway.app.domain.routing.RaptorRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 현재 지역의 노선망·노선도·시간표·실시간 정보를 한 곳에서 관리 (화면 간 공유) */
class RegionSession(private val c: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    class Data(
        val region: RegionInfo,
        val network: Network,
        val layout: MapLayout,
        val timetable: Timetable,
        val dayType: DayType,
        val timetableError: String?,
    ) {
        val tripsByLine: Map<String, List<Trip>> = timetable.trips.groupBy { it.lineId }
        val router: RaptorRouter by lazy { RaptorRouter(RaptorIndex.build(network, timetable)) }

        /** 한국(서울) 지역: 시간표 없이 역별 실시간 도착정보 API 를 쓴다 */
        val isLiveArrivals: Boolean get() = region.country == "kr"
    }

    sealed interface State {
        data object Loading : State
        data class Ready(val data: Data) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _realtime = MutableStateFlow(RealtimeSnapshot())
    val realtime: StateFlow<RealtimeSnapshot> = _realtime.asStateFlow()

    /** 10초마다 갱신되는 현재 시각 ("N분 후" 재계산용) */
    val tick: StateFlow<ServiceClock.Now> = flow {
        while (true) {
            emit(ServiceClock.now())
            delay(10_000)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ServiceClock.now())

    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            merge(
                c.settings.regionId.map { it to false },
                reloadRequests.map { c.settings.regionId.value to true },
            ).collectLatest { (id, force) -> load(id, force) }
        }
    }

    /** 시간표를 강제로 다시 받음 */
    fun reload() {
        reloadRequests.tryEmit(Unit)
    }

    private suspend fun load(regionId: String, force: Boolean) {
        _state.value = State.Loading
        _realtime.value = RealtimeSnapshot()
        try {
            val region = c.networks.region(regionId)
            val network = c.networks.network(region.id)
            val layout = c.networks.layout(region.id)
            val now = ServiceClock.now()
            val day = DayType.of(now.serviceDate)
            var err: String? = null
            val tt = if (region.country == "kr") {
                // 서울: 시간표 없음 (도착정보는 역 화면에서 실시간 API 로)
                Timetable(region.id, day.name, "live", now.epochSec, emptyList())
            } else {
                try {
                    c.timetables.get(region, network, day, force)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err = e.message ?: e.javaClass.simpleName
                    DemoTimetableSource.generate(network, day)
                }
            }
            _state.value = State.Ready(Data(region, network, layout, tt, day, err))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 화면이 보이는 동안 호출: 30초마다 열차 위치·지연·운행정보 갱신, 운행일 변경 시 시간표 재로딩 */
    suspend fun pollRealtime() {
        state.collectLatest { s ->
            val d = (s as? State.Ready)?.data ?: return@collectLatest
            while (true) {
                val now = ServiceClock.now()
                if (DayType.of(now.serviceDate) != d.dayType) {
                    reload()
                    return@collectLatest
                }
                // ODPT 실시간은 일본 지역만 (서울은 역 화면이 도착정보를 직접 받는다)
                if (!d.isLiveArrivals && d.region.realtime && c.settings.hasToken()) {
                    _realtime.value = try {
                        c.realtime.snapshot(d.region, d.network, now.epochSec)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RealtimeSnapshot(error = e.message ?: e.javaClass.simpleName)
                    }
                }
                delay(30_000)
            }
        }
    }
}
