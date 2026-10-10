package io.github.jpsubway.app.domain

import io.github.jpsubway.app.data.remote.SeoulClient
import io.github.jpsubway.app.domain.seoul.SeoulLive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서울 실시간 도착정보 해석·방면 묶기 */
class SeoulLiveTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val sample = """
        {"errorMessage":{"status":200,"code":"INFO-000","message":"정상 처리되었습니다.","total":4},
         "realtimeArrivalList":[
          {"subwayId":"1002","updnLine":"외선","trainLineNm":"성수행 - 역삼방면","statnNm":"강남","btrainSttus":"일반",
           "barvlDt":"180","btrainNo":"2210","bstatnNm":"성수","arvlMsg2":"3분 후 (교대)","arvlMsg3":"교대","arvlCd":"99","lstcarAt":"0","ordkey":"01001성수0"},
          {"subwayId":"1002","updnLine":"내선","trainLineNm":"신도림행 - 교대방면","statnNm":"강남","btrainSttus":"일반",
           "barvlDt":0,"btrainNo":"2311","bstatnNm":"신도림","arvlMsg2":"[4]번째 전역 (선릉)","arvlMsg3":"선릉","arvlCd":"99","lstcarAt":"1","ordkey":"11004신도림0"},
          {"subwayId":"1002","updnLine":"내선","trainLineNm":"신도림행 - 교대방면","statnNm":"강남","btrainSttus":"일반",
           "barvlDt":"0","btrainNo":"2309","bstatnNm":"신도림","arvlMsg2":"강남 출발","arvlMsg3":"강남","arvlCd":"2","lstcarAt":"0","ordkey":"10000신도림0"},
          {"subwayId":"1077","updnLine":"하행","trainLineNm":"광교행 - 양재방면","statnNm":"강남","btrainSttus":"일반",
           "barvlDt":"60","btrainNo":"S123","bstatnNm":"광교","arvlMsg2":"전역 출발","arvlMsg3":"신논현","arvlCd":"3","lstcarAt":"0","ordkey":"01000광교0"}
         ]}
    """.trimIndent()

    @Test
    fun parsesMixedNumberAndStringFields() {
        val rows = SeoulClient.parseArrivals(json, sample)
        assertEquals(4, rows.size)
        assertEquals("0", rows[1].barvlDt) // 숫자로 온 값도 문자열로
    }

    @Test
    fun noDataIsEmpty() {
        val none = """{"status":500,"code":"INFO-200","message":"해당하는 데이터가 없습니다.","total":0}"""
        assertTrue(SeoulClient.parseArrivals(json, none).isEmpty())
    }

    @Test
    fun boardsPerLineAndDirection() {
        val rows = SeoulClient.parseArrivals(json, sample)
        val boards = SeoulLive.boards(rows, "2", fetchedEpochSec = 1_000)
        assertEquals(listOf("내선", "외선"), boards.map { it.updn })
        val inner = boards[0]
        assertEquals("교대", inner.heading)
        assertEquals(1, inner.arrivals.size) // 방금 떠난 열차(arvlCd 2) 제외
        assertEquals(4, inner.arrivals[0].stopsAway)
        assertTrue(inner.arrivals[0].last)
        assertNull(inner.arrivals[0].etaEpochSec)
        assertEquals(1_180L, boards[1].arrivals[0].etaEpochSec)
        // 신분당선은 다른 노선 키
        assertEquals("광교", SeoulLive.boards(rows, "SBD", 0).single().arrivals.single().destination)
    }

    @Test
    fun helpers() {
        assertEquals("2", SeoulLive.lineKey("kr.Seoul.2.Seongsu"))
        assertEquals("서울", SeoulLive.apiStationName("서울역"))
        assertEquals("역삼", SeoulLive.apiStationName("역삼"))
    }
}
