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

@Serializable
data class LayoutLine(val lineId: String, val color: String, val segments: List<LayoutSegment>)

/** points: [[x,y], ...], offset: 평행 노선 오프셋 (선 두께 배수) */
@Serializable
data class LayoutSegment(val points: List<List<Float>>, val offset: Float = 0f)
