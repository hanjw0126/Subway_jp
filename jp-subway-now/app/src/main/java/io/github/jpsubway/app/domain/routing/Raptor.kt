package io.github.jpsubway.app.domain.routing

import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip

sealed interface JourneyLeg

data class RideLeg(val trip: Trip, val boardIndex: Int, val alightIndex: Int, val depSec: Int, val arrSec: Int) : JourneyLeg {
    val fromStationId: String get() = trip.stops[boardIndex].stationId
    val toStationId: String get() = trip.stops[alightIndex].stationId
    val stopCount: Int get() = alightIndex - boardIndex
}

data class WalkLeg(val fromStationId: String, val toStationId: String, val durationSec: Int) : JourneyLeg

data class Journey(val legs: List<JourneyLeg>, val departSec: Int, val arriveSec: Int) {
    val rides: Int get() = legs.count { it is RideLeg }
    val transfers: Int get() = (rides - 1).coerceAtLeast(0)
    val durationSec: Int get() = arriveSec - departSec
}

/** 같은 정차 패턴을 가진 열차 묶음 (출발 시각 순) */
class RoutePattern(val stops: IntArray, val trips: List<Trip>) {
    val arr: List<IntArray> = trips.map { t -> IntArray(stops.size) { t.stops[it].arr } }
    val dep: List<IntArray> = trips.map { t -> IntArray(stops.size) { t.stops[it].dep } }

    fun earliestTrip(pos: Int, ready: Int): Int {
        for (t in trips.indices) if (dep[t][pos] >= ready) return t
        return -1
    }
}

class RaptorIndex(
    val stopIds: List<String>,
    val stopOf: Map<String, Int>,
    val routes: List<RoutePattern>,
    val routesAtStop: Array<IntArray>,
    val footpaths: Array<List<Pair<Int, Int>>>,
) {
    companion object {
        fun build(network: Network, timetable: Timetable): RaptorIndex {
            val stopIds = network.stations.map { it.id }
            val stopOf = stopIds.withIndex().associate { it.value to it.index }
            val routes = timetable.trips
                .map { t -> t.copy(stops = t.stops.filter { it.stationId in stopOf }) }
                .filter { it.stops.size >= 2 }
                .groupBy { t -> t.stops.map { it.stationId } }
                .values.map { g ->
                    val sorted = g.sortedBy { it.stops.first().dep }
                    RoutePattern(sorted.first().stops.map { stopOf.getValue(it.stationId) }.toIntArray(), sorted)
                }
            val atStop = Array(stopIds.size) { mutableListOf<Int>() }
            routes.forEachIndexed { r, rp -> rp.stops.distinct().forEach { atStop[it] += r } }
            val foot = Array(stopIds.size) { mutableListOf<Pair<Int, Int>>() }
            network.transfers.forEach { tr ->
                val a = stopOf[tr.from]
                val b = stopOf[tr.to]
                if (a != null && b != null) {
                    foot[a] += b to tr.walkSec
                    foot[b] += a to tr.walkSec
                }
            }
            return RaptorIndex(
                stopIds, stopOf, routes,
                Array(atStop.size) { atStop[it].toIntArray() },
                Array(foot.size) { foot[it].toList() },
            )
        }
    }
}

/**
 * RAPTOR (Round-bAsed Public Transit Optimized Router).
 * 라운드 k = k번 탑승이므로 한 번의 탐색으로 (도착시각 × 환승횟수) 파레토 최적 경로를 모두 얻는다.
 */
class RaptorRouter(private val idx: RaptorIndex, private val minChangeSec: Int = 60) {
    private sealed interface Label
    private data class Ride(val route: Int, val trip: Int, val boardPos: Int, val alightPos: Int) : Label
    private data class Walk(val from: Int, val sec: Int) : Label
    private data object Origin : Label

