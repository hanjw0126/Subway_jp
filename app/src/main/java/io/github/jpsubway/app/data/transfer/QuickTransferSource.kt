package io.github.jpsubway.app.data.transfer

import android.content.Context
import io.github.jpsubway.app.domain.transfer.QuickTransferRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 빠른 환승 자산 (tools/seoul/build_quick_transfer.py 가 만든 assets/regions/<지역>/quick_transfer.json).
 * 지역에 자산이 없으면(현재 일본 지역) 빈 목록.
 */
class QuickTransferSource(private val context: Context, private val json: Json) {
    private val cache = ConcurrentHashMap<String, List<QuickTransferRow>>()

    fun rows(regionId: String): List<QuickTransferRow> = cache.getOrPut(regionId) {
        try {
            context.assets.open("regions/$regionId/quick_transfer.json").bufferedReader(Charsets.UTF_8).use { parse(json, it.readText()) }
        } catch (e: IOException) {
            emptyList()
        }
    }

    companion object {
        /** {"rows": [[노선키, 역명, 종착역명, 환승노선키, 환승 뒤 다음 역명, 칸, 문]]} */
        fun parse(json: Json, text: String): List<QuickTransferRow> =
            json.parseToJsonElement(text).jsonObject["rows"]?.jsonArray.orEmpty().mapNotNull { el ->
                val a = el.jsonArray
                if (a.size < 7) return@mapNotNull null
                QuickTransferRow(
                    line = a[0].jsonPrimitive.content,
                    station = a[1].jsonPrimitive.content,
                    toward = a[2].jsonPrimitive.content,
                    xferLine = a[3].jsonPrimitive.content,
                    xferNext = a[4].jsonPrimitive.content,
                    car = a[5].jsonPrimitive.int,
                    door = a[6].jsonPrimitive.int,
                )
            }
    }
}
