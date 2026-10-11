package io.github.jpsubway.app.core.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 철도의 "운행일": 새벽 4시 이전은 전날 운행일로 취급하고 시각에 +24h 를 더한다.
 * 일본(JST)과 한국(KST)은 모두 UTC+9(일광절약시간 없음)라 같은 시계를 쓴다.
 */
object ServiceClock {
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    const val DAY_START_HOUR = 4

    data class Now(val serviceDate: LocalDate, val seconds: Int, val epochSec: Long)

    fun now(clock: Clock = Clock.systemUTC()): Now = at(Instant.now(clock))

    fun at(instant: Instant): Now {
        val t = instant.atZone(JST)
        val sec = t.toLocalTime().toSecondOfDay()
        return if (t.hour < DAY_START_HOUR) Now(t.toLocalDate().minusDays(1), sec + 86_400, instant.epochSecond)
        else Now(t.toLocalDate(), sec, instant.epochSecond)
    }

    /** "05:01" → 18060, "00:12" → 87120. 직전 시각보다 12시간 이상 작으면 다음날로 보정 */
    fun parse(hhmm: String, prev: Int? = null): Int {
        val parts = hhmm.trim().split(":")
        val h = parts[0].toInt()
        val m = parts.getOrNull(1)?.toInt() ?: 0
        var s = h * 3600 + m * 60
        if (h < DAY_START_HOUR) s += 86_400
        if (prev != null && s + 43_200 < prev) s += 86_400
        return s
    }

    fun format(sec: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", (sec / 3600) % 24, (sec % 3600) / 60)
}

enum class DayType(val labelKo: String, val odptCalendars: List<String>) {
    WEEKDAY("평일", listOf("odpt.Calendar:Weekday")),
    SATURDAY("토요일", listOf("odpt.Calendar:Saturday", "odpt.Calendar:SaturdayHoliday")),
    HOLIDAY("일요일·공휴일", listOf("odpt.Calendar:Holiday", "odpt.Calendar:SaturdayHoliday", "odpt.Calendar:SundayHoliday"));

    companion object {
        /** 일본 기준 (일본 공휴일·연말연시) */
        fun of(d: LocalDate): DayType = when {
            d.dayOfWeek == DayOfWeek.SUNDAY || JapaneseHolidays.isHoliday(d) || JapaneseHolidays.isYearEnd(d) -> HOLIDAY
            d.dayOfWeek == DayOfWeek.SATURDAY -> SATURDAY
            else -> WEEKDAY
        }

        /** 한국 기준 (한국 공휴일·대체공휴일) — 서울 지하철은 토요일 / 일요일·공휴일 시간표를 따로 쓴다 */
        fun ofKorea(d: LocalDate): DayType = when {
            d.dayOfWeek == DayOfWeek.SUNDAY || KoreanHolidays.isHoliday(d) -> HOLIDAY
            d.dayOfWeek == DayOfWeek.SATURDAY -> SATURDAY
            else -> WEEKDAY
        }

        /** 지역의 국가("jp" / "kr")에 맞는 요일 구분 */
        fun of(d: LocalDate, country: String): DayType = if (country == "kr") ofKorea(d) else of(d)
    }
}
