package io.github.jpsubway.app.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jpsubway.app.domain.seoul.TrainMarker
import kotlin.math.atan2
import kotlin.math.max

private val TrainInk = Color(0xFF212529)

/**
 * 실시간 열차 아이콘: 진행 방향으로 회전한 작은 열차(노선색 차체 + 창문 + 앞쪽 삼각형)와 위쪽 행선지 라벨.
 * 같은 구간에서 엇갈리는 열차가 겹치지 않도록 진행 방향 오른쪽으로 살짝 비켜 그린다.
 * tr: 월드 좌표 → 화면 좌표
 */
internal fun DrawScope.drawTrains(
    trains: List<Pair<TrainMarker, String>>,
    color: Color,
    measurer: TextMeasurer,
    lw: Float,
    tr: (Float, Float) -> Offset,
) {
    val body = max(lw * 3.4f, 15.dp.toPx())
    val h = body * 0.56f
    val rim = 1.5.dp.toPx()
    val side = lw * 1.5f
    val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TrainInk)
    trains.forEach { (t, label) ->
        val hasDir = t.dirX != 0f || t.dirY != 0f
        // 진행 방향 오른쪽(화면 좌표계: (-dirY, dirX))으로 비켜 상·하행을 나눈다
        val c = tr(t.x, t.y) + if (hasDir) Offset(-t.dirY, t.dirX) * side else Offset.Zero
        if (c.x < -body * 3 || c.y < -body * 3 || c.x > size.width + body * 3 || c.y > size.height + body * 3) return@forEach
        val angle = if (hasDir) Math.toDegrees(atan2(t.dirY, t.dirX).toDouble()).toFloat() else 0f
        withTransform({ rotate(angle, pivot = c) }) {
            drawRoundRect(
                Color.White,
                topLeft = Offset(c.x - body / 2 - rim, c.y - h / 2 - rim),
                size = Size(body + rim * 2, h + rim * 2),
                cornerRadius = CornerRadius(h * 0.45f),
            )
            drawRoundRect(color, topLeft = Offset(c.x - body / 2, c.y - h / 2), size = Size(body, h), cornerRadius = CornerRadius(h * 0.4f))
            // 창문 3개
            val ww = body * 0.15f
            val wh = h * 0.38f
            for (k in 0..2) {
                drawRect(Color.White, topLeft = Offset(c.x - body * 0.33f + k * body * 0.22f, c.y - wh / 2), size = Size(ww, wh))
            }
            // 앞쪽(진행 방향) 삼각형
            if (hasDir) {
                val nose = Path().apply {
                    moveTo(c.x + body / 2 + rim * 0.5f, c.y - h * 0.38f)
                    lineTo(c.x + body / 2 + h * 0.6f, c.y)
                    lineTo(c.x + body / 2 + rim * 0.5f, c.y + h * 0.38f)
                    close()
                }
                drawPath(nose, color)
            }
        }
        // 행선지 라벨 (회전하지 않음)
        if (label.isNotBlank()) {
            val tl = measurer.measure(AnnotatedString(label), labelStyle)
            val padX = 3.dp.toPx()
            val lx = c.x - tl.size.width / 2f
            val ly = c.y - h / 2 - rim - 2.dp.toPx() - tl.size.height
            drawRoundRect(
                Color(0xF2FFFFFF),
                topLeft = Offset(lx - padX, ly),
                size = Size(tl.size.width + padX * 2, tl.size.height.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(tl, topLeft = Offset(lx, ly))
        }
    }
}
