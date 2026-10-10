package io.github.jpsubway.app.domain.model

import kotlinx.serialization.Serializable

/** tools/layout_schematic.py 가 생성하는 도식 노선도 (좌표 단위: world px, unit = 역 간격) */
@Serializable
data class MapLayout(
    val schemaVersion: Int = 1,
    val regionId: String,
    val unit: Float,
    val width: Float,
    val height: Float,
    val nodes: List<LayoutNode>,
    val lines: List<LayoutLine>,
    /** 다른 지역 노선 중 이 지역 환승역을 지나는 부분 (반투명 표시) */
    val ghosts: List<GhostLine> = emptyList(),
    /** 강 (예: 서울 한강). 노선 아래에 그린다 — tools/rivers.py */
    val rivers: List<RiverPath> = emptyList(),
)

@Serializable
data class LayoutNode(
    val group: String,
    val x: Float,
    val y: Float,
    val labelKo: String,
    val labelJa: String = "",
    val labelDx: Int = 1,
    val labelDy: Int = 0,
    val interchange: Boolean = false,
    val stationIds: List<String> = emptyList(),
)

/**
 * category: 선 디자인 (metro / toei / jr / private / monorail / tram)
 * filter: 상단 필터 그룹 (subway / jr / private)
 * terminals: 종점 아이콘 위치 [[x, y], ...]
 */
@Serializable
data class LayoutLine(
    val lineId: String,
    val color: String,
    val segments: List<LayoutSegment>,
    val code: String = "",
    val category: String = "metro",
    val filter: String = "subway",
    val terminals: List<List<Float>> = emptyList(),
)

/** points: [[x,y], ...], offset: 평행 노선 오프셋 (선 두께 배수) */
@Serializable
data class LayoutSegment(val points: List<List<Float>>, val offset: Float = 0f)

@Serializable
data class GhostLine(
    val lineId: String,
    val color: String,
    val regionId: String,
    val code: String = "",
    val category: String = "metro",
    val points: List<List<Float>>,
)

/** 강 중심선 [[x, y], ...] 과 폭(world px) */
@Serializable
data class RiverPath(
    val id: String = "",
    val name: LocalizedName = LocalizedName(),
    val width: Float = 30f,
    val points: List<List<Float>> = emptyList(),
)
