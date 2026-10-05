package io.github.jpsubway.app.data.remote

import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Trip

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

    /** 역 시간표 → Trip. 같은 열차번호(방향별)의 발차 시각을 모아 역 순서대로 정렬한다. 열차 식별자가 없으면 버린다 */
    fun fromStationTimetables(dtos: List<OdptStationTimetable>, line: Line, known: Set<String>): List<Trip> {
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

    fun trainTypeKo(t: String?): String = when {
        t == null -> ""
        t.endsWith("LimitedExpress") -> "특급"
        t.endsWith("Express") -> "급행"
        t.endsWith("Rapid") -> "쾌속"
        t.endsWith("Local") -> "각역정차"
        else -> ""
    }
}
