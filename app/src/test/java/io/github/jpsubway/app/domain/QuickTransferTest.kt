package io.github.jpsubway.app.domain

import io.github.jpsubway.app.data.transfer.QuickTransferSource
import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Trip
import io.github.jpsubway.app.domain.routing.RideLeg
import io.github.jpsubway.app.domain.transfer.QuickTransfer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 빠른 환승: 내리는 노선·역·진행 방향 + 갈아탈 노선·방향으로 칸·문 찾기 */
class QuickTransferTest {
    private fun line(id: String, vararg st: String) = Line(
        id = id, operator = "kr:Seoul", code = "", name = LocalizedName(ko = id), color = "#000000",
        stations = st.toList(), directions = LineDirections(Direction("$id:asc"), Direction("$id:desc")),
    )

    private fun st(id: String, line: String, ko: String) =
        Station(id, line, name = LocalizedName(ko = ko), lat = 0.0, lon = 0.0, group = "g.$ko")

    // 공항철도: 서울역 - 공덕 - 홍대입구 - 인천공항1터미널 / 5호선: 애오개 - 공덕 - 마포
    private val arex = line("kr.Seoul.AREX", "A1", "A2", "A3", "A4")
    private val l5 = line("kr.Seoul.5", "F1", "F2", "F3")
    private val net = Network(
        regionId = "seoul", lines = listOf(arex, l5),
        stations = listOf(
            st("A1", arex.id, "서울역"), st("A2", arex.id, "공덕"), st("A3", arex.id, "홍대입구"), st("A4", arex.id, "인천공항1터미널"),
            st("F1", l5.id, "애오개"), st("F2", l5.id, "공덕"), st("F3", l5.id, "마포"),
        ),
    )

    private val rows = QuickTransferSource.parse(
        Json,
        """{"rows":[
          ["AREX","공덕","인천공항1터미널","5","마포",1,4],
          ["AREX","공덕","서울역","5","마포",6,1],
          ["AREX","공덕","인천공항1터미널","5","애오개",1,4],
          ["AREX","공덕","서울역","5","애오개",6,1]]}""",
    )

    private fun ride(trip: Trip, board: Int, alight: Int) = RideLeg(trip, board, alight, trip.stops[board].dep, trip.stops[alight].arr)

    @Test
    fun picksRowByTravelDirectionAndTransferDirection() {
        // 인천공항 방면에서 와서(홍대입구 → 공덕은 서울역 방면) 공덕에서 내림 → 서울역 방면 행
        val toSeoul = Trip("t1", arex.id, "1", "${arex.id}:desc", "A1",
            stops = listOf(StopTime("A4", 0, 0), StopTime("A3", 100, 110), StopTime("A2", 200, 210), StopTime("A1", 300, 300)))
        val to5 = Trip("t2", l5.id, "2", "${l5.id}:asc", "F3",
            stops = listOf(StopTime("F1", 200, 210), StopTime("F2", 400, 420), StopTime("F3", 500, 500)))
        val hit = QuickTransfer.find(rows, net, ride(toSeoul, 1, 2), ride(to5, 1, 2))
        assertEquals(6 to 1, hit?.car to hit?.door)
        // 서울역에서 인천공항 방면으로 가다 공덕에서 내림 → 인천공항1터미널 방면 행
        val toAirport = Trip("t3", arex.id, "3", "${arex.id}:asc", "A4",
            stops = listOf(StopTime("A1", 0, 0), StopTime("A2", 100, 110), StopTime("A3", 200, 210), StopTime("A4", 300, 300)))
        val hit2 = QuickTransfer.find(rows, net, ride(toAirport, 0, 1), ride(to5, 1, 2))
        assertEquals(1 to 4, hit2?.car to hit2?.door)
    }

    @Test
    fun unknownTransferGivesNothing() {
        val t = Trip("t", arex.id, "1", "${arex.id}:asc", "A4",
            stops = listOf(StopTime("A1", 0, 0), StopTime("A3", 100, 110), StopTime("A4", 300, 300)))
        val u = Trip("u", l5.id, "2", "${l5.id}:asc", "F3", stops = listOf(StopTime("F2", 400, 420), StopTime("F3", 500, 500)))
        // 홍대입구에서 5호선 환승 데이터는 없다
        assertNull(QuickTransfer.find(rows, net, ride(t, 0, 1), ride(u, 0, 1)))
    }
}
