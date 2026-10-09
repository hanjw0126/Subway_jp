package io.github.jpsubway.app.domain.arrival

import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.domain.model.Arrival
import io.github.jpsubway.app.domain.model.ArrivalPhase
import io.github.jpsubway.app.domain.model.ArrivalSource
import io.github.jpsubway.app.domain.model.DirectionBoard
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.TrainPosition
import io.github.jpsubway.app.domain.model.Trip

/**
 * 시간표 + 실시간 정보 → "N분 후 도착".
 *  예측 도착 = 시간표 도착 + 실시간 지연(초)
 *  열차 위치: ODPT fromStation/toStation 이 있으면 그것으로, 없으면 시간표로 추정
 */
class ArrivalEstimator(private val perDirection: Int = 2, private val graceSec: Int = 20) {

    fun boards(line: Line, stationId: String, trips: List<Trip>, rt: RealtimeSnapshot, nowSec: Int): List<DirectionBoard> {
        val i = line.stations.indexOf(stationId)
        val n = line.stations.size
        fun at(k: Int): String? = when {
            i < 0 -> null
            line.loop -> line.stations[((k % n) + n) % n]
            k in 0 until n -> line.stations[k]
            else -> null
        }
        // 기본: desc(순번 감소) 열차는 i+1 에서 와서 i-1 로 간다. asc 는 반대.
        // 단, 오에도선(6자형)처럼 같은 방면이라도 구간에 따라 순번 방향이 다르면 이 역에서 실제로 달리는 방향을 따른다.
        return listOf(line.directions.desc to false, line.directions.asc to true).map { (dir, ascDefault) ->
            val dirTrips = trips.filter { it.lineId == line.id && it.directionId == dir.id }
            val up = movesUp(line, stationId, dirTrips) ?: ascDefault
            val prev = if (up) at(i - 1) else at(i + 1)
            val next = if (up) at(i + 1) else at(i - 1)
            val arrivals = dirTrips.asSequence()
                .mapNotNull { estimate(it, stationId, rt, nowSec) }
                .sortedBy { it.predictedSec }
                .take(perDirection)
                .toList()
            val deps = dirTrips.mapNotNull { t ->
                val si = t.stops.indexOfFirst { it.stationId == stationId }
                if (si < 0 || si == t.stops.lastIndex) null else t.stops[si].dep
            }
            DirectionBoard(dir, prev, next, arrivals, deps.minOrNull(), deps.maxOrNull())
        }
    }

    /**
     * 이 방면 열차들이 이 역에서 역 순번이 커지는 쪽으로 달리는가 (열차별 다수결).
     * 순환선은 끝 ↔ 처음을 이웃으로 본다. 판단할 열차가 없으면 null.
     */
    private fun movesUp(line: Line, stationId: String, dirTrips: List<Trip>): Boolean? {
        val i = line.stations.indexOf(stationId)
        if (i < 0) return null
        val n = line.stations.size
        val idx = HashMap<String, Int>(n * 2)
        line.stations.forEachIndexed { k, s -> idx[s] = k }
        fun delta(from: Int, to: Int): Int {
            var d = to - from
            if (line.loop && n > 0) {
                d = ((d % n) + n) % n
                if (d > n / 2) d -= n
            }
            return d
        }
        var up = 0
        var down = 0
        for (t in dirTrips) {
            val si = t.stops.indexOfFirst { it.stationId == stationId }
            if (si < 0) continue
            val d = when {
                si < t.stops.lastIndex -> idx[t.stops[si + 1].stationId]?.let { delta(i, it) }
                si > 0 -> idx[t.stops[si - 1].stationId]?.let { delta(it, i) }
                else -> null
            } ?: continue
            if (d > 0) up++ else if (d < 0) down++
        }
        return when {
            up > down -> true
            down > up -> false
            else -> null
        }
    }

