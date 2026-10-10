package io.github.jpsubway.app.di

import io.github.jpsubway.app.BuildConfig
import android.content.Context
import io.github.jpsubway.app.data.remote.OdptClient
import io.github.jpsubway.app.data.remote.SeoulClient
import io.github.jpsubway.app.data.repo.NetworkRepository
import io.github.jpsubway.app.data.repo.RealtimeRepository
import io.github.jpsubway.app.data.repo.SettingsRepository
import io.github.jpsubway.app.data.repo.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 수동 DI 컨테이너 */
class AppContainer(context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    val settings = SettingsRepository(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val odpt = OdptClient(http, json, proxyBaseUrl = BuildConfig.ODPT_PROXY_URL)
    val seoul = SeoulClient(http, json, BuildConfig.ODPT_PROXY_URL)
    val networks = NetworkRepository(context, json)
    val timetables = TimetableRepository(context, json, odpt, settings)
    val realtime = RealtimeRepository(odpt, settings)
    val routeSelection = RouteSelection()
    val session: RegionSession by lazy { RegionSession(this) }

    /**
     * 메인 화면에서 국가를 고를 때: 그 나라에서 마지막으로 본 지역(없으면 첫 지역)으로 바꾼다.
     * @return 그 나라 지역이 없으면 false
     */
    fun selectCountry(country: String): Boolean {
        val regions = networks.regions.filter { it.country == country }
        if (regions.isEmpty()) return false
        val id = settings.lastRegion(country)?.takeIf { last -> regions.any { it.id == last } } ?: regions.first().id
        if (id != settings.regionId.value) routeSelection.clear()
        settings.setRegion(id, country)
        return true
    }
}

/** 노선도/역/검색 화면이 공유하는 출발·도착 선택 상태 (값 = 환승 그룹 ID) */
class RouteSelection {
    private val _from = MutableStateFlow<String?>(null)
    private val _to = MutableStateFlow<String?>(null)
    val from: StateFlow<String?> = _from.asStateFlow()
    val to: StateFlow<String?> = _to.asStateFlow()

    fun setFrom(group: String?) { _from.value = group }
    fun setTo(group: String?) { _to.value = group }
    fun swap() { val f = _from.value; _from.value = _to.value; _to.value = f }
    fun clear() { _from.value = null; _to.value = null }
    val isComplete: Boolean get() = _from.value != null && _to.value != null
}
