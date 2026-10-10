package io.github.jpsubway.app.data.remote

import io.github.jpsubway.app.domain.seoul.SeoulArrival
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
    suspend fun arrivals(station: String): List<SeoulArrival> = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw IOException("중계 서버 주소가 없습니다")
        val url = base.toHttpUrl().newBuilder().addPathSegment("arrival").addQueryParameter("station", station).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            parseArrivals(json, resp.body?.string().orEmpty())
        }
    }

    companion object {
        /**
         * 응답 해석. 숫자·문자열이 섞여 와도 되도록 필드를 문자열로 읽는다.
         * 데이터 없음(INFO-200)은 빈 목록, 그 밖의 오류 코드는 예외.
         */
        fun parseArrivals(json: Json, text: String): List<SeoulArrival> {
            if (text.isBlank()) return emptyList()
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw IOException("응답 형식 오류")
            val list = root["realtimeArrivalList"] as? JsonArray
            if (list == null) {
                val err = (root["errorMessage"] as? JsonObject) ?: root
                val code = err.str("code")
                if (code == "INFO-200" || code.isBlank() && root.containsKey("error").not()) return emptyList()
                throw IOException(err.str("message").ifBlank { root.str("error") }.ifBlank { code })
            }
            return list.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
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
        }

        private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
