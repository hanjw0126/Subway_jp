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
) {
    suspend fun trainTimetables(railway: String, calendar: String, key: String): List<OdptTrainTimetable> =
        get("odpt:TrainTimetable", mapOf("odpt:railway" to railway, "odpt:calendar" to calendar), key, OdptTrainTimetable.serializer())

    suspend fun trains(railway: String, key: String): List<OdptTrain> =
        get("odpt:Train", mapOf("odpt:railway" to railway), key, OdptTrain.serializer())

    suspend fun trainInformation(railway: String, key: String): List<OdptTrainInformation> =
        get("odpt:TrainInformation", mapOf("odpt:railway" to railway), key, OdptTrainInformation.serializer())

    private suspend fun <T> get(type: String, params: Map<String, String>, key: String, ser: KSerializer<T>): List<T> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.toHttpUrl().newBuilder().addPathSegment(type).apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("acl:consumerKey", key)
            }.build()
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("ODPT HTTP ${resp.code}")
                json.decodeFromString(ListSerializer(ser), resp.body?.string().orEmpty().ifBlank { "[]" })
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.odpt.org/api/v4/"
    }
}
