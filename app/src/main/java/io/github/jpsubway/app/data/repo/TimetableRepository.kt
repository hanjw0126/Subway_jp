package io.github.jpsubway.app.data.repo

import android.content.Context
import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.data.demo.DemoTimetableSource
import io.github.jpsubway.app.data.remote.OdptClient
import io.github.jpsubway.app.data.remote.OdptMapper
import io.github.jpsubway.app.data.remote.OdptTrainTimetable
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RegionInfo
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 시간표 공급자: ODPT(토큰/중계 서버 + 실시간 지원 지역) → 파일 캐시(7일) → 데모 시간표 순으로 폴백.
 * 캐시 키에 노선 목록 해시를 넣어, 앱 업데이트로 노선이 추가되면(JR·사철 등) 이전 캐시를 버리고 다시 받는다.
 */
class TimetableRepository(
    context: Context,
    private val json: Json,
    private val odpt: OdptClient,
    private val settings: SettingsRepository,
) {
    private val dir = File(context.filesDir, "timetables").apply { mkdirs() }
    private val mem = ConcurrentHashMap<String, Timetable>()
    private val mutex = Mutex()

    suspend fun get(region: RegionInfo, network: Network, dayType: DayType, forceRefresh: Boolean = false): Timetable =
        mutex.withLock { withContext(Dispatchers.IO) { load(region, network, dayType, forceRefresh) } }

    fun clearMemory() = mem.clear()

    private suspend fun load(region: RegionInfo, network: Network, dayType: DayType, force: Boolean): Timetable {
        val key = cacheKey(region, network, dayType)
        if (!force) mem[key]?.let { return it }
        val token = settings.consumerKey()
        val result = if (region.realtime && token.isNotBlank()) {
            val file = File(dir, "$key.json")
            purgeStale(region, dayType, file.name)
            val cached = readCache(file)
            if (!force && cached != null && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
                cached.copy(source = "odpt-cache")
            } else {
                try {
                    fetch(network, dayType, token).also { file.writeText(json.encodeToString(Timetable.serializer(), it)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    cached?.copy(source = "odpt-cache") ?: DemoTimetableSource.generate(network, dayType)
                }
            }
        } else {
            DemoTimetableSource.generate(network, dayType)
        }
        mem[key] = result
        return result
    }

    private fun cacheKey(region: RegionInfo, network: Network, dayType: DayType): String {
        val sig = network.lines.joinToString(",") { it.id }.hashCode().toUInt().toString(16)
        return "${region.id}_${dayType.name}_v${CACHE_VERSION}_$sig"
    }

    /** 노선 구성이 다른(이전 버전) 캐시 파일 삭제 */
    private fun purgeStale(region: RegionInfo, dayType: DayType, keep: String) {
        val prefix = "${region.id}_${dayType.name}"
        dir.listFiles()?.filter { it.name.startsWith(prefix) && it.name != keep }?.forEach { it.delete() }
    }

    private fun readCache(file: File): Timetable? =
        if (!file.exists()) null else runCatching { json.decodeFromString(Timetable.serializer(), file.readText()) }.getOrNull()

    /** 노선별로 병렬(최대 PARALLEL 개) 수신. 노선 하나가 실패해도 나머지는 계속 받는다 */
    private suspend fun fetch(network: Network, dayType: DayType, token: String): Timetable = coroutineScope {
        val known = network.stationById.keys
        val gate = Semaphore(PARALLEL)
        val trips = network.lines.map { line ->
            async { gate.withPermit { fetchLine(network, line, dayType, token, known) } }
        }.awaitAll().flatten()
        check(trips.isNotEmpty()) { "ODPT 시간표가 비어 있습니다" }
        Timetable(network.regionId, dayType.name, "odpt", System.currentTimeMillis() / 1000, trips)
    }

    private suspend fun fetchLine(network: Network, line: Line, dayType: DayType, token: String, known: Set<String>): List<Trip> {
        for (cal in dayType.odptCalendars) {
            val dtos = trainTimetables(line, cal, token)
            if (dtos.isNotEmpty()) return dtos.mapNotNull { OdptMapper.toTrip(it, line, known) }
        }
        // 열차 시간표가 없는 노선(챌린지 2026 세이부·도큐·오다큐·게이큐 등): 역 시간표로 열차를 재구성
        for (cal in dayType.odptCalendars) {
            val st = runCatching { odpt.stationTimetables(line.id, cal, token) }.getOrDefault(emptyList())
            if (st.isNotEmpty()) return OdptMapper.fromStationTimetables(st, line, known) { a, b -> distanceM(network, a, b) }
        }
        return emptyList()
    }

    /** ODPT 응답 상한(1000건)에 걸리면 방향별로 나눠 다시 받는다 (JR·도부 등 운행 횟수가 많은 노선) */
    private suspend fun trainTimetables(line: Line, cal: String, token: String): List<OdptTrainTimetable> {
        val all = runCatching { odpt.trainTimetables(line.id, cal, token) }.getOrDefault(emptyList())
        if (all.size < PAGE_LIMIT) return all
        val split = listOf(line.directions.asc.id, line.directions.desc.id).distinct().flatMap { d ->
            runCatching { odpt.trainTimetables(line.id, cal, token, d) }.getOrDefault(emptyList())
        }
        return if (split.size > all.size) split.distinctBy { it.id.ifBlank { "${it.trainNumber}|${it.railDirection}" } } else all
    }

    private fun distanceM(network: Network, a: String, b: String): Double? {
        val s = network.stationById[a] ?: return null
        val t = network.stationById[b] ?: return null
        val lat1 = Math.toRadians(s.lat)
        val lon1 = Math.toRadians(s.lon)
        val lat2 = Math.toRadians(t.lat)
        val lon2 = Math.toRadians(t.lon)
        val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(h))
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
        // 3: 순환선 방향 판정 수정 — 이전 캐시에 잘못 분류된 열차가 남아 있으므로 다시 받는다
        const val CACHE_VERSION = 3
        const val PARALLEL = 6
        const val PAGE_LIMIT = 1000
    }
}
