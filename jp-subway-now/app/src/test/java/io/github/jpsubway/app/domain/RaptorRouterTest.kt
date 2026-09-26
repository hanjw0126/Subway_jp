package io.github.jpsubway.app.domain

import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Transfer
import io.github.jpsubway.app.domain.model.Trip
import io.github.jpsubway.app.domain.routing.RaptorIndex
import io.github.jpsubway.app.domain.routing.RaptorRouter
import io.github.jpsubway.app.domain.routing.WalkLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaptorRouterTest {
    private fun line(id: String, st: List<String>) = Line(
        id, "op", id, LocalizedName(ko = id), "#000000", st,
        LineDirections(Direction("$id+"), Direction("$id-")),
    )
    private fun station(id: String, line: String, g: String = id) = Station(id, line, "", LocalizedName(ko = id), 0.0, 0.0, g)

    private val net = Network(
        regionId = "t",
        lines = listOf(line("X", listOf("s1", "s2", "s3")), line("Y", listOf("t1", "t2"))),
        stations = listOf(station("s1", "X"), station("s2", "X", "G"), station("s3", "X"), station("t1", "Y", "G"), station("t2", "Y")),
        transfers = listOf(Transfer("s2", "t1", 120)),
    )
    private val tt = Timetable(
        "t", "WEEKDAY", "test", 0,
        listOf(
            Trip("x1", "X", "x1", "X+", "s3", stops = listOf(StopTime("s1", 1000, 1000), StopTime("s2", 1100, 1110), StopTime("s3", 1200, 1200))),
            Trip("y1", "Y", "y1", "Y+", "t2", stops = listOf(StopTime("t1", 1150, 1150), StopTime("t2", 1250, 1250))),
            Trip("y2", "Y", "y2", "Y+", "t2", stops = listOf(StopTime("t1", 1300, 1300), StopTime("t2", 1400, 1400))),
        ),
    )
    private val router = RaptorRouter(RaptorIndex.build(net, tt))

    @Test
    fun transferRespectsWalkTime() {
        val js = router.search(listOf("s1"), listOf("t2"), 900)
        assertEquals(1, js.size)
        val j = js[0]
        assertEquals(1000, j.departSec)
        assertEquals(1400, j.arriveSec) // 1150 열차는 도보 환승(120초) 때문에 놓침
        assertEquals(1, j.transfers)
        assertTrue(j.legs[1] is WalkLeg)
    }

    @Test
    fun directRide() {
        val js = router.search(listOf("s1"), listOf("s3"), 900)
        assertEquals(1200, js.single().arriveSec)
        assertEquals(0, js.single().transfers)
    }

    @Test
    fun noRouteAfterLastTrain() {
        assertTrue(router.search(listOf("s1"), listOf("t2"), 1500).isEmpty())
    }
}
