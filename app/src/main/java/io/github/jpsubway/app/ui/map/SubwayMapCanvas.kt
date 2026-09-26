package io.github.jpsubway.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jpsubway.app.domain.model.MapLayout
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.MapBackground
import io.github.jpsubway.app.ui.theme.ToRed
import io.github.jpsubway.app.ui.theme.parseColor
import kotlin.math.min

/** layout.json 의 segment.offset 단위 (tools/layout_schematic.py 출력 기준으로 자동 결정됨) */
private const val OFFSET_IN_LINE_WIDTHS = true

/**
 * 도식 노선도. 두 손가락 확대/이동, 더블탭 확대, 역 탭 → onStationTap(환승그룹 ID).
 * 역명은 확대 배율과 무관하게 같은 글자 크기로 그려 가독성을 유지한다.
 */
@Composable
fun SubwayMapCanvas(
    layout: MapLayout,
    network: Network,
    fromGroup: String?,
    toGroup: String?,
    highlighted: String?,
    onStationTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tapCallback by rememberUpdatedState(onStationTap)
    val bounds = remember(layout) { layoutBounds(layout) }
    val lineColors = remember(layout) { layout.lines.associate { it.lineId to parseColor(it.color) } }
    val nodeColors = remember(layout, network) {
        layout.nodes.associate { n ->
            val lineId = n.stationIds.firstNotNullOfOrNull { network.stationById[it] }?.lineId
            n.group to (lineId?.let { network.lineById[it] }?.let { parseColor(it.color) } ?: Color.DarkGray)
        }
    }
    val labels = remember(layout, measurer) {
        val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF212529))
        val strong = style.copy(fontWeight = FontWeight.Bold)
        layout.nodes.associate { it.group to measurer.measure(AnnotatedString(it.labelKo), style = if (it.interchange) strong else style) }
    }

    BoxWithConstraints(modifier.background(MapBackground).clipToBounds()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val pad = with(density) { 56.dp.toPx() }
        val fit = remember(bounds, wPx, hPx) {
            min((wPx - pad) / bounds.width.coerceAtLeast(1f), (hPx - pad) / bounds.height.coerceAtLeast(1f)).coerceAtLeast(0.01f)
        }
        var scale by remember(layout.regionId, fit) { mutableFloatStateOf(fit) }
        var pan by remember(layout.regionId, fit) {
            mutableStateOf(Offset(wPx / 2 - bounds.center.x * fit, hPx / 2 - bounds.center.y * fit))
        }

        val gestures = Modifier
            .pointerInput(layout.regionId, fit) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(fit * 0.6f, fit * 10f)
                    val z = ns / scale
                    pan = (pan - centroid) * z + centroid + panChange
                    scale = ns
                }
            }
            .pointerInput(layout.regionId, fit) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        val ns = (scale * 2f).coerceAtMost(fit * 10f)
                        val z = ns / scale
                        pan = (pan - p) * z + p
                        scale = ns
                    },
                    onTap = { p ->
                        val hit = 28.dp.toPx()
                        val best = layout.nodes.minByOrNull { n -> (Offset(n.x, n.y) * scale + pan - p).getDistance() }
                        if (best != null && (Offset(best.x, best.y) * scale + pan - p).getDistance() <= hit) {
                            tapCallback(best.group)
                        }
                    },
                )
            }

        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val zoomLevel = scale / fit
            val lw = 4.dp.toPx() * zoomLevel.coerceIn(0.9f, 2.2f)
            fun tr(x: Float, y: Float) = Offset(x * scale + pan.x, y * scale + pan.y)

            // 1) 노선
            layout.lines.forEach { ll ->
                val color = lineColors[ll.lineId] ?: Color.Gray
                ll.segments.forEach { seg ->
                    val pts = seg.points.mapNotNull { if (it.size >= 2) tr(it[0], it[1]) else null }
                    if (pts.size >= 2) {
                        val off = if (OFFSET_IN_LINE_WIDTHS) seg.offset * lw else seg.offset * scale
                        drawPath(
                            polylinePath(offsetPolyline(pts, off)),
                            color,
                            style = Stroke(width = lw, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }

            // 2) 역 + 역명
            val r = lw * 0.95f
            val showAll = zoomLevel >= 1.4f || layout.nodes.size <= 40
            layout.nodes.forEach { n ->
                val c = tr(n.x, n.y)
                val visible = c.x > -120f && c.y > -60f && c.x < size.width + 120f && c.y < size.height + 60f
                if (visible) {
                    val radius = if (n.interchange) r * 1.45f else r
                    if (n.group == highlighted) drawCircle(Color(0x553B5BDB), radius * 2.4f, c)
                    val fill = when (n.group) {
                        fromGroup -> FromGreen
                        toGroup -> ToRed
                        else -> Color.White
                    }
                    drawCircle(fill, radius, c)
                    drawCircle(
                        if (n.interchange) Color(0xFF343A40) else nodeColors[n.group] ?: Color.DarkGray,
                        radius,
                        c,
                        style = Stroke(width = lw * 0.45f),
                    )
                    val tl = labels[n.group]
                    if (tl != null && (showAll || n.interchange || n.group == fromGroup || n.group == toGroup || n.group == highlighted)) {
                        val gap = radius + 3.dp.toPx()
                        val w = tl.size.width.toFloat()
                        val h = tl.size.height.toFloat()
                        val x = when {
                            n.labelDx > 0 -> c.x + gap
                            n.labelDx < 0 -> c.x - gap - w
                            else -> c.x - w / 2
                        }
                        val y = when {
                            n.labelDy > 0 -> c.y + gap
                            n.labelDy < 0 -> c.y - gap - h
                            else -> c.y - h / 2
                        }
                        drawRoundRect(
                            Color(0xE6FFFFFF),
                            topLeft = Offset(x - 2.dp.toPx(), y),
                            size = Size(w + 4.dp.toPx(), h),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        drawText(tl, topLeft = Offset(x, y))
                    }
                }
            }
        }
    }
}
