package io.github.jpsubway.app.domain

import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.core.time.KoreanHolidays
import io.github.jpsubway.app.data.seoul.SeoulTimetableSource
import io.github.jpsubway.app.domain.model.Direction
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineDirections
import io.github.jpsubway.app.domain.model.LocalizedName
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 한국 공휴일·서울 시간표 읽기 */
class SeoulTimetableTest {
    @Test
    fun koreanHolidaysWithSubstitutes() {
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2026, 2, 16))) // 설날 전날
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2025, 5, 6))) // 어린이날·부처님오신날 겹침 → 대체
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2025, 10, 8))) // 추석 연휴 일요일·개천절 → 대체
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2026, 6, 3))) // 지방선거
        assertFalse(KoreanHolidays.isHoliday(LocalDate.of(2026, 10, 13)))
        assertEquals(DayType.HOLIDAY, DayType.of(LocalDate.of(2026, 9, 24), "kr")) // 추석 전날(목)
        assertEquals(DayType.WEEKDAY, DayType.of(LocalDate.of(2026, 9, 24), "jp"))
        assertEquals(DayType.SATURDAY, DayType.of(LocalDate.of(2026, 10, 10), "kr"))
    }

    @Test
    fun parsesCompactTimetable() {
        val line = Line(
            id = "kr.Seoul.2", operator = "kr:Seoul", code = "2", name = LocalizedName(ko = "2호선"), color = "#00A84D",
            stations = listOf("A", "B", "C"),
            directions = LineDirections(Direction("kr.Seoul.2:asc"), Direction("kr.Seoul.2:desc")), loop = true,
        )
        fun st(id: String) = Station(id, "kr.Seoul.2", name = LocalizedName(ko = id), lat = 0.0, lon = 0.0, group = "g$id")
        val net = Network(regionId = "seoul", lines = listOf(line), stations = listOf(st("A"), st("B"), st("C")))
        val text = """{"source":"x.csv","lines":["kr.Seoul.2","kr.Seoul.9"],"stations":["A","B","C"],
            "trips":[[0,"2001",1,2,0,[0,1,2],[19800,19920,20040],[30,30,0]],[1,"9001",0,1,1,[0,1],[100,200],[0,0]]]}"""
        val tt = SeoulTimetableSource.parse(Json, text, net, DayType.WEEKDAY, 0)
        assertEquals(1, tt.trips.size) // 없는 노선(9호선) 열차는 버린다
        val t = tt.trips.single()
        assertEquals("kr.Seoul.2:desc", t.directionId)
        assertEquals("C", t.destinationId)
        assertEquals(19830, t.stops[0].dep)
        assertEquals(20040, t.stops[2].arr)
        assertEquals("WEEKDAY", tt.dayType)
    }
}
