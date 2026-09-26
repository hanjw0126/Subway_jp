package io.github.jpsubway.app.core

import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.core.time.JapaneseHolidays
import io.github.jpsubway.app.core.time.ServiceClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TimeTest {
    @Test
    fun parseServiceSeconds() {
        assertEquals(18060, ServiceClock.parse("05:01"))
        assertEquals(87120, ServiceClock.parse("00:12"))
        assertEquals(86580, ServiceClock.parse("00:03", prev = 86280))
        assertEquals("00:12", ServiceClock.format(87120))
    }

    @Test
    fun serviceDayBefore4am() {
        val now = ServiceClock.at(Instant.parse("2024-05-31T16:30:00Z")) // JST 06-01 01:30
        assertEquals(LocalDate.of(2024, 5, 31), now.serviceDate)
        assertEquals(91800, now.seconds)
    }

    @Test
    fun holidays() {
        listOf("2024-01-08", "2024-02-12", "2024-03-20", "2024-05-06", "2024-09-22", "2024-09-23", "2026-09-22")
            .forEach { assertTrue(it, JapaneseHolidays.isHoliday(LocalDate.parse(it))) }
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.parse("2024-06-03")))
    }

    @Test
    fun dayTypes() {
        assertEquals(DayType.SATURDAY, DayType.of(LocalDate.parse("2024-06-01")))
        assertEquals(DayType.HOLIDAY, DayType.of(LocalDate.parse("2024-06-02")))
        assertEquals(DayType.HOLIDAY, DayType.of(LocalDate.parse("2024-12-31")))
        assertEquals(DayType.WEEKDAY, DayType.of(LocalDate.parse("2024-06-03")))
    }
}
