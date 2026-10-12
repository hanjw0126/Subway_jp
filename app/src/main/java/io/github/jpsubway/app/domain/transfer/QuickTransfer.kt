package io.github.jpsubway.app.domain.transfer

import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.routing.RideLeg
import io.github.jpsubway.app.domain.seoul.SeoulLive

/** 빠른 환승 한 행: 노선·역·진행 방향(종착역) → 갈아탈 노선·방향(환승 뒤 다음 역)에 가장 가까운 칸·문 */
data class QuickTransferRow(
    val line: String,
    val station: String,
    val toward: String,
    val xferLine: String,
    val xferNext: String,
    val car: Int,
    val door: Int,
)

/**
 * 경로의 환승(앞 승차 구간 a 에서 내려 다음 승차 구간 b 로)에 맞는 빠른 환승 칸·문을 찾는다.
 * 노선은 앱 노선 키(kr.Seoul.<키>), 역은 '역'·괄호를 뺨 한국어 역명으로 맞춘다.
 * 진행 방향을 확정할 수 없으면 틀린 정보를 보여 주지 않도록 null.
 */
object QuickTransfer {
    fun find(rows: List<QuickTransferRow>, network: Network, a: RideLeg, b: RideLeg): QuickTransferRow? {
        if (rows.isEmpty()) return null
        fun nm(sid: String?): String? = sid?.let { network.stationById[it]?.name?.ko }?.let(SeoulLive::apiStationName)
        val la = SeoulLive.lineKey(a.trip.lineId)
        val lb = SeoulLive.lineKey(b.trip.lineId)
        val st = nm(a.toStationId) ?: return null
        val nextB = nm(b.trip.stops.getOrNull(b.boardIndex + 1)?.stationId)
        val cands = rows.filter { it.line == la && it.station == st && it.xferLine == lb && (nextB == null || it.xferNext == nextB) }
        if (cands.isEmpty()) return null
        // 1) 타고 온 열차가 앞으로 갈 역(또는 종착역)에 행의 종착역이 있으면 그 방향
        val ahead = a.trip.stops.drop(a.alightIndex + 1).mapNotNull { nm(it.stationId) }.toSet() + setOfNotNull(nm(a.trip.destinationId))
        cands.firstOrNull { it.toward in ahead }?.let { return it }
        // 2) 노선 역 순서로: 직전 정차역 → 내린 역 방향에 종착역이 있는 행
        val names = network.lineById[a.trip.lineId]?.stations?.map { nm(it) }
        val prev = nm(a.trip.stops.getOrNull(a.alightIndex - 1)?.stationId)
        if (names != null && prev != null) {
            val ps = names.indexOf(st)
            val pp = names.indexOf(prev)
            if (ps >= 0 && pp >= 0 && ps != pp) {
                val sign = Integer.signum(ps - pp)
                cands.firstOrNull { val pt = names.indexOf(it.toward); pt >= 0 && Integer.signum(pt - ps) == sign }?.let { return it }
                // 종착역이 다른 지선에 있는 행: 분명히 반대 방향인 행을 빼고 하나만 남으면 그것
                val rest = cands.filterNot { val pt = names.indexOf(it.toward); pt >= 0 && Integer.signum(pt - ps) == -sign }
                if (rest.map { it.car to it.door }.distinct().size == 1) return rest.first()
            }
        }
        // 3) 후보가 모두 같은 칸·문이면 방향과 관계없이 그것
        return cands.takeIf { c -> c.map { it.car to it.door }.distinct().size == 1 }?.first()
    }
}
