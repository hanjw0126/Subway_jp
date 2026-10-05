package io.github.jpsubway.app.data.remote

import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Trip
import kotlin.math.abs

object OdptMapper {
    /** ODPT 열차 시간표 → Trip. 네트워크에 없는 역(직통 운행 구간)은 제외하고, 방향은 역 순서로 판정 */
    fun toTrip(dto: OdptTrainTimetable, line: Line, known: Set<String>): Trip? {
        val stops = ArrayList<StopTime>(dto.objects.size)
        var prev: Int? = null
        for (o in dto.objects) {
            val sid = o.departureStation ?: o.arrivalStation ?: continue
            val depStr = o.departureTime ?: o.arrivalTime ?: continue
            val arrStr = o.arrivalTime ?: depStr
            val arr = ServiceClock.parse(arrStr, prev)
            val dep = ServiceClock.parse(depStr, arr).coerceAtLeast(arr)
            prev = dep
            if (sid in known) stops += StopTime(sid, arr, dep)
        }
        if (stops.size < 2) return null
        val i0 = line.stations.indexOf(stops[0].stationId)
        val i1 = line.stations.indexOf(stops[1].stationId)
        val asc = if (i0 >= 0 && i1 >= 0) i1 > i0 else dto.railDirection == line.directions.asc.id
        val dir = if (asc) line.directions.asc else line.directions.desc
        return Trip(
            id = dto.id.ifBlank { "${line.id}.${dto.trainNumber}" },
            lineId = line.id,
            trainNumber = dto.trainNumber,
            directionId = dir.id,
            destinationId = dto.destinationStation?.firstOrNull() ?: stops.last().stationId,
            trainType = trainTypeKo(dto.trainType),
            stops = stops,
        )
    }

    /**
     * 역 시간표 → Trip.
     * - 열차번호가 있으면(JR 등) 같은 열차번호의 발차 시각을 모은다.
     * - 열차번호가 없으면(세이부·도큐·오다큐 등 챌린지 2026 일부) 같은 (종별, 행선지) 열차를
     *   역 순서대로 따라가며 역간 거리로 추정한 소요시간에 가장 가까운 발차를 이어 붙인다.
     */
    fun fromStationTimetables(
        dtos: List<OdptStationTimetable>,
        line: Line,
        known: Set<String>,
        distM: (String, String) -> Double? = { _, _ -> null },
    ): List<Trip> {
        val numbered = dtos.any { st -> st.objects.any { !it.trainNumber.isNullOrBlank() || !it.train.isNullOrBlank() } }
        return if (numbered) byTrainNumber(dtos, line, known) else chainByTime(dtos, line, known, distM)
    }

    private fun byTrainNumber(dtos: List<OdptStationTimetable>, line: Line, known: Set<String>): List<Trip> {
        val idx = line.stations.withIndex().associate { it.value to it.index }
        val byTrain = LinkedHashMap<String, MutableList<StopTime>>()
        val meta = HashMap<String, Triple<String, String, String>>()
        for (st in dtos) {
            if (st.station !in known) continue
            val asc = st.railDirection == line.directions.asc.id
            val dir = if (asc) line.directions.asc else line.directions.desc
            for (o in st.objects) {
                val tStr = o.departureTime ?: o.arrivalTime ?: continue
                val train = o.trainNumber?.takeIf { it.isNotBlank() } ?: o.train?.substringAfterLast('.')?.takeIf { it.isNotBlank() } ?: continue
                val t = ServiceClock.parse(tStr, null)
                val key = dir.id + "#" + train
                byTrain.getOrPut(key) { mutableListOf() }.add(StopTime(st.station, t, t))
                if (key !in meta) meta[key] = Triple(dir.id, o.destinationStation?.firstOrNull().orEmpty(), trainTypeKo(o.trainType))
            }
        }
        val out = ArrayList<Trip>(byTrain.size)
        for ((key, stops) in byTrain) {
            val m = meta[key] ?: continue
            val sign = if (m.first == line.directions.asc.id) 1 else -1
            val sorted = stops.distinctBy { it.stationId }.sortedBy { (idx[it.stationId] ?: 0) * sign }
            if (sorted.size < 2) continue
            out += Trip(
                id = line.id + ".st." + key,
                lineId = line.id,
                trainNumber = key.substringAfter('#'),
                directionId = m.first,
                destinationId = m.second.ifBlank { sorted.last().stationId },
                trainType = m.third,
                stops = sorted,
            )
        }
        return out
    }

