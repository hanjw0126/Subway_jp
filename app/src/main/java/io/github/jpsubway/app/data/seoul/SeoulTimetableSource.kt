package io.github.jpsubway.app.data.seoul

import android.content.Context
import io.github.jpsubway.app.core.time.DayType
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.StopTime
import io.github.jpsubway.app.domain.model.Timetable
import io.github.jpsubway.app.domain.model.Trip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * 서울교통공사_서울 도시철도 열차운행시각표 (tools/seoul/build_timetable.py 가 만든
 * assets/regions/<지역>/timetable_<DAY|SAT|END>.json.gz) → 경로 탐색용 Timetable.
 * 형식: {"source", "lines": [노선 ID], "stations": [역 ID],
 *        "trips": [[노선#, 열차코드, 방향(0=asc,1=desc), 종착역#, 급행(0/1), [역#...], [도착초...], [정차초...]]]}
 */
class SeoulTimetableSource(private val context: Context, private val json: Json) {

    /** 자산이 없으면 null */
    fun load(network: Network, day: DayType, nowEpochSec: Long): Timetable? {
        val text = try {
            GZIPInputStream(context.assets.open("regions/${network.regionId}/timetable_${code(day)}.json.gz"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            return null
        }
        return parse(json, text, network, day, nowEpochSec)
    }

    companion object {
        fun code(day: DayType): String = when (day) {
            DayType.WEEKDAY -> "DAY"
            DayType.SATURDAY -> "SAT"
            DayType.HOLIDAY -> "END"
        }

        fun parse(json: Json, text: String, network: Network, day: DayType, nowEpochSec: Long): Timetable {
            val root = json.parseToJsonElement(text).jsonObject
            val lines = root.getValue("lines").jsonArray.map { it.jsonPrimitive.content }
            val stations = root.getValue("stations").jsonArray.map { it.jsonPrimitive.content }
            val source = root["source"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val trips = ArrayList<Trip>()
            root.getValue("trips").jsonArray.forEachIndexed { i, el ->
                val a = el.jsonArray
                val lineId = lines[a[0].jsonPrimitive.int]
                val line = network.lineById[lineId] ?: return@forEachIndexed
                val code = a[1].jsonPrimitive.content
                val dir = if (a[2].jsonPrimitive.int == 0) line.directions.asc.id else line.directions.desc.id
                val dest = stations[a[3].jsonPrimitive.int]
                val express = a[4].jsonPrimitive.int == 1
                val st = a[5].jsonArray
                val arr = a[6].jsonArray
                val dwell = a[7].jsonArray
                val stops = List(st.size) { k ->
                    val t = arr[k].jsonPrimitive.int
                    StopTime(stations[st[k].jsonPrimitive.int], t, t + dwell[k].jsonPrimitive.int)
                }
                if (stops.size >= 2) {
                    trips += Trip("$lineId:$code:$i", lineId, code, dir, dest, if (express) "급행" else "", stops)
                }
            }
            return Timetable(network.regionId, day.name, "seoul:$source", nowEpochSec, trips)
        }
    }
}