    fun estimate(trip: Trip, stationId: String, rt: RealtimeSnapshot, nowSec: Int): Arrival? {
        val si = trip.stops.indexOfFirst { it.stationId == stationId }
        if (si < 0 || si == trip.stops.lastIndex) return null // 이 역이 종착인 열차는 제외
        val live = rt.trains[RealtimeSnapshot.key(trip.lineId, trip.trainNumber)]
        val delay = live?.delaySec ?: 0
        val stop = trip.stops[si]
        if (stop.dep + delay < nowSec - graceSec) return null // 이미 출발
        val predicted = stop.arr + delay
        val pos = (if (live?.fromStation != null) fromLive(trip, si, live, nowSec, predicted) else fromSchedule(trip, si, delay, nowSec))
            ?: return null
        val minutes = ((predicted - nowSec).coerceAtLeast(0) + 59) / 60
        return Arrival(
            trip = trip, scheduledSec = stop.arr, predictedSec = predicted, delaySec = delay, minutes = minutes,
            stopsAway = pos.second, phase = pos.first,
            source = if (live != null) ArrivalSource.REALTIME else ArrivalSource.SCHEDULE,
        )
    }

    private fun fromLive(trip: Trip, si: Int, live: TrainPosition, now: Int, predArr: Int): Pair<ArrivalPhase, Int?>? {
        val fi = trip.stops.indexOfFirst { it.stationId == live.fromStation }
        if (fi < 0) return ArrivalPhase.EN_ROUTE to null // 직통 운행 구간 등 노선 밖
        val moving = live.toStation != null
        return when {
            fi > si -> null
            fi == si -> if (!moving) ArrivalPhase.ARRIVED to 0 else null
            fi == si - 1 && moving -> (if (predArr - now <= 60) ArrivalPhase.APPROACHING else ArrivalPhase.LEFT_PREVIOUS) to 1
            fi == si - 1 -> ArrivalPhase.AT_PREVIOUS to 1
            else -> ArrivalPhase.EN_ROUTE to (si - fi)
        }
    }

    private fun fromSchedule(trip: Trip, si: Int, delay: Int, now: Int): Pair<ArrivalPhase, Int?>? {
        val s = trip.stops
        val atIdx = s.indexOfFirst { it.arr + delay <= now && now < it.dep + delay }
        if (atIdx >= 0) {
            return when {
                atIdx == si -> ArrivalPhase.ARRIVED to 0
                atIdx > si -> null
                atIdx == si - 1 -> ArrivalPhase.AT_PREVIOUS to 1
                atIdx == 0 -> ArrivalPhase.WAITING_AT_ORIGIN to si
                else -> ArrivalPhase.EN_ROUTE to (si - atIdx)
            }
        }
        val departed = s.indexOfLast { it.dep + delay <= now }
        return when {
            departed < 0 -> ArrivalPhase.WAITING_AT_ORIGIN to si
            departed >= si -> null
            departed == si - 1 ->
                (if (s[si].arr + delay - now <= 60) ArrivalPhase.APPROACHING else ArrivalPhase.LEFT_PREVIOUS) to 1
            else -> ArrivalPhase.EN_ROUTE to (si - departed)
        }
    }
}

/** 카드 큰 글씨 (현재 화면 언어) */
fun Arrival.minutesLabel(): String {
    val s = Strings.current
    return when {
        phase == ArrivalPhase.ARRIVED -> s.arrived
        minutes <= 0 || phase == ArrivalPhase.APPROACHING -> s.arrivingSoon
        else -> s.minutesLater(minutes)
    }
}

/** 카드 보조 문구 */
fun Arrival.phaseLabel(): String {
    val s = Strings.current
    return when (phase) {
        ArrivalPhase.ARRIVED -> s.phaseArrived
        ArrivalPhase.APPROACHING -> s.phaseApproaching
        ArrivalPhase.LEFT_PREVIOUS -> s.phaseLeftPrevious
        ArrivalPhase.AT_PREVIOUS -> s.phaseAtPrevious
        ArrivalPhase.EN_ROUTE -> stopsAway?.let { s.phaseStopsAway(it) } ?: s.phaseEnRoute
        ArrivalPhase.WAITING_AT_ORIGIN -> s.phaseWaiting
    }
}

fun Arrival.delayLabel(): String? {
    val s = Strings.current
    return when {
        source == ArrivalSource.SCHEDULE -> null
        delaySec >= 60 -> s.delayed(delaySec / 60)
        else -> s.onTime
    }
}
