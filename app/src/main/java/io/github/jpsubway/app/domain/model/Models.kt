package io.github.jpsubway.app.domain.model

import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Lang
import kotlinx.serialization.Serializable

/**
 * 다국어 이름.
 * koAlt: 한국어 위키백과(위키데이터) 표기가 앱 표기(ko)와 다를 때만 들어 있다.
 *   역 상세정보에서는 괄호로 함께 보여 주고, 역 검색에도 쓴다 (노선도에는 표시하지 않음).
 */
@Serializable
data class LocalizedName(
    val ja: String = "",
    val ko: String = "",
    val en: String = "",
    val kana: String = "",
    val koAlt: String = "",
)

/** 현재 역명 언어(Lang.names)로 표시. 비어 있으면 다른 언어로 대체 */
fun LocalizedName.display(): String = inLanguage(Lang.names.value)

fun LocalizedName.inLanguage(lang: AppLanguage): String = when (lang) {
    AppLanguage.JA -> ja.ifBlank { ko.ifBlank { en } }
    AppLanguage.EN -> en.ifBlank { ja.ifBlank { ko } }
    else -> ko.ifBlank { ja.ifBlank { en } }
}

/** 상세정보용: 한국어 역명이면 위키백과 표기를 괄호로 덧붙인다 (예: 혼마치 (혼초)) */
fun LocalizedName.detailed(lang: AppLanguage): String {
    val main = inLanguage(lang)
    return if (lang == AppLanguage.KO && koAlt.isNotBlank() && koAlt != main) "$main ($koAlt)" else main
}

@Serializable
data class Direction(val id: String, val name: LocalizedName = LocalizedName())

/** asc = 노선 역 순번이 증가하는 방향, desc = 감소하는 방향 */
@Serializable
data class LineDirections(val asc: Direction, val desc: Direction)

@Serializable
data class Line(
    val id: String,
    val operator: String,
    val code: String,
    val name: LocalizedName,
    val color: String,
    val stations: List<String>,
    val directions: LineDirections,
    val loop: Boolean = false,
    /** "" = 공개 데이터, "c2026" = 공공교통 오픈데이터 챌린지 2026 제공 노선, "seoul" = 서울 열린데이터광장 */
    val source: String = "",
)

@Serializable
data class Station(
    val id: String,
    val lineId: String,
    val code: String = "",
    val name: LocalizedName,
    val lat: Double,
    val lon: Double,
    /** 환승 묶음 ID (같은 역 이름 + 가까운 위치) */
    val group: String,
)

@Serializable
data class Transfer(val from: String, val to: String, val walkSec: Int = 180)

@Serializable
data class Network(
    val schemaVersion: Int = 1,
    val regionId: String,
    val source: String = "",
    val lines: List<Line>,
    val stations: List<Station>,
    val transfers: List<Transfer> = emptyList(),
) {
    val stationById: Map<String, Station> by lazy { stations.associateBy { it.id } }
    val lineById: Map<String, Line> by lazy { lines.associateBy { it.id } }
    val stationsByGroup: Map<String, List<Station>> by lazy { stations.groupBy { it.group } }

    fun stationName(id: String): String = stationById[id]?.name?.display() ?: id.substringAfterLast('.')
    fun groupName(group: String): LocalizedName = stationsByGroup[group]?.firstOrNull()?.name ?: LocalizedName(ko = group)
    fun linesOfGroup(group: String): List<Line> = stationsByGroup[group].orEmpty().mapNotNull { lineById[it.lineId] }
}

/** country: "jp" = 일본, "kr" = 한국. 메인 화면에서 고른 국가의 지역만 보여 준다 */
@Serializable
data class RegionInfo(
    val id: String,
    val name: LocalizedName,
    val realtime: Boolean = false,
    val operators: List<String> = emptyList(),
    val note: String = "",
    val country: String = "jp",
)

/** 시각은 "운행일 기준 초". 04:00 이전 시각은 +24h (예: 00:12 → 87120) */
@Serializable
data class StopTime(val stationId: String, val arr: Int, val dep: Int)

@Serializable
data class Trip(
    val id: String,
    val lineId: String,
    val trainNumber: String,
    val directionId: String,
    val destinationId: String,
    val trainType: String = "",
    val stops: List<StopTime>,
)

@Serializable
data class Timetable(
    val regionId: String,
    val dayType: String,
    /** "odpt" | "odpt-cache" | "demo" */
    val source: String,
    val fetchedAtEpochSec: Long,
    val trips: List<Trip>,
) {
    val isDemo: Boolean get() = source == "demo"
}

data class TrainPosition(
    val lineId: String,
    val trainNumber: String,
    val delaySec: Int,
    val fromStation: String?,
    val toStation: String?,
)

data class LineStatus(val lineId: String, val textJa: String, val textKo: String, val isNormal: Boolean)

data class RealtimeSnapshot(
    val trains: Map<String, TrainPosition> = emptyMap(),
    val statuses: Map<String, LineStatus> = emptyMap(),
    val fetchedAtEpochSec: Long = 0,
    val available: Boolean = false,
    val error: String? = null,
) {
    companion object {
        fun key(lineId: String, trainNumber: String) = "$lineId|$trainNumber"
    }
}

enum class ArrivalPhase { ARRIVED, APPROACHING, LEFT_PREVIOUS, AT_PREVIOUS, EN_ROUTE, WAITING_AT_ORIGIN }

enum class ArrivalSource { REALTIME, SCHEDULE }

data class Arrival(
    val trip: Trip,
    val scheduledSec: Int,
    val predictedSec: Int,
    val delaySec: Int,
    val minutes: Int,
    val stopsAway: Int?,
    val phase: ArrivalPhase,
    val source: ArrivalSource,
)

data class DirectionBoard(
    val direction: Direction,
    val prevStationId: String?,
    val nextStationId: String?,
    val arrivals: List<Arrival>,
    val firstDepSec: Int? = null,
    val lastDepSec: Int? = null,
)
