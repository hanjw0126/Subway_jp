package io.github.jpsubway.app.domain.seoul

import io.github.jpsubway.app.domain.model.Network
import kotlin.math.hypot

/** 서울 실시간 열차 위치 API(realtimePosition) 한 행 — 값은 모두 원본 문자열 */
data class SeoulTrainPos(
    val subwayId: String = "",
    /** 상태의 기준 역 */
    val statnNm: String = "",
    val trainNo: String = "",
    /** 0 = 상행/내선, 1 = 하행/외선 */
    val updnLine: String = "",
    /** 종착역 */
    val statnTnm: String = "",
    /** 0 진입, 1 도착, 2 출발, 3 전역출발 */
    val trainSttus: String = "",
    /** 1 = 급행 */
    val directAt: String = "",
    /** 1 = 막차 */
    val lstcarAt: String = "",
)

/** 노선도(월드 좌표) 위 열차 아이콘. dirX/dirY = 진행 방향 단위 벡터 (모르면 0) */
data class TrainMarker(
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    /** 종착역 한국어 원문 (표시할 때 역명 언어로 바꾼다) */
    val destination: String,
    val trainNo: String,
    val express: Boolean,
    val last: Boolean,
    val atStation: Boolean,
)

object SeoulTrains {
    /** 앱 노선 키 → 실시간 위치 API 노선명 (중계 서버 허용 목록과 같아야 한다) */
    val apiLineName = mapOf(
        "1" to "1호선", "2" to "2호선", "3" to "3호선", "4" to "4호선", "5" to "5호선",
        "6" to "6호선", "7" to "7호선", "8" to "8호선", "9" to "9호선",
        "KJ" to "경의중앙선", "AREX" to "공항철도", "GC" to "경춘선", "SB" to "수인분당선",
        "SBD" to "신분당선", "GG" to "경강선", "WS" to "서해선", "UI" to "우이신설선",
        "SL" to "신림선", "GTXA" to "GTX-A",
    )

    fun apiLineFor(lineId: String): String? =
        if (SeoulLive.isSeoulLine(lineId)) apiLineName[SeoulLive.lineKey(lineId)] else null

    /**
     * 열차 위치 → 노선도 아이콘.
     *  - 상태 기준 역을 표시 중인 노선(lineIds)에서 찾고, 종착역이 있는 노선을 우선한다 (지선 구분).
     *  - 진행 방향: 순환선은 외선(1) = 순번 증가, 그 외는 종착역 쪽.
     *  - 위치: 도착 = 역, 진입 = 직전 역→역 75%, 전역출발 = 35%, 출발 = 역→다음 역 30%.
     * nodeXY: 환승 묶음(group) → 노선도 좌표
     */
    fun markers(
        rows: List<SeoulTrainPos>,
        network: Network,
        lineIds: Set<String>,
        nodeXY: Map<String, Pair<Float, Float>>,
    ): List<TrainMarker> {
        val lines = lineIds.mapNotNull { network.lineById[it] }
        if (lines.isEmpty()) return emptyList()
        fun nameOf(sid: String) = network.stationById[sid]?.name?.ko?.let(SeoulLive::apiStationName)
        fun xyOf(sid: String?): Pair<Float, Float>? = sid?.let { network.stationById[it]?.group }?.let { nodeXY[it] }
        val out = ArrayList<TrainMarker>()
        for (r in rows) {
            val st = SeoulLive.apiStationName(r.statnNm)
            val dest = SeoulLive.apiStationName(r.statnTnm)
            val cands = lines.flatMap { l -> l.stations.withIndex().filter { nameOf(it.value) == st }.map { l to it.index } }
            if (cands.isEmpty()) continue
            val (line, i) = cands.firstOrNull { (l, _) -> l.stations.any { nameOf(it) == dest } } ?: cands.first()
            val n = line.stations.size
            val dIdx = line.stations.indexOfFirst { nameOf(it) == dest }
            val step = when {
                line.loop -> if (r.updnLine.trim() == "1") 1 else -1
                dIdx >= 0 && dIdx != i -> if (dIdx > i) 1 else -1
                else -> 0
            }
            fun at(k: Int): String? = if (line.loop) line.stations[((k % n) + n) % n] else line.stations.getOrNull(k)
            val cur = xyOf(line.stations[i]) ?: continue
            val next = if (step != 0) xyOf(at(i + step)) else null
            val prev = if (step != 0) xyOf(at(i - step)) else null
            fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float) =
                Pair(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t)
            val status = r.trainSttus.trim()
            val p = when {
                status == "0" && prev != null -> lerp(prev, cur, 0.75f)
                status == "3" && prev != null -> lerp(prev, cur, 0.35f)
                status == "2" && next != null -> lerp(cur, next, 0.3f)
                else -> cur
            }
            // 진행 방향: 직전 역 → 다음 역 (한쪽만 있으면 그쪽 기준)
            val from = prev ?: cur
            val to = next ?: cur
            var dx = to.first - from.first
            var dy = to.second - from.second
            val len = hypot(dx, dy)
            if (len > 1e-3f) {
                dx /= len
                dy /= len
            } else {
                dx = 0f
                dy = 0f
            }
            out += TrainMarker(
                x = p.first, y = p.second, dirX = dx, dirY = dy,
                destination = r.statnTnm.trim(), trainNo = r.trainNo.trim(),
                express = r.directAt.trim() == "1", last = r.lstcarAt.trim() == "1",
                atStation = p == cur,
            )
        }
        return out
    }
}
