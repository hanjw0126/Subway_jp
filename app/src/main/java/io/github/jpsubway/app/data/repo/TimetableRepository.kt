package io.github.jpsubway.app.data.repo

import android.content.Context
import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.data.demo.DemoTimetableSource
import io.github.jpsubway.app.data.remote.OdptClient
import io.github.jpsubway.app.data.remote.OdptMapper
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.RegionInfo
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 시간표 공급자: ODPT(토큰 있음 + 실시간 지원 지역) → 파일 캐시(7일) → 데모 시간표 순으로 폴백.
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
        val key = "${region.id}_${dayType.name}"
        if (!force) mem[key]?.let { return it }
        val token = settings.consumerKey()
        val result = if (region.realtime && token.isNotBlank()) {
            val file = File(dir, "$key.json")
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

    private fun readCache(file: File): Timetable? =
        if (!file.exists()) null else runCatching { json.decodeFromString(Timetable.serializer(), file.readText()) }.getOrNull()

    private suspend fun fetch(network: Network, dayType: DayType, token: String): Timetable {
        val known = network.stationById.keys
        val trips = mutableListOf<Trip>()
        for (line in network.lines) {
            var got = false
            for (cal in dayType.odptCalendars) {
                // 노선 하나가 실패해도(미제공·일시 오류) 나머지 노선은 계속 받는다
                val dtos = runCatching { odpt.trainTimetables(line.id, cal, token) }.getOrDefault(emptyList())
                if (dtos.isNotEmpty()) {
                    dtos.mapNotNullTo(trips) { OdptMapper.toTrip(it, line, known) }
                    got = true
                    break
                }
            }
            if (got) continue
            // 열차 시간표가 없는 노선(챌린지 2026 일부 사철 등): 역 시간표로 열차를 재구성
            for (cal in dayType.odptCalendars) {
                val st = runCatching { odpt.stationTimetables(line.id, cal, token) }.getOrDefault(emptyList())
                if (st.isNotEmpty()) {
                    trips += OdptMapper.fromStationTimetables(st, line, known)
                    break
                }
            }
        }
        check(trips.isNotEmpty()) { "ODPT 시간표가 비어 있습니다" }
        return Timetable(network.regionId, dayType.name, "odpt", System.currentTimeMillis() / 1000, trips)
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }
}
