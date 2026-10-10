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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
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
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.domain.model.LayoutNode
import io.github.jpsubway.app.domain.model.MapLayout
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.seoul.TrainMarker
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.MapBackground
import io.github.jpsubway.app.ui.theme.ToRed
import io.github.jpsubway.app.ui.theme.parseColor
import kotlin.math.max
import kotlin.math.min

private val Ink = Color(0xFF212529)
private val InkSoft = Color(0xFF495057)
private val RiverBlue = Color(0xFFCFE6FA)

/** 역명 후보 방향: 레이아웃 생성기가 고른 방향을 먼저 시도하고, 겹치면 나머지 방향 */
private val LabelDirs = listOf(1 to 0, -1 to 0, 0 to -1, 0 to 1, 1 to -1, 1 to 1, -1 to -1, -1 to 1)

/** 역 공점 반지름: 노선 수가 많을수록 크게 (평행 노선 묶음을 덮도록) */
private fun stationRadius(lineCount: Int, lw: Float): Float =
    if (lineCount <= 1) lw * 0.95f else lw * (0.5f + 0.5f * min(lineCount, 4))

private fun darker(c: Color, f: Float = 0.55f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

private fun stroke(w: Float, cap: StrokeCap = StrokeCap.Round, effect: PathEffect? = null) =
    Stroke(width = w, cap = cap, join = StrokeJoin.Round, pathEffect = effect)

private fun Rect.hitsCircle(c: Offset, r: Float): Boolean {
    val nx = c.x.coerceIn(left, right)
    val ny = c.y.coerceIn(top, bottom)
    val dx = c.x - nx
    val dy = c.y - ny
    return dx * dx + dy * dy < r * r
}

/**
 * 철도회사 분류별 선 디자인
 *  metro(도쿄메트로·시영 지하철) = 실선 / toei = 실선 + 가운데 흰 줄 / jr = 실선 + 흰 점선
 *  private(사철) = 진한 테두리 실선 / monorail = 속이 빈 선 / tram = 가는 선
 */
private fun DrawScope.drawStyledPath(path: Path, color: Color, category: String, lw: Float, alpha: Float = 1f) {
    when (category) {
        "toei" -> {
            drawPath(path, color, alpha = alpha, style = stroke(lw))
            drawPath(path, Color.White, alpha = alpha, style = stroke(lw * 0.22f))
        }
        "jr" -> {
            drawPath(path, color, alpha = alpha, style = stroke(lw * 1.1f, StrokeCap.Butt))
            drawPath(
                path, Color.White, alpha = alpha,
                style = stroke(lw * 0.36f, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(lw * 2.2f, lw * 1.6f))),
            )
        }
        "private" -> {
            drawPath(path, darker(color), alpha = alpha, style = stroke(lw * 1.3f))
            drawPath(path, color, alpha = alpha, style = stroke(lw * 0.72f))
        }
        "monorail" -> {
            drawPath(path, color, alpha = alpha, style = stroke(lw * 0.9f))
            drawPath(path, Color.White, alpha = alpha, style = stroke(lw * 0.42f))
        }
        "tram" -> drawPath(path, color, alpha = alpha, style = stroke(lw * 0.6f))
        else -> drawPath(path, color, alpha = alpha, style = stroke(lw))
    }
}

