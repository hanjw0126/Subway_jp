package io.github.jpsubway.app.domain.arrival

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
        // desc(순번 감소) 열차는 i+1 에서 와서 i-1 로 간다. asc 는 반대.
        return listOf(
            Triple(line.directions.desc, at(i + 1), at(i - 1)),
            Triple(line.directions.asc, at(i - 1), at(i + 1)),
        ).map { (dir, prev, next) ->
            val dirTrips = trips.filter { it.lineId == line.id && it.directionId == dir.id }
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

/** 카드 큰 글씨 */
fun Arrival.minutesLabel(): String = when {
    phase == ArrivalPhase.ARRIVED -> "도착"
    minutes <= 0 || phase == ArrivalPhase.APPROACHING -> "곧 도착"
    else -> "${minutes}분 후"
}

/** 카드 보조 문구 */
fun Arrival.phaseLabel(): String = when (phase) {
    ArrivalPhase.ARRIVED -> "승강장 도착"
    ArrivalPhase.APPROACHING -> "전역 출발 · 진입 중"
    ArrivalPhase.LEFT_PREVIOUS -> "전역 출발"
    ArrivalPhase.AT_PREVIOUS -> "전역 도착"
    ArrivalPhase.EN_ROUTE -> stopsAway?.let { "${it}번째 전역" } ?: "운행 중"
    ArrivalPhase.WAITING_AT_ORIGIN -> "출발 대기"
}

fun Arrival.delayLabel(): String? = when {
    source == ArrivalSource.SCHEDULE -> null
    delaySec >= 60 -> "${delaySec / 60}분 지연"
    else -> "정시"
}
