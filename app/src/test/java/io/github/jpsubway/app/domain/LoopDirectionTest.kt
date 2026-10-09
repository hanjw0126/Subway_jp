package io.github.jpsubway.app.domain

import io.github.jpsubway.app.data.remote.OdptMapper
import io.github.jpsubway.app.domain.arrival.ArrivalEstimator
import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.RealtimeSnapshot
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Test

/** 순환선(야마노테형)·6자형(오에도형) 열차의 방면 분류 */
class LoopDirectionTest {
    private val outer = Direction("Outer")
    private val inner = Direction("Inner")

    private fun line(stations: List<String>, loop: Boolean) = Line(
        id = "L", operator = "op", code = "X", name = LocalizedName(ko = "테스트선"), color = "#000000",
        stations = stations, directions = LineDirections(asc = outer, desc = inner), loop = loop,
    )

    // 야마노테형: S0..S5 순환
    private val ring = line(listOf("S0", "S1", "S2", "S3", "S4", "S5"), loop = true)

    // 오에도형: 순환 구간 R0..R4 + 분기역 T(5) + 지선 B6..B8 (T→R0 은 노선 데이터의 extraEdges 로 이어짐)
    private val six = line(listOf("R0", "R1", "R2", "R3", "R4", "T", "B6", "B7", "B8"), loop = false)

    @Test
    fun loopWrapCountsAsForward() {
        // 끝(S5) → 처음(S0) 을 넘어가도 순번이 증가하는 방향(asc)
        assertEquals(outer, OdptMapper.directionOf(listOf("S5", "S0", "S1"), ring, null))
        assertEquals(inner, OdptMapper.directionOf(listOf("S0", "S5", "S4"), ring, null))
    }

    @Test
    fun loopPrefersRailDirection() {
        assertEquals(inner, OdptMapper.directionOf(listOf("S1", "S2"), ring, "Inner"))
        assertEquals(outer, OdptMapper.directionOf(listOf("S2", "S1"), ring, "Outer"))
    }

    @Test
    fun branchIntoRingUsesRailDirection() {
        // 지선(순번 감소) → 분기역 → 순환 구간(순번 증가): 첫 두 정거장만 보면 desc 로 잘못 분류됐다
        val stops = listOf("B8", "B7", "B6", "T", "R0", "R1", "R2", "R3", "R4", "T")
        assertEquals(outer, OdptMapper.directionOf(stops, six, "Outer"))
        assertEquals(inner, OdptMapper.directionOf(stops.reversed(), six, "Inner"))
    }

    @Test
    fun straightLineKeepsStationOrder() {
        val plain = line(listOf("A", "B", "C", "D"), loop = false)
        assertEquals(outer, OdptMapper.directionOf(listOf("A", "B", "C"), plain, null))
        assertEquals(inner, OdptMapper.directionOf(listOf("C", "B"), plain, null))
        // 일자선에서 순번이 한쪽으로만 움직이면 railDirection 표기와 상관없이 역 순서를 따른다 (기존 동작)
        assertEquals(outer, OdptMapper.directionOf(listOf("A", "B"), plain, "Inner"))
    }

    @Test
    fun boardNeighboursFollowActualRunningDirection() {
        // 오에도형 지선 B7: 외선(asc) 열차가 실제로는 순번이 줄어드는 쪽(B8 → B7 → B6)으로 달린다
        val trip = Trip(
            "t", "L", "1", "Outer", "T",
            stops = listOf(StopTime("B8", 0, 0), StopTime("B7", 100, 110), StopTime("B6", 200, 210), StopTime("T", 300, 300)),
        )
        val boards = ArrivalEstimator().boards(six, "B7", listOf(trip), RealtimeSnapshot(), 0)
        val outerBoard = boards.first { it.direction == outer }
        assertEquals("B8", outerBoard.prevStationId)
        assertEquals("B6", outerBoard.nextStationId)
        // 해당 방면 열차가 없으면 기본(asc = 순번 증가) 표시
        val innerBoard = boards.first { it.direction == inner }
        assertEquals("B6", innerBoard.prevStationId)
        assertEquals("B8", innerBoard.nextStationId)
    }
}
