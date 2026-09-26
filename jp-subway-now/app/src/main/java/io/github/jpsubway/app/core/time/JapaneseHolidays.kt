package io.github.jpsubway.app.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/** 일본 국민의 축일 (2000년 이후 규칙, 춘분·추분은 근사식) + 대체휴일 + 국민의 휴일 */
object JapaneseHolidays {
    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()

    fun isHoliday(d: LocalDate): Boolean = d in cache.getOrPut(d.year) { compute(d.year) }

    /** 연말연시(12/30~1/3)는 대부분의 사업자가 휴일 시간표로 운행 */
    fun isYearEnd(d: LocalDate): Boolean =
        (d.monthValue == 12 && d.dayOfMonth >= 30) || (d.monthValue == 1 && d.dayOfMonth <= 3)

    fun holidaysOf(year: Int): Set<LocalDate> = cache.getOrPut(year) { compute(year) }

    private fun compute(y: Int): Set<LocalDate> {
        fun day(m: Int, d: Int) = LocalDate.of(y, m, d)
        fun monday(m: Int, n: Int) = LocalDate.of(y, m, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val k = y - 1980
        val spring = floor(20.8431 + 0.242194 * k - floor(k / 4.0)).toInt()
        val autumn = floor(23.2488 + 0.242194 * k - floor(k / 4.0)).toInt()
        val base = TreeSet<LocalDate>()
        base.addAll(
            listOf(
                day(1, 1), monday(1, 2), day(2, 11), day(3, spring), day(4, 29), day(5, 3), day(5, 4), day(5, 5),
                monday(7, 3), day(8, 11), monday(9, 3), day(9, autumn), monday(10, 2), day(11, 3), day(11, 23),
            ),
        )
        if (y >= 2020) base.add(day(2, 23))
        // 국민의 휴일: 두 공휴일 사이에 낀 평일
        for (h in base.toList()) {
            val mid = h.plusDays(1)
            if (mid !in base && h.plusDays(2) in base && mid.dayOfWeek != DayOfWeek.SUNDAY) base.add(mid)
        }
        // 대체 휴일: 일요일과 겹친 공휴일 → 다음 비공휴일
        for (h in base.filter { it.dayOfWeek == DayOfWeek.SUNDAY }) {
            var d = h.plusDays(1)
            while (d in base) d = d.plusDays(1)
            base.add(d)
        }
        return base
    }
}