    private class Chain(val type: String, val dest: String) {
        val stops = ArrayList<StopTime>()
    }

    private class Dep(val t: Int, val type: String, val dest: String, val origin: Boolean)

    private fun chainByTime(
        dtos: List<OdptStationTimetable>,
        line: Line,
        known: Set<String>,
        distM: (String, String) -> Double?,
    ): List<Trip> {
        val idx = line.stations.withIndex().associate { it.value to it.index }
        val out = ArrayList<Trip>()
        dtos.filter { it.station in known && it.station in idx }
            .groupBy { it.railDirection.orEmpty() }
            .forEach { (dirId, sts) ->
                val asc = dirId == line.directions.asc.id
                val dir = if (asc) line.directions.asc else line.directions.desc
                val sign = if (asc) 1 else -1
                val active = ArrayList<Chain>()
                val chains = ArrayList<Chain>()
                for (st in sts.sortedBy { idx.getValue(it.station) * sign }) {
                    val deps = st.objects.mapNotNull { o ->
                        val ts = o.departureTime ?: o.arrivalTime ?: return@mapNotNull null
                        Dep(ServiceClock.parse(ts, null), o.trainType.orEmpty(), o.destinationStation?.firstOrNull().orEmpty(), o.isOrigin == true)
                    }.sortedBy { it.t }
                    val used = HashSet<Chain>()
                    for (d in deps) {
                        var best: Chain? = null
                        var bestErr = Int.MAX_VALUE
                        if (!d.origin) {
                            for (c in active) {
                                if (c in used || c.type != d.type || c.dest != d.dest) continue
                                val last = c.stops.last()
                                val est = runSec(distM(last.stationId, st.station))
                                val gap = d.t - last.dep
                                if (gap < MIN_RUN_SEC || gap > est * 3 + 600) continue
                                val err = abs(gap - est)
                                if (err < bestErr) {
                                    bestErr = err
                                    best = c
                                }
                            }
                        }
                        val c = best ?: Chain(d.type, d.dest).also {
                            chains += it
                            active += it
                        }
                        c.stops += StopTime(st.station, d.t, d.t)
                        used += c
                    }
                    active.removeAll { it.dest == st.station }
                }
                chains.forEachIndexed { i, c ->
                    val last = c.stops.last()
                    val di = idx[c.dest]
                    val li = idx.getValue(last.stationId)
                    // 종착역은 발차 시각이 없으므로 도착 시각을 거리로 추정해 붙인다 (경로 검색에서 종착역 도착 가능)
                    if (di != null && c.dest in known && (di - li) * sign > 0) {
                        val arr = last.dep + runSec(distM(last.stationId, c.dest))
                        c.stops += StopTime(c.dest, arr, arr)
                    }
                    if (c.stops.size < 2) return@forEachIndexed
                    out += Trip(
                        id = "${line.id}.st.${dir.id.substringAfterLast(':')}.$i",
                        lineId = line.id,
                        trainNumber = "",
                        directionId = dir.id,
                        destinationId = c.dest.ifBlank { c.stops.last().stationId },
                        trainType = trainTypeKo(c.type.ifBlank { null }),
                        stops = c.stops.toList(),
                    )
                }
            }
        return out
    }

    /** 역간 거리(m) → 추정 소요시간(초): 평균 표정속도 12 m/s + 정차 40초 */
    private fun runSec(d: Double?): Int = if (d == null) 150 else (d / 12.0 + 40.0).toInt().coerceAtLeast(MIN_RUN_SEC)

    private const val MIN_RUN_SEC = 30

    fun trainTypeKo(t: String?): String = when {
        t == null -> ""
        t.endsWith("LimitedExpress") -> "특급"
        t.endsWith("Express") -> "급행"
        t.endsWith("Rapid") -> "쾌속"
        t.endsWith("Local") -> "각역정차"
        else -> ""
    }
}
