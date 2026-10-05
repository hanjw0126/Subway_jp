package io.github.jpsubway.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 공공교통 오픈데이터센터 API v4 클라이언트 */
class OdptClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL,
    /** 키를 보관하는 중계 서버(Cloudflare Worker). 비어 있으면 사용하지 않는다. */
    private val proxyBaseUrl: String = "",
) {
    suspend fun trainTimetables(railway: String, calendar: String, key: String, direction: String? = null): List<OdptTrainTimetable> =
        get(
            "odpt:TrainTimetable",
            buildMap {
                put("odpt:railway", railway)
                put("odpt:calendar", calendar)
                if (direction != null) put("odpt:railDirection", direction)
            },
            key, OdptTrainTimetable.serializer(),
        )

    suspend fun stationTimetables(railway: String, calendar: String, key: String): List<OdptStationTimetable> =
        get("odpt:StationTimetable", mapOf("odpt:railway" to railway, "odpt:calendar" to calendar), key, OdptStationTimetable.serializer())

    suspend fun trains(railway: String, key: String): List<OdptTrain> =
        get("odpt:Train", mapOf("odpt:railway" to railway), key, OdptTrain.serializer())

    suspend fun trainInformation(railway: String, key: String): List<OdptTrainInformation> =
        get("odpt:TrainInformation", mapOf("odpt:railway" to railway), key, OdptTrainInformation.serializer())

    private suspend fun <T> get(type: String, params: Map<String, String>, key: String, ser: KSerializer<T>): List<T> =
        withContext(Dispatchers.IO) {
            val viaProxy = key == PROXY_KEY
            val base = if (viaProxy) proxyBaseUrl else baseUrl
            if (base.isBlank()) throw IOException("ODPT 중계 서버 주소가 설정되지 않았습니다")
            val url = base.toHttpUrl().newBuilder().addPathSegment(type).apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                if (!viaProxy) addQueryParameter("acl:consumerKey", key)
            }.build()
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("ODPT HTTP ${resp.code}")
                json.decodeFromString(ListSerializer(ser), resp.body?.string().orEmpty().ifBlank { "[]" })
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.odpt.org/api/v4/"

        /** consumerKey 대신 이 값을 넘기면 중계 서버로 요청한다 (APK 에 ODPT 키를 넣지 않기 위함). */
        const val PROXY_KEY = "@proxy"
    }
}
