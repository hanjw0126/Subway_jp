package io.github.jpsubway.app.domain

import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.data.demo.DemoTimetableSource
import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import io.github.jpsubway.app.domain.status.StatusTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAndStatusTest {
    @Test
    fun demoTimetableIsMonotonicBothDirections() {
        val st = listOf(
            Station("a", "L", "", LocalizedName(ko = "a"), 35.659, 139.7016, "a"),
            Station("b", "L", "", LocalizedName(ko = "b"), 35.6652, 139.7123, "b"),
            Station("c", "L", "", LocalizedName(ko = "c"), 35.6705, 139.7177, "c"),
        )
        val line = Line("L", "op", "G", LocalizedName(ko = "L"), "#FF9500", st.map { it.id }, LineDirections(Direction("up"), Direction("down")))
        val tt = DemoTimetableSource.generate(Network(regionId = "r", lines = listOf(line), stations = st), DayType.WEEKDAY)
        assertTrue(tt.isDemo)
        assertEquals(setOf("up", "down"), tt.trips.map { it.directionId }.toSet())
        tt.trips.forEach { t -> t.stops.zipWithNext().forEach { (x, y) -> assertTrue(y.arr > x.dep) } }
    }

    @Test
    fun statusTranslation() {
        assertTrue(StatusTranslator.isNormal("現在、平常どおり運転しています。"))
        assertEquals("평상시대로 운행", StatusTranslator.toKorean(""))
        assertFalse(StatusTranslator.isNormal("人身事故の影響で、一部列車に遅れが出ています。"))
        assertEquals("지연 운행", StatusTranslator.toKorean("一部列車に遅れが出ています。"))
    }
}