    /** 파레토 최적 경로 목록. 앞쪽일수록 탑승 횟수가 적다 */
    fun search(from: Collection<String>, to: Collection<String>, departSec: Int, maxRides: Int = 5): List<Journey> {
        val n = idx.stopIds.size
        val src = from.mapNotNull { idx.stopOf[it] }
        val dst = to.mapNotNull { idx.stopOf[it] }.toSet()
        if (src.isEmpty() || dst.isEmpty()) return emptyList()
        val tau = Array(maxRides + 1) { IntArray(n) { INF } }
        val lab = Array(maxRides + 1) { arrayOfNulls<Label>(n) }
        val best = IntArray(n) { INF }
        var marked = mutableSetOf<Int>()
        src.forEach { tau[0][it] = departSec; best[it] = departSec; lab[0][it] = Origin; marked += it }

        for (k in 1..maxRides) {
            tau[k] = tau[k - 1].copyOf()
            val queue = HashMap<Int, Int>()
            for (s in marked) for (r in idx.routesAtStop[s]) {
                val p = idx.routes[r].stops.indexOf(s)
                queue[r] = minOf(queue[r] ?: Int.MAX_VALUE, p)
            }
            marked = mutableSetOf()
            for ((r, p0) in queue) {
                val route = idx.routes[r]
                var trip = -1
                var boardPos = -1
                for (p in p0 until route.stops.size) {
                    val s = route.stops[p]
                    if (trip >= 0) {
                        val a = route.arr[trip][p]
                        val targetBest = dst.minOf { best[it] }
                        if (a < best[s] && a < targetBest) {
                            tau[k][s] = a
                            best[s] = a
                            lab[k][s] = Ride(r, trip, boardPos, p)
                            marked += s
                        }
                    }
                    val prev = tau[k - 1][s]
                    if (prev == INF) continue
                    val ready = prev + if (lastLabel(lab, k - 1, s) is Ride) minChangeSec else 0
                    if (trip < 0 || ready <= route.dep[trip][p]) {
                        val t = route.earliestTrip(p, ready)
                        if (t >= 0 && t != trip) {
                            trip = t
                            boardPos = p
                        }
                    }
                }
            }
            for (s in marked.toList()) for ((t, sec) in idx.footpaths[s]) {
                val a = tau[k][s] + sec
                if (a < best[t]) {
                    tau[k][t] = a
                    best[t] = a
                    lab[k][t] = Walk(s, sec)
                    marked += t
                }
            }
            if (marked.isEmpty()) break
        }

        val out = mutableListOf<Journey>()
        var bestArr = INF
        for (k in 1..maxRides) {
            val t = dst.filter { lab[k][it] != null }.minByOrNull { tau[k][it] } ?: continue
            if (tau[k][t] >= bestArr) continue
            bestArr = tau[k][t]
            reconstruct(lab, k, t, tau[k][t])?.let { out += it }
        }
        return out
    }

    /** 다음 열차 대안까지 포함해 여러 경로를 반환 (도착 시각 → 환승 순 정렬) */
    fun searchAlternatives(from: Collection<String>, to: Collection<String>, departSec: Int, count: Int = 3): List<Journey> {
        val all = mutableListOf<Journey>()
        var t = departSec
        repeat(count) {
            val found = search(from, to, t)
            if (found.isEmpty()) return all.distinctBy { it.legs }.sortedWith(compareBy({ it.arriveSec }, { it.transfers }))
            all += found
            t = found.minOf { it.departSec } + 1
        }
        return all.distinctBy { it.legs }.sortedWith(compareBy({ it.arriveSec }, { it.transfers }))
    }

    private fun lastLabel(lab: Array<Array<Label?>>, k: Int, s: Int): Label? = (k downTo 0).firstNotNullOfOrNull { lab[it][s] }

    private fun reconstruct(lab: Array<Array<Label?>>, k0: Int, target: Int, arrive: Int): Journey? {
        val legs = ArrayDeque<JourneyLeg>()
        var k = k0
        var s = target
        var guard = 0
        while (guard++ < 64) {
            when (val l = lab[k][s]) {
                is Walk -> {
                    legs.addFirst(WalkLeg(idx.stopIds[l.from], idx.stopIds[s], l.sec))
                    s = l.from
                }
                is Ride -> {
                    val route = idx.routes[l.route]
                    legs.addFirst(RideLeg(route.trips[l.trip], l.boardPos, l.alightPos, route.dep[l.trip][l.boardPos], route.arr[l.trip][l.alightPos]))
                    s = route.stops[l.boardPos]
                    k = (k - 1 downTo 0).firstOrNull { lab[it][s] != null } ?: return null
                }
                Origin -> break
                null -> return null
            }
        }
        val firstRide = legs.firstOrNull { it is RideLeg } as? RideLeg ?: return null
        return Journey(legs.toList(), firstRide.depSec, arrive)
    }

    private companion object {
        const val INF = Int.MAX_VALUE / 2
    }
}
