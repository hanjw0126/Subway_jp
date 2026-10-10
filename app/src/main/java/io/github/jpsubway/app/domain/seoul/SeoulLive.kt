package io.github.jpsubway.app.domain.seoul

/** 서울 실시간 도착정보 API(realtimeStationArrival) 한 행 — 값은 모두 원본 문자열 */
data class SeoulArrival(
    val subwayId: String = "",
    val updnLine: String = "",
    val trainLineNm: String = "",
    val statnNm: String = "",
    val btrainSttus: String = "",
    val barvlDt: String = "",
    val btrainNo: String = "",
    val bstatnNm: String = "",
    val arvlMsg2: String = "",
    val arvlMsg3: String = "",
    val arvlCd: String = "",
    val lstcarAt: String = "",
    val ordkey: String = "",
)

/** 화면에 보여 줄 도착 한 건 */
data class LiveArrival(
    /** 종착역 (한국어 원문) */
    val destination: String,
    /** API 문구 원문 (예: "3분 후 (서울)", "[4]번째 전역 (회현)") */
    val messageKo: String,
    /** arvlCd: 0 진입, 1 도착, 3 전역 출발, 4 전역 진입, 5 전역 도착, 99 운행 중 */
    val code: String,
    /** 도착 예정 시각(epoch 초). API 가 초를 주지 않으면 null */
    val etaEpochSec: Long?,
    val stopsAway: Int?,
    /** 급행·ITX 등 (일반 열차는 "") */
    val trainType: String,
    val last: Boolean,
    val trainNo: String,
)

/** 방면 카드 한 장: updn = 상행/하행/내선/외선, heading = 다음 역 방면 */
data class LiveBoard(val updn: String, val heading: String, val arrivals: List<LiveArrival>)

/** 서울 실시간 도착정보 → 노선별 방면 카드 */
object SeoulLive {
    /** API subwayId → 앱 노선 키 (kr.Seoul.<키>[.지선]) */
    val lineKeyBySubwayId = mapOf(
        "1001" to "1", "1002" to "2", "1003" to "3", "1004" to "4", "1005" to "5",
        "1006" to "6", "1007" to "7", "1008" to "8", "1009" to "9",
        "1061" to "KJ", "1063" to "KJ", "1065" to "AREX", "1067" to "GC", "1075" to "SB",
        "1077" to "SBD", "1081" to "GG", "1092" to "UI", "1093" to "WS", "1094" to "SL", "1032" to "GTXA",
    )

    private val updnOrder = listOf("상행", "내선", "하행", "외선")

    fun isSeoulLine(lineId: String) = lineId.startsWith("kr.Seoul.")

    /** kr.Seoul.2.Seongsu → "2" */
    fun lineKey(lineId: String): String = lineId.removePrefix("kr.Seoul.").substringBefore('.')

    /** API 역명: '역'을 뷐다 (서울역 → 서울) */
    fun apiStationName(ko: String): String {
        val n = ko.trim()
        return if (n.length > 1 && n.endsWith("역")) n.dropLast(1) else n
    }

    /** "성수행 - 을지로입구방면" → "을지로입구" */
    fun headingOf(trainLineNm: String): String? =
        trainLineNm.substringAfter(" - ", "").trim().removeSuffix("방면").trim().ifBlank { null }

    /** "[4]번째 전역 (회현)" → 4 */
    fun stopsAway(message: String): Int? = Regex("""\[(\d+)]번째 전역""").find(message)?.groupValues?.get(1)?.toIntOrNull()

    fun toLive(a: SeoulArrival, fetchedEpochSec: Long): LiveArrival {
        val sec = a.barvlDt.trim().toIntOrNull()?.takeIf { it > 0 }
        return LiveArrival(
            destination = a.bstatnNm.trim(),
            messageKo = a.arvlMsg2.trim(),
            code = a.arvlCd.trim(),
            etaEpochSec = sec?.let { fetchedEpochSec + it },
            stopsAway = stopsAway(a.arvlMsg2),
            trainType = a.btrainSttus.trim().takeIf { it.isNotBlank() && it != "일반" }.orEmpty(),
            last = a.lstcarAt.trim() == "1",
            trainNo = a.btrainNo.trim(),
        )
    }

    /**
     * 역 하나의 도착정보 중 lineKey 노선만 골라 방면(상행/내선/하행/외선)별 카드로 나눈다.
     * 방금 떠난 열차(arvlCd 2)는 빼고, API 표시 순서(ordkey)대로 perBoard 건까지.
     */
    fun boards(rows: List<SeoulArrival>, lineKey: String, fetchedEpochSec: Long, perBoard: Int = 3): List<LiveBoard> =
        rows.asSequence()
            .filter { lineKeyBySubwayId[it.subwayId.trim()] == lineKey && it.arvlCd.trim() != "2" }
            .groupBy { it.updnLine.trim() }
            .toList()
            .sortedBy { (k, _) -> updnOrder.indexOf(k).let { if (it < 0) 99 else it } }
            .map { (updn, rs) ->
                val sorted = rs.sortedWith(
                    compareBy<SeoulArrival>({ it.ordkey.ifBlank { "~" } }, { it.barvlDt.trim().toIntOrNull() ?: Int.MAX_VALUE }),
                )
                LiveBoard(
                    updn = updn,
                    heading = sorted.firstNotNullOfOrNull { headingOf(it.trainLineNm) }.orEmpty(),
                    arrivals = sorted.take(perBoard).map { toLive(it, fetchedEpochSec) },
                )
            }
}
