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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
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
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF212529)
private val InkSoft = Color(0xFF495057)

/** 역 공점 반지름: 노선 수가 많을수록 크게 (평행 노선 묶음을 덮도록) */
private fun stationRadius(lineCount: Int, lw: Float): Float =
    if (lineCount <= 1) lw * 0.95f else lw * (0.5f + 0.5f * min(lineCount, 4))

/**
 * 역 공점. 노선 수에 따라 단계별 디자인:
 *  1개 = 흰 원 + 노선색 테두리
 *  2개 = 회색 외곽 + 노선색 2분할 링
 *  3개 = 진한 외곽 + 3분할 링 (더 큼)
 *  4개 이상 = 굵은 외곽 + N분할 링 + 가운데 노선 수 숫자
 */
private fun DrawScope.drawStation(c: Offset, colors: List<Color>, lw: Float, fill: Color, countLabel: TextLayoutResult?) {
    val k = colors.size
    val radius = stationRadius(k, lw)
    if (k <= 1) {
        drawCircle(fill, radius, c)
        drawCircle(colors.firstOrNull() ?: Color.DarkGray, radius, c, style = Stroke(width = lw * 0.45f))
        return
    }
    val outline = (when {
        k >= 4 -> lw * 0.34f
        k == 3 -> lw * 0.27f
        else -> lw * 0.2f
    }).coerceAtLeast(1f)
    drawCircle(if (k == 2) InkSoft else Ink, radius, c)
    val r2 = radius - outline
    val sweep = 360f / k
    colors.forEachIndexed { i, col ->
        drawArc(
            color = col,
            startAngle = -90f + i * sweep,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(c.x - r2, c.y - r2),
            size = Size(r2 * 2, r2 * 2),
        )
    }
    val inner = r2 * (if (k >= 4) 0.68f else 0.58f)
    drawCircle(fill, inner, c)
    if (countLabel != null) {
        val f = (inner * 1.3f) / countLabel.size.height.coerceAtLeast(1)
        val w = countLabel.size.width * f
        val h = countLabel.size.height * f
        withTransform({
            translate(c.x - w / 2, c.y - h / 2)
            scale(f, f, pivot = Offset.Zero)
        }) { drawText(countLabel) }
    }
}

