package io.github.jpsubway.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.jpsubway.app.JpSubwayApp
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineStatus
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.ui.theme.DelayOrange
import io.github.jpsubway.app.ui.theme.parseColor

fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as JpSubwayApp).container

/** 노선 기호 원형 배지 (카카오지하철의 호선 배지와 같은 역할) */
@Composable
fun LineBadge(line: Line, size: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(parseColor(line.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            line.code.ifBlank { line.name.display().take(1) },
            color = Color.White,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun LinePill(line: Line, selected: Boolean = true) {
    val c = parseColor(line.color)
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) c else c.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(line.name.display(), color = if (selected) Color.White else c, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** 지연·운행 중단 안내 배너 */
@Composable
fun StatusBanner(statuses: List<LineStatus>, network: Network, modifier: Modifier = Modifier) {
    if (statuses.isEmpty()) return
    Surface(modifier.fillMaxWidth(), color = DelayOrange.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            statuses.forEach { st ->
                val name = network.lineById[st.lineId]?.name?.display() ?: st.lineId.substringAfterLast('.')
                Text("⚠ $name · ${st.textKo.ifBlank { st.textJa }}", color = DelayOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ODPT 열차종별 ID → 한국어 (보통열차는 표시하지 않음) */
fun trainTypeKo(id: String): String {
    val t = id.substringAfterLast(':').substringAfterLast('.')
    return when {
        t.isBlank() || t.equals("Local", ignoreCase = true) -> ""
        t.contains("LimitedExpress", ignoreCase = true) -> "특급"
        t.contains("SemiExpress", ignoreCase = true) -> "준급"
        t.contains("CommuterExpress", ignoreCase = true) -> "통근급행"
        t.contains("RapidExpress", ignoreCase = true) -> "쾌속급행"
        t.contains("Express", ignoreCase = true) -> "급행"
        t.contains("Rapid", ignoreCase = true) -> "쾌속"
        else -> ""
    }
}

fun minutesText(sec: Int): String = "${(sec.coerceAtLeast(0) + 59) / 60}분"
