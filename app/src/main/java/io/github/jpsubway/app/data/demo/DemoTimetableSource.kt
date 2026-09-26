package io.github.jpsubway.app.data.demo

import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.Station
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 토큰이 없거나 실시간 미지원 지역용 가상 시간표.
 * 역 간 거리로 주행시간을 추정하고, 시간대별 배차 간격으로 05:00~24:15 열차를 만든다.
 * 실제 시간표가 아니므로 UI 에 "데모 시간표" 로 표시한다.
 */
object DemoTimetableSource {
    private const val FIRST = 5 * 3600
    private const val LAST = 24 * 3600 + 15 * 60
    private const val DWELL = 20

    fun generate(network: Network, dayType: DayType): Timetable {
        val trips = mutableListOf<Trip>()
        for (line in network.lines) {
            val st = line.stations.mapNotNull { network.stationById[it] }
            if (st.size < 2) continue
            val run = IntArray(st.size - 1) { i -> runSec(st[i], st[i + 1]) }
            for (asc in listOf(true, false)) {
                val order = if (asc) st.indices.toList() else st.indices.reversed()
                val dir = if (asc) line.directions.asc else line.directions.desc
                val tag = if (asc) "A" else "B"
                var t = FIRST + if (asc) 0 else 120
                var n = 0
                while (t <= LAST) {
                    val stops = ArrayList<StopTime>(order.size)
                    var clock = t
                    order.forEachIndexed { k, idx ->
                        val dwell = if (k == 0 || k == order.lastIndex) 0 else DWELL
                        stops += StopTime(st[idx].id, clock, clock + dwell)
                        if (k < order.lastIndex) clock += dwell + if (asc) run[idx] else run[idx - 1]
                    }
                    val number = String.format(Locale.ROOT, "D%s%s%03d", line.code, tag, n)
                    trips += Trip("demo.${line.id}.$tag.$n", line.id, number, dir.id, stops.last().stationId, "각역정차", stops)
                    t += headway(t, dayType)
                    n++
                }
            }
        }
        return Timetable(network.regionId, dayType.name, "demo", System.currentTimeMillis() / 1000, trips)
    }

    fun headway(t: Int, d: DayType): Int {
        val h = t / 3600
        return when {
            h < 6 || h >= 23 -> 480
            d == DayType.WEEKDAY && (h in 7..9 || h in 17..19) -> 180
            d == DayType.WEEKDAY -> 300
            else -> 360
        }
    }

    fun runSec(a: Station, b: Station): Int {
        val km = haversineKm(a.lat, a.lon, b.lat, b.lon)
        return (((50 + km * 75) / 15).roundToInt() * 15).coerceIn(60, 300)
    }

    private fun haversineKm(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = Math.PI / 180
        val h = sin((la2 - la1) * r / 2).let { it * it } + cos(la1 * r) * cos(la2 * r) * sin((lo2 - lo1) * r / 2).let { it * it }
        return 2 * 6371.0 * asin(sqrt(h))
    }
}