/** 종점 노선 아이콘: JR = 둥근 사각형, 그 외 = 원 */
private fun DrawScope.drawLineIcon(c: Offset, color: Color, square: Boolean, side: Float, label: TextLayoutResult?) {
    val half = side / 2f
    val rim = side * 0.1f
    if (square) {
        drawRoundRect(
            Color.White,
            topLeft = Offset(c.x - half - rim, c.y - half - rim),
            size = Size(side + rim * 2, side + rim * 2),
            cornerRadius = CornerRadius(side * 0.22f),
        )
        drawRoundRect(color, topLeft = Offset(c.x - half, c.y - half), size = Size(side, side), cornerRadius = CornerRadius(side * 0.16f))
    } else {
        drawCircle(Color.White, half + rim, c)
        drawCircle(color, half, c)
    }
    if (label != null && label.size.width > 0 && label.size.height > 0) {
        val f = min(side * 0.58f / label.size.height, side * 0.82f / label.size.width)
        val w = label.size.width * f
        val h = label.size.height * f
        withTransform({
            translate(c.x - w / 2, c.y - h / 2)
            scale(f, f, pivot = Offset.Zero)
        }) { drawText(label, color = if (color.luminance() > 0.62f) Ink else Color.White) }
    }
}

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
 * 노선 굵기·역 공점·역명은 지도 좌표 기준 크기라 확대/축소에 비례하고, 역명 글자는 상한에서 멈춰
 * 확대할수록 더 많은 역명이 보인다. 역명은 매 프레임 겹침 검사(다른 역명·공점·아이콘)를 거쳐
 * 빈 방향에 놓고, 자리가 없으면 그 배율에서는 숨긴다 (환승 노선이 많은 역 우선).
 * 강(layout.rivers)은 노선 아래에 하늘색 띠로 그린다.
 * visibleFilters: 표시할 필터 그룹 (subway / jr / private)
 * focusLines: 지정하면 이 노선들만 그린다 (노선 단독 보기). 필터·다른 지역 노선은 무시하고,
 *   이 노선의 역만 보이되 환승역 공점에는 다른 환승 노선 색도 표시한다. 역명은 읽을 수 있는 최소 크기를 유지한다.
 * initialFocus: 처음 화면에 맞춰 보여 줄 영역 (지도 좌표). null 이면 지도 중심을 기본 배율로 보여 준다.
 * nameLanguage: 역명 언어 (한국어 / 일본어 / 영어)
 * trains: 실시간 열차 아이콘과 행선지 라벨 (서울 노선 전체 지도). 역명 위에 trainColor 로 그린다.
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
    visibleFilters: Set<String> = setOf("subway", "jr", "private"),
    focusLines: Set<String>? = null,
    initialFocus: Rect? = null,
    nameLanguage: AppLanguage = AppLanguage.KO,
    trains: List<Pair<TrainMarker, String>> = emptyList(),
    trainColor: Color = Color(0xFF3B5BDB),
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tapCallback by rememberUpdatedState(onStationTap)
    val bounds = remember(layout) { layoutBounds(layout) }
    val lineColors = remember(layout) { layout.lines.associate { it.lineId to parseColor(it.color) } }
    val focusMode = focusLines != null
    val visibleLines = remember(layout, visibleFilters, focusLines) {
        if (focusLines != null) {
            layout.lines.map { it.lineId }.filter { it in focusLines }.toSet()
        } else {
            layout.lines.filter { it.filter in visibleFilters }.map { it.lineId }.toSet()
        }
    }
    // 역(환승그룹)별 표시 중인 노선 색 — 같은 색(본선/지선)은 하나로 친다
    val nodeColors = remember(layout, network, visibleLines, focusLines) {
        layout.nodes.associate { n ->
            val ids = n.stationIds.mapNotNull { network.stationById[it]?.lineId }.distinct()
            val shown = when {
                focusLines == null -> ids.filter { it in visibleLines }
                // 노선 단독 보기: 이 노선의 역만 보이고, 환승역에는 다른 노선 색도 함께 표시
                ids.any { it in visibleLines } -> ids.sortedBy { if (it in visibleLines) 0 else 1 }
                else -> emptyList()
            }
            val cols = shown
                .mapNotNull { network.lineById[it]?.color }
                .map { parseColor(it) }
                .distinct()
            n.group to cols
        }
    }
    val visibleNodes = remember(layout, nodeColors) { layout.nodes.filter { nodeColors[it.group].orEmpty().isNotEmpty() } }
    val tapNodes by rememberUpdatedState(visibleNodes)
    val labelBasePx = with(density) { 12.sp.toPx() }
    val labels = remember(layout, network, measurer, nodeColors, nameLanguage) {
        val base = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
        layout.nodes.associate { n ->
            val k = nodeColors[n.group]?.size ?: 1
            val st = when {
                k >= 3 -> base.copy(fontWeight = FontWeight.ExtraBold)
                k == 2 -> base.copy(fontWeight = FontWeight.Bold)
                else -> base
            }
            val text = when (nameLanguage) {
                AppLanguage.JA -> n.labelJa.ifBlank { n.labelKo }
                AppLanguage.EN -> network.groupName(n.group).en.ifBlank { n.labelJa.ifBlank { n.labelKo } }
                else -> n.labelKo
            }
            n.group to measurer.measure(AnnotatedString(text), style = st)
        }
    }
    val countLabels = remember(measurer) {
        (4..12).associateWith {
            measurer.measure(AnnotatedString("$it"), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink))
        }
    }
    val iconLabels = remember(layout, measurer) {
        layout.lines.associate { l ->
            l.lineId to l.code.takeIf { it.isNotBlank() }?.let {
                measurer.measure(AnnotatedString(it), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }
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
        // 처음에는 역명이 읽히는 배율(역 간격 ≈ 44dp)로 지도 중심을 보여 준다.
        // initialFocus 가 있으면 그 영역이 화면에 꽉 차도록 맞춘다.
        val initScale = if (initialFocus != null) {
            min(
                (wPx - pad) / initialFocus.width.coerceAtLeast(unit),
                (hPx - pad) / initialFocus.height.coerceAtLeast(unit),
            ).coerceIn(minScale, maxScale)
        } else {
            (44f * dpPx / unit).coerceIn(fit, maxScale)
        }
        val initCenter = initialFocus?.center ?: bounds.center
        var scale by remember(layout.regionId, fit, initialFocus) { mutableFloatStateOf(initScale) }
        var pan by remember(layout.regionId, fit, initialFocus) {
            mutableStateOf(Offset(wPx / 2 - initCenter.x * initScale, hPx / 2 - initCenter.y * initScale))
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
                        val best = tapNodes.minByOrNull { n -> (Offset(n.x, n.y) * scale + pan - p).getDistance() }
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
            fun onScreen(c: Offset, m: Float) = c.x > -m && c.y > -m && c.x < size.width + m && c.y < size.height + m

            // -1) 강 (맨 아래)
            layout.rivers.forEach { rv ->
                val pts = rv.points.mapNotNull { if (it.size >= 2) tr(it[0], it[1]) else null }
                if (pts.size >= 2) {
                    drawPath(polylinePath(pts), RiverBlue, style = stroke(rv.width * scale))
                }
            }

            // 0) 다른 지역의 환승 노선 (반투명) — 노선 단독 보기에서는 그리지 않는다
            if (!focusMode) {
                layout.ghosts.forEach { g ->
                    val pts = g.points.mapNotNull { if (it.size >= 2) tr(it[0], it[1]) else null }
                    if (pts.size >= 2) {
                        drawStyledPath(polylinePath(offsetPolyline(pts, 0f)), parseColor(g.color), g.category, lw * 0.9f, alpha = 0.35f)
                    }
                }
            }

            // 1) 노선 (회사별 디자인)
            layout.lines.forEach { ll ->
                if (ll.lineId !in visibleLines) return@forEach
                val color = lineColors[ll.lineId] ?: Color.Gray
                ll.segments.forEach { seg ->
                    val pts = seg.points.mapNotNull { if (it.size >= 2) tr(it[0], it[1]) else null }
                    if (pts.size >= 2) {
                        drawStyledPath(polylinePath(offsetPolyline(pts, seg.offset * lw)), color, ll.category, lw)
                    }
                }
            }

            // 2) 종점 노선 아이콘
            val iconSide = lw * 3.2f
            val iconRects = ArrayList<Rect>()
            layout.lines.forEach { ll ->
                if (ll.lineId !in visibleLines) return@forEach
                val color = lineColors[ll.lineId] ?: Color.Gray
                ll.terminals.forEach { t ->
                    if (t.size >= 2) {
                        val c = tr(t[0], t[1])
                        if (onScreen(c, iconSide)) {
                            drawLineIcon(c, color, ll.category == "jr", iconSide, iconLabels[ll.lineId])
                            iconRects.add(Rect(c.x - iconSide / 2, c.y - iconSide / 2, c.x + iconSide / 2, c.y + iconSide / 2))
                        }
                    }
                }
            }

            // 3) 역 공점 (노선 수 적은 역 → 많은 역 순서로 그려 환승역이 위에 오도록)
            val circles = ArrayList<Pair<Offset, Float>>()
            visibleNodes.sortedBy { nodeColors[it.group]?.size ?: 1 }.forEach { n ->
                val c = tr(n.x, n.y)
                val colors = nodeColors[n.group].orEmpty()
                val radius = stationRadius(colors.size, lw)
                if (onScreen(c, radius * 3)) {
                    circles.add(c to radius)
                    if (n.group == highlighted) drawCircle(Color(0x553B5BDB), radius * 2.2f, c)
                    val fill = when (n.group) {
                        fromGroup -> FromGreen
                        toGroup -> ToRed
                        else -> Color.White
                    }
                    drawStation(c, colors, lw, fill, if (colors.size >= 4) countLabels[min(colors.size, 12)] else null)
                }
            }

            // 4) 역명 — 겹치지 않는 방향에만 배치
            val spPx = 1.sp.toPx()
            val fontPx = min(unitPx * 0.25f, 14f * spPx)
            val placed = ArrayList<Rect>()
            val order = visibleNodes.sortedWith(
                compareByDescending<LayoutNode> { it.group == fromGroup || it.group == toGroup || it.group == highlighted }
                    .thenByDescending { nodeColors[it.group]?.size ?: 1 },
            )
            order.forEach { n ->
                val k = nodeColors[n.group]?.size ?: 1
                val selected = n.group == fromGroup || n.group == toGroup || n.group == highlighted
                val show = selected || focusMode ||
                    fontPx >= 7f * spPx ||
                    (k >= 3 && fontPx >= 4.5f * spPx) ||
                    (k == 2 && fontPx >= 5.5f * spPx)
                val tl = labels[n.group]
                val c = tr(n.x, n.y)
                if (!show || tl == null || !onScreen(c, 300f)) return@forEach
                // 노선 단독 보기는 역 수가 적으니 축소 상태에서도 읽을 수 있는 글자 크기를 유지 (겹치면 숨김)
                val fp = when {
                    selected -> max(fontPx, 11f * spPx)
                    focusMode -> max(fontPx, 9f * spPx)
                    else -> fontPx
                }
                val f = fp / labelBasePx
                val w = tl.size.width * f
                val h = tl.size.height * f
                val padX = fp * 0.18f
                val gap = stationRadius(k, lw) + unitPx * 0.05f
                fun rectFor(dx: Int, dy: Int): Rect {
                    val x = when {
                        dx > 0 -> c.x + gap
                        dx < 0 -> c.x - gap - w
                        else -> c.x - w / 2
                    }
                    val y = when {
                        dy > 0 -> c.y + gap
                        dy < 0 -> c.y - gap - h
                        else -> c.y - h / 2
                    }
                    return Rect(x - padX, y, x + w + padX, y + h)
                }
                var chosen: Rect? = null
                for ((dx, dy) in listOf(n.labelDx to n.labelDy) + LabelDirs) {
                    val r = rectFor(dx, dy)
                    val hit = placed.any { it.overlaps(r) } ||
                        iconRects.any { it.overlaps(r) } ||
                        circles.any { (cc, rr) -> cc != c && r.hitsCircle(cc, rr) }
                    if (!hit) {
                        chosen = r
                        break
                    }
                }
                if (chosen == null && selected) chosen = rectFor(n.labelDx, n.labelDy)
                val r = chosen ?: return@forEach
                placed.add(r)
                drawRoundRect(Color(0xE6FFFFFF), topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(fp * 0.3f))
                withTransform({
                    translate(r.left + padX, r.top)
                    scale(f, f, pivot = Offset.Zero)
                }) { drawText(tl) }
            }

            // 5) 실시간 열차 (서울 노선 전체 지도) — 역명 위에 그린다
            if (trains.isNotEmpty()) {
                drawTrains(trains, trainColor, measurer, lw) { x, y -> tr(x, y) }
            }
        }
    }
}