/**
 * 도식 노선도. 두 손가락 확대/이동, 더블탭 확대, 역 탭 → onStationTap(환승그룹 ID).
 * 노선 굵기·역 공점·역명 모두 지도 좌표(역 간격 unit) 기준 크기라서 확대/축소에 비례해 커지고 작아진다.
 * 너무 작아 읽을 수 없는 역명은 숨기되, 환승 노선이 많은 역부터 먼저 보인다.
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
    // 역(환승그룹)별 노선 색 — 같은 색(본선/지선)은 하나로 친다
    val nodeColors = remember(layout, network) {
        layout.nodes.associate { n ->
            val cols = n.stationIds
                .mapNotNull { network.stationById[it]?.lineId }
                .distinct()
                .mapNotNull { network.lineById[it]?.color }
                .map { parseColor(it) }
                .distinct()
            n.group to cols.ifEmpty { listOf(Color.DarkGray) }
        }
    }
    val labelBasePx = with(density) { 12.sp.toPx() }
    val labels = remember(layout, measurer, nodeColors) {
        val base = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
        layout.nodes.associate { n ->
            val k = nodeColors[n.group]?.size ?: 1
            val st = when {
                k >= 3 -> base.copy(fontWeight = FontWeight.ExtraBold)
                k == 2 -> base.copy(fontWeight = FontWeight.Bold)
                else -> base
            }
            n.group to measurer.measure(AnnotatedString(n.labelKo), style = st)
        }
    }
    val countLabels = remember(measurer) {
        (4..12).associateWith {
            measurer.measure(AnnotatedString("$it"), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink))
        }
    }

    BoxWithConstraints(modifier.background(MapBackground).clipToBounds()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val dpPx = with(density) { 1.dp.toPx() }
        val pad = 56f * dpPx
        val unit = layout.unit.coerceAtLeast(1f)
        val fit = remember(bounds, wPx, hPx) {
            min((wPx - pad) / bounds.width.coerceAtLeast(1f), (hPx - pad) / bounds.height.coerceAtLeast(1f)).coerceAtLeast(0.01f)
        }
        val minScale = fit * 0.8f
        val maxScale = max(fit * 4f, 150f * dpPx / unit)
        // 처음에는 역명이 읽히는 배율(역 간격 ≈ 44dp)로 지도 중심을 보여 준다
        val initScale = (44f * dpPx / unit).coerceIn(fit, maxScale)
        var scale by remember(layout.regionId, fit) { mutableFloatStateOf(initScale) }
        var pan by remember(layout.regionId, fit) {
            mutableStateOf(Offset(wPx / 2 - bounds.center.x * initScale, hPx / 2 - bounds.center.y * initScale))
        }

        val gestures = Modifier
            .pointerInput(layout.regionId, fit) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(minScale, maxScale)
                    val z = ns / scale
                    pan = (pan - centroid) * z + centroid + panChange
                    scale = ns
                }
            }
            .pointerInput(layout.regionId, fit) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        val ns = (scale * 2f).coerceAtMost(maxScale)
                        val z = ns / scale
                        pan = (pan - p) * z + p
                        scale = ns
                    },
                    onTap = { p ->
                        val hit = max(26.dp.toPx(), unit * scale * 0.45f)
                        val best = layout.nodes.minByOrNull { n -> (Offset(n.x, n.y) * scale + pan - p).getDistance() }
                        if (best != null && (Offset(best.x, best.y) * scale + pan - p).getDistance() <= hit) {
                            tapCallback(best.group)
                        }
                    },
                )
            }

        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val unitPx = unit * scale
            val lw = max(1.2.dp.toPx(), unitPx * 0.075f)
            fun tr(x: Float, y: Float) = Offset(x * scale + pan.x, y * scale + pan.y)

            // 1) 노선
            layout.lines.forEach { ll ->
                val color = lineColors[ll.lineId] ?: Color.Gray
                ll.segments.forEach { seg ->
                    val pts = seg.points.mapNotNull { if (it.size >= 2) tr(it[0], it[1]) else null }
                    if (pts.size >= 2) {
                        drawPath(
                            polylinePath(offsetPolyline(pts, seg.offset * lw)),
                            color,
                            style = Stroke(width = lw, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }

            fun onScreen(c: Offset, m: Float) = c.x > -m && c.y > -m && c.x < size.width + m && c.y < size.height + m

            // 2) 역 공점 (노선 수 적은 역 → 많은 역 순서로 그려 환승역이 위에 오도록)
            layout.nodes.sortedBy { nodeColors[it.group]?.size ?: 1 }.forEach { n ->
                val c = tr(n.x, n.y)
                val colors = nodeColors[n.group] ?: listOf(Color.DarkGray)
                val radius = stationRadius(colors.size, lw)
                if (onScreen(c, radius * 3)) {
                    if (n.group == highlighted) drawCircle(Color(0x553B5BDB), radius * 2.2f, c)
                    val fill = when (n.group) {
                        fromGroup -> FromGreen
                        toGroup -> ToRed
                        else -> Color.White
                    }
                    drawStation(c, colors, lw, fill, if (colors.size >= 4) countLabels[min(colors.size, 12)] else null)
                }
            }

            // 3) 역명 — 글자 크기도 배율에 비례
            val spPx = 1.sp.toPx()
            val fontPx = unitPx * 0.25f
            layout.nodes.forEach { n ->
                val k = nodeColors[n.group]?.size ?: 1
                val selected = n.group == fromGroup || n.group == toGroup || n.group == highlighted
                val show = selected ||
                    fontPx >= 7f * spPx ||
                    (k >= 3 && fontPx >= 4.5f * spPx) ||
                    (k == 2 && fontPx >= 5.5f * spPx)
                val tl = labels[n.group]
                val c = tr(n.x, n.y)
                if (show && tl != null && onScreen(c, 400f)) {
                    val fp = if (selected) max(fontPx, 11f * spPx) else fontPx
                    val f = fp / labelBasePx
                    val w = tl.size.width * f
                    val h = tl.size.height * f
                    val gap = stationRadius(k, lw) + unitPx * 0.05f
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
                    val padX = fp * 0.18f
                    drawRoundRect(
                        Color(0xE6FFFFFF),
                        topLeft = Offset(x - padX, y),
                        size = Size(w + padX * 2, h),
                        cornerRadius = CornerRadius(fp * 0.3f),
                    )
                    withTransform({
                        translate(x, y)
                        scale(f, f, pivot = Offset.Zero)
                    }) { drawText(tl) }
                }
            }
        }
    }
}
