package io.github.jpsubway.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import io.github.jpsubway.app.domain.model.MapLayout

/** 한 구간을 여러 노선이 공유할 때 나란히 그리기 위해 폴리라인을 법선 방향으로 평행 이동 */
internal fun offsetPolyline(pts: List<Offset>, d: Float): List<Offset> {
    if (d == 0f || pts.size < 2) return pts
    fun normal(a: Offset, b: Offset): Offset {
        val v = b - a
        val len = v.getDistance()
        return if (len < 1e-3f) Offset.Zero else Offset(-v.y / len, v.x / len)
    }
    return pts.indices.map { i ->
        val prev = if (i > 0) normal(pts[i - 1], pts[i]) else null
        val next = if (i < pts.lastIndex) normal(pts[i], pts[i + 1]) else null
        val n = when {
            prev == null -> next ?: Offset.Zero
            next == null -> prev
            else -> {
                val m = prev + next
                val len = m.getDistance()
                if (len < 1e-3f) prev else {
                    val u = m / len
                    val cos = (u.x * prev.x + u.y * prev.y).coerceAtLeast(0.35f)
                    u / cos
                }
            }
        }
        pts[i] + n * d
    }
}

internal fun polylinePath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
}

internal fun layoutBounds(layout: MapLayout): Rect {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    fun acc(x: Float, y: Float) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    layout.nodes.forEach { acc(it.x, it.y) }
    layout.lines.forEach { l -> l.segments.forEach { s -> s.points.forEach { p -> if (p.size >= 2) acc(p[0], p[1]) } } }
    if (minX > maxX) return Rect(0f, 0f, layout.width.coerceAtLeast(1f), layout.height.coerceAtLeast(1f))
    return Rect(minX, minY, maxX, maxY)
}

/** 지정한 노선들(구간 + 종점 아이콘)을 감싸는 영역. 해당 노선이 노선도에 없으면 null */
internal fun linesBounds(layout: MapLayout, lineIds: Set<String>): Rect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    fun acc(p: List<Float>) {
        if (p.size < 2) return
        if (p[0] < minX) minX = p[0]
        if (p[1] < minY) minY = p[1]
        if (p[0] > maxX) maxX = p[0]
        if (p[1] > maxY) maxY = p[1]
    }
    layout.lines.filter { it.lineId in lineIds }.forEach { l ->
        l.segments.forEach { s -> s.points.forEach(::acc) }
        l.terminals.forEach(::acc)
    }
    if (minX > maxX) return null
    return Rect(minX, minY, maxX, maxY)
}
