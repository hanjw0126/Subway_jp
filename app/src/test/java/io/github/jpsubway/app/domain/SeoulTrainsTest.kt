package io.github.jpsubway.app.domain

import io.github.jpsubway.app.data.remote.SeoulClient
import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import io.github.jpsubway.app.domain.seoul.SeoulTrainPos
import io.github.jpsubway.app.domain.seoul.SeoulTrains
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서울 실시간 열차 위치 → 노선도 아이콘 */
class SeoulTrainsTest {
    private fun st(id: String, ko: String) = Station(id, lineIdOf(id), name = LocalizedName(ko = ko), lat = 0.0, lon = 0.0, group = "g.$id")
    private fun lineIdOf(id: String) = if (id.startsWith("R")) "kr.Seoul.2" else "kr.Seoul.4"

    // 4호선형 일자선 A(0,0) - B(60,0) - C(120,0) - D(180,0)
    private val straight = Line(
        id = "kr.Seoul.4", operator = "kr:Seoul", code = "4", name = LocalizedName(ko = "4호선"), color = "#00A5DE",
        stations = listOf("A", "B", "C", "D"),
        directions = LineDirections(Direction("kr.Seoul.4:asc"), Direction("kr.Seoul.4:desc")),
    )

    // 2호선형 순환선 R0..R3 (외선 = 순번 증가)
    private val ring = Line(
        id = "kr.Seoul.2", operator = "kr:Seoul", code = "2", name = LocalizedName(ko = "2호선"), color = "#00A84D",
        stations = listOf("R0", "R1", "R2", "R3"),
        directions = LineDirections(Direction("kr.Seoul.2:asc"), Direction("kr.Seoul.2:desc")),
        loop = true,
    )

    private val net = Network(
        regionId = "seoul",
        lines = listOf(straight, ring),
        stations = listOf(st("A", "가역"), st("B", "나"), st("C", "다"), st("D", "라"),
            st("R0", "시청"), st("R1", "을지로입구"), st("R2", "강남"), st("R3", "홍대입구")),
    )

    private val xy = mapOf(
        "g.A" to (0f to 0f), "g.B" to (60f to 0f), "g.C" to (120f to 0f), "g.D" to (180f to 0f),
        "g.R0" to (0f to 0f), "g.R1" to (100f to 0f), "g.R2" to (100f to 100f), "g.R3" to (0f to 100f),
    )

    private fun one(row: SeoulTrainPos, lines: Set<String> = setOf("kr.Seoul.4")) =
        SeoulTrains.markers(listOf(row), net, lines, xy).single()

    @Test
    fun arrivedTrainSitsOnStationFacingDestination() {
        val m = one(SeoulTrainPos(statnNm = "나", statnTnm = "라", trainSttus = "1", trainNo = "4001"))
        assertEquals(60f, m.x, 0.01f)
        assertEquals(1f, m.dirX, 0.01f)
        assertTrue(m.atStation)
    }

    @Test
    fun departedAndApproachingTrainsSitBetweenStations() {
        // 나에서 출발(가역 방향, '역'을 뷔 이름으로도 맞춘다) → 나→가 30%
        val dep = one(SeoulTrainPos(statnNm = "나", statnTnm = "가", trainSttus = "2"))
        assertEquals(42f, dep.x, 0.01f)
        assertEquals(-1f, dep.dirX, 0.01f)
        // 다에 진입(라 방향) → 나→다 75%
        val app = one(SeoulTrainPos(statnNm = "다", statnTnm = "라", trainSttus = "0"))
        assertEquals(105f, app.x, 0.01f)
    }

    @Test
    fun loopUsesInnerOuterAndWraps() {
        // 외선(1): 순번 증가 → 시청(R0)에서 출발하면 을지로입구(R1) 쪽
        val outer = one(SeoulTrainPos(statnNm = "시청", statnTnm = "성수", updnLine = "1", trainSttus = "2"), setOf("kr.Seoul.2"))
        assertEquals(30f, outer.x, 0.01f)
        // 내선(0): 순번 감소, 끝↔처음 연결 → 시청(R0)에서 출발하면 홍대입구(R3) 쪽
        val inner = one(SeoulTrainPos(statnNm = "시청", statnTnm = "성수", updnLine = "0", trainSttus = "2"), setOf("kr.Seoul.2"))
        assertEquals(0f, inner.x, 0.01f)
        assertEquals(30f, inner.y, 0.01f)
    }

    @Test
    fun unknownStationIsSkippedAndParserWorks() {
        assertTrue(SeoulTrains.markers(listOf(SeoulTrainPos(statnNm = "없는역")), net, setOf("kr.Seoul.4"), xy).isEmpty())
        val json = Json { ignoreUnknownKeys = true }
        val rows = SeoulClient.parsePositions(
            json,
            """{"errorMessage":{"code":"INFO-000"},"realtimePositionList":[{"statnNm":"강남","trainNo":2210,"updnLine":"1","statnTnm":"성수","trainSttus":"1","directAt":"0","lstcarAt":"0"}]}""",
        )
        assertEquals("2210", rows.single().trainNo)
        assertEquals("2호선", SeoulTrains.apiLineFor("kr.Seoul.2.Seongsu"))
    }
}
