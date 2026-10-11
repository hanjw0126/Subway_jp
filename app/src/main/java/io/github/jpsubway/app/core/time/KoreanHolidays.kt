package io.github.jpsubway.app.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 한국 공휴일 (서울 지하철 휴일 시간표 적용일 판정용).
 *  - 고정: 1/1, 3/1, 5/5, 6/6, 8/15, 10/3, 10/9, 12/25
 *  - 음력: 설날·추석(전날·당일·다음날), 부처님오신날 — 연도별 표 (2025~2030)
 *  - 선거일: 알려진 것만
 *  - 대체공휴일: 설날·추석은 일요일 또는 다른 공휴일과 겹치면, 3·1절·어린이날·광복절·개천절·한글날·
 *    성탄절·부처님오신날은 토·일요일 또는 다른 공휴일과 겹치면 그 뒤 첫 평일을 쉬는 날로 한다.
 * 표에 없는 연도는 고정 공휴일만 따진다 (연도를 늘릴 때 여기에 추가).
 */
object KoreanHolidays {
    private val fixedNoSubstitute = listOf(1 to 1, 6 to 6)
    private val fixedWithSubstitute = listOf(3 to 1, 5 to 5, 8 to 15, 10 to 3, 10 to 9, 12 to 25)
    private val seollal = mapOf(2025 to "2025-01-29", 2026 to "2026-02-17", 2027 to "2027-02-07", 2028 to "2028-01-26", 2029 to "2029-02-13", 2030 to "2030-02-03")
    private val chuseok = mapOf(2025 to "2025-10-06", 2026 to "2026-09-25", 2027 to "2027-09-15", 2028 to "2028-10-03", 2029 to "2029-09-22", 2030 to "2030-09-12")
    private val buddha = mapOf(2025 to "2025-05-05", 2026 to "2026-05-24", 2027 to "2027-05-13", 2028 to "2028-05-02", 2029 to "2029-05-20", 2030 to "2030-05-09")
    private val elections = listOf("2026-06-03", "2028-04-12")
    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()

    fun isHoliday(d: LocalDate): Boolean = d in holidays(d.year)

    fun holidays(year: Int): Set<LocalDate> = cache.getOrPut(year) { compute(year) }

    private fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    private fun compute(year: Int): Set<LocalDate> {
        val singlesSub = fixedWithSubstitute.map { (m, d) -> LocalDate.of(year, m, d) } +
            listOfNotNull(buddha[year]).map(LocalDate::parse)
        val singlesNoSub = fixedNoSubstitute.map { (m, d) -> LocalDate.of(year, m, d) } +
            elections.map(LocalDate::parse).filter { it.year == year }
        val groups = listOfNotNull(seollal[year], chuseok[year]).map(LocalDate::parse).map { c -> (-1L..1L).map { c.plusDays(it) } }
        val all = singlesSub + singlesNoSub + groups.flatten() // 같은 날이 두 번 나오면 겹침
        val count = all.groupingBy { it }.eachCount()
        val result = all.toMutableSet()
        fun nextFree(after: LocalDate): LocalDate {
            var d = after.plusDays(1)
            while (weekend(d) || d in result) d = d.plusDays(1)
            return d
        }
        for (g in groups) {
            val need = g.count { it.dayOfWeek == DayOfWeek.SUNDAY || (count[it] ?: 0) > 1 }
            repeat(need) { result += nextFree(g.last()) }
        }
        for (d in singlesSub) {
            if (weekend(d) || (count[d] ?: 0) > 1) result += nextFree(d)
        }
        return result
    }
}
