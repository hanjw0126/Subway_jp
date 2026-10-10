package io.github.jpsubway.app.data.remote

import io.github.jpsubway.app.domain.seoul.SeoulArrival
import io.github.jpsubway.app.domain.seoul.SeoulTrainPos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 서울 실시간 지하철 API — 중계 서버(/seoul/v1/)를 거친다 (앱에는 키가 없다).
 * proxyApiBase: ODPT 중계 주소(…/api/v4/). 같은 호스트의 /seoul/v1/ 를 쓴다.
 */
class SeoulClient(private val http: OkHttpClient, private val json: Json, proxyApiBase: String) {
    private val base: String = proxyApiBase.trim()
        .substringBefore("/api/v4")
        .trimEnd('/')
        .let { if (it.isBlank()) "" else "$it/seoul/v1/" }

    val available: Boolean get() = base.isNotBlank()

    /** 역명(API 표기, '역' 없이)으로 실시간 도착정보 */
    suspend fun arrivals(station: String): List<SeoulArrival> =
        parseArrivals(json, get("arrival", "station", station))

    /** 노선명(예: 2호선)으로 실시간 열차 위치 */
    suspend fun positions(line: String): List<SeoulTrainPos> =
        parsePositions(json, get("position", "line", line))

    private suspend fun get(path: String, key: String, value: String): String = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw IOException("중계 서버 주소가 없습니다")
        val url = base.toHttpUrl().newBuilder().addPathSegment(path).addQueryParameter(key, value).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    companion object {
        /**
         * 응답 목록(listKey)을 꺼낸다. 데이터 없음(INFO-200)은 빈 목록, 그 밖의 오류 코드는 예외.
         * 숫자·문자열이 섞여 와도 되도록 필드는 문자열로 읽는다.
         */
        private fun listOf(json: Json, text: String, listKey: String): List<JsonObject> {
            if (text.isBlank()) return emptyList()
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw IOException("응답 형식 오류")
            val list = root[listKey] as? JsonArray
            if (list == null) {
                val err = (root["errorMessage"] as? JsonObject) ?: root
                val code = err.str("code")
                if (code == "INFO-200" || code.isBlank() && root.containsKey("error").not()) return emptyList()
                throw IOException(err.str("message").ifBlank { root.str("error") }.ifBlank { code })
            }
            return list.mapNotNull { it as? JsonObject }
        }

        fun parseArrivals(json: Json, text: String): List<SeoulArrival> =
            listOf(json, text, "realtimeArrivalList").map { o ->
                SeoulArrival(
                    subwayId = o.str("subwayId"),
                    updnLine = o.str("updnLine"),
                    trainLineNm = o.str("trainLineNm"),
                    statnNm = o.str("statnNm"),
                    btrainSttus = o.str("btrainSttus"),
                    barvlDt = o.str("barvlDt"),
                    btrainNo = o.str("btrainNo"),
                    bstatnNm = o.str("bstatnNm"),
                    arvlMsg2 = o.str("arvlMsg2"),
                    arvlMsg3 = o.str("arvlMsg3"),
                    arvlCd = o.str("arvlCd"),
                    lstcarAt = o.str("lstcarAt"),
                    ordkey = o.str("ordkey"),
                )
            }

        fun parsePositions(json: Json, text: String): List<SeoulTrainPos> =
            listOf(json, text, "realtimePositionList").map { o ->
                SeoulTrainPos(
                    subwayId = o.str("subwayId"),
                    statnNm = o.str("statnNm"),
                    trainNo = o.str("trainNo"),
                    updnLine = o.str("updnLine"),
                    statnTnm = o.str("statnTnm"),
                    trainSttus = o.str("trainSttus"),
                    directAt = o.str("directAt"),
                    lstcarAt = o.str("lstcarAt"),
                )
            }

        private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
