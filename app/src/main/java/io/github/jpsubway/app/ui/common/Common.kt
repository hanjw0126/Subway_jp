package io.github.jpsubway.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.jpsubway.app.JpSubwayApp
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.core.i18n.TrainType
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.domain.model.Line
import io.github.jpsubway.app.domain.model.LineStatus
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.ui.theme.DelayOrange
import io.github.jpsubway.app.ui.theme.parseColor

fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as JpSubwayApp).container

/** 현재 노선도 화면 언어의 문구. 언어를 바꾸면 이 값을 읽은 화면이 바로 다시 그려진다 */
@Composable
fun strings(): Strings {
    val lang by Lang.ui.collectAsState()
    return Strings.of(lang)
}

/** 현재 역명 언어. 역명을 그리는 화면이 언어 변경에 맞춰 다시 그려지도록 읽는다 */
@Composable
fun nameLanguage(): AppLanguage {
    val lang by Lang.names.collectAsState()
    return lang
}

/** 노선도 화면 언어 선택 (화면 문구 / 역명 각각). country 를 주지 않으면 현재 국가 (일본: 일/영/한, 한국: 한/영) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLanguageOptions(country: String? = null) {
    val settings = (LocalContext.current.applicationContext as JpSubwayApp).container.settings
    val cur by settings.mapLanguages.collectAsStateWithLifecycle()
    val current by settings.country.collectAsStateWithLifecycle()
    val target = country ?: current
    val s = strings()
    val opts = Lang.mapOptions(target)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.uiLanguage, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { l ->
                FilterChip(selected = cur.ui == l, onClick = { settings.setMapLanguages(l, cur.names, target) }, label = { Text(l.label) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(s.nameLanguage, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { l ->
                FilterChip(selected = cur.names == l, onClick = { settings.setMapLanguages(cur.ui, l, target) }, label = { Text(l.label) })
            }
        }
    }
}

@Composable
fun MapLanguageDialog(onDismiss: () -> Unit) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
        title = { Text(s.language) },
        text = { MapLanguageOptions() },
    )
}

/** 노선 기호 원형 배지 */
@Composable
fun LineBadge(line: Line, size: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(parseColor(line.color)),
        contentAlignment = Alignment.Center,
    ) {
        val code = line.code.ifBlank { line.name.display().take(1) }
        Text(
            code,
            color = Color.White,
            fontSize = (size.value * (if (code.length >= 2) 0.36f else 0.5f)).sp,
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
    val ui by Lang.ui.collectAsState()
    Surface(modifier.fillMaxWidth(), color = DelayOrange.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            statuses.forEach { st ->
                val name = network.lineById[st.lineId]?.name?.display() ?: st.lineId.substringAfterLast('.')
                val text = if (ui == AppLanguage.KO) st.textKo.ifBlank { st.textJa } else st.textJa.ifBlank { st.textKo }
                Text("⚠ $name · $text", color = DelayOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

/**
 * 열차 종별. ODPT 종별 ID(odpt.TrainType:...Express)와 시간표에 저장된 한국어 종별명(급행 등)을 모두 받는다.
 * 보통열차는 null (표시하지 않음).
 */
fun trainTypeOf(raw: String): TrainType? {
    when (raw.trim()) {
        "특급" -> return TrainType.LIMITED_EXPRESS
        "준급" -> return TrainType.SEMI_EXPRESS
        "통근급행" -> return TrainType.COMMUTER_EXPRESS
        "쾌속급행" -> return TrainType.RAPID_EXPRESS
        "급행" -> return TrainType.EXPRESS
        "쾌속" -> return TrainType.RAPID
    }
    val t = raw.substringAfterLast(':').substringAfterLast('.')
    return when {
        t.isBlank() || t.equals("Local", ignoreCase = true) -> null
        t.contains("LimitedExpress", ignoreCase = true) -> TrainType.LIMITED_EXPRESS
        t.contains("SemiExpress", ignoreCase = true) -> TrainType.SEMI_EXPRESS
        t.contains("CommuterExpress", ignoreCase = true) -> TrainType.COMMUTER_EXPRESS
        t.contains("RapidExpress", ignoreCase = true) -> TrainType.RAPID_EXPRESS
        t.contains("Express", ignoreCase = true) -> TrainType.EXPRESS
        t.contains("Rapid", ignoreCase = true) -> TrainType.RAPID
        else -> null
    }
}

/** 현재 화면 언어로 된 열차 종별명 (보통열차는 "") */
fun trainTypeLabel(raw: String): String = trainTypeOf(raw)?.let { Strings.current.trainType(it) }.orEmpty()

/** ODPT 열차종별 ID → 한국어 (보통열차는 표시하지 않음) */
fun trainTypeKo(id: String): String = trainTypeOf(id)?.let { Strings.of(AppLanguage.KO).trainType(it) }.orEmpty()

fun minutesText(sec: Int): String = Strings.current.minutes((sec.coerceAtLeast(0) + 59) / 60)
