package io.github.jpsubway.app.ui.station

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.KoreaStrings
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.domain.seoul.LiveArrival
import io.github.jpsubway.app.domain.seoul.LiveBoard
import io.github.jpsubway.app.domain.seoul.SeoulLive
import io.github.jpsubway.app.ui.theme.DelayOrange
import io.github.jpsubway.app.ui.theme.ToRed
import androidx.compose.runtime.collectAsState

/** 한국어 역명(API 표기) → 현재 역명 언어 표기 */
@Composable
internal fun rememberKoNameLookup(net: Network): (String) -> String {
    val byKo = remember(net) {
        net.stations.associateBy({ SeoulLive.apiStationName(it.name.ko) }, { it.name })
    }
    return { ko -> byKo[SeoulLive.apiStationName(ko)]?.display() ?: ko }
}

/** 서울 실시간 도착정보 방면 카드 */
@Composable
internal fun LiveDirectionCard(b: LiveBoard, color: Color, nowEpochSec: Long, s: Strings, nameOf: (String) -> String) {
    val ui by Lang.ui.collectAsState()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                val title = if (b.heading.isNotBlank()) s.bound(nameOf(b.heading)) else KoreaStrings.updn(ui, b.updn)
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (b.updn.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(KoreaStrings.updn(ui, b.updn), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (b.arrivals.isEmpty()) {
                Text(s.noUpcoming, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                b.arrivals.forEachIndexed { i, a ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    LiveArrivalRow(a, first = i == 0, nowEpochSec = nowEpochSec, s = s, ui = ui, nameOf = nameOf)
                }
            }
        }
    }
}

@Composable
private fun LiveArrivalRow(a: LiveArrival, first: Boolean, nowEpochSec: Long, s: Strings, ui: AppLanguage, nameOf: (String) -> String) {
    val remain = a.etaEpochSec?.let { it - nowEpochSec }
    val big = when {
        a.code == "1" -> s.arrived
        a.code == "0" -> s.arrivingSoon
        remain != null && remain > 30 -> s.minutesLater(((remain + 59) / 60).toInt())
        remain != null -> s.arrivingSoon
        a.code == "3" || a.code == "4" || a.code == "5" -> s.arrivingSoon
        else -> a.stopsAway?.let { s.phaseStopsAway(it) } ?: s.phaseEnRoute
    }
    val phase = when (a.code) {
        "0" -> s.phaseApproaching
        "1" -> s.phaseArrived
        "3" -> s.phaseLeftPrevious
        "4" -> s.phaseApproaching
        "5" -> s.phaseAtPrevious
        else -> a.stopsAway?.let { s.phaseStopsAway(it) } ?: s.phaseEnRoute
    }
    // 한국어 화면은 API 문구 원문(예: "3분 후 (서울)"), 다른 언어는 상태를 번역해 보여 준다
    val sub = if (ui == AppLanguage.KO && a.messageKo.isNotBlank()) a.messageKo else phase
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val type = if (a.trainType.isNotBlank()) "[${KoreaStrings.trainType(ui, a.trainType)}] " else ""
            Text(
                type + s.destination(nameOf(a.destination)),
                fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.last) {
                    Spacer(Modifier.width(6.dp))
                    Text(KoreaStrings.lastTrain(ui), fontSize = 12.sp, color = DelayOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            big,
            fontSize = if (first) 22.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (a.code == "0" || a.code == "1" || (remain != null && remain <= 90)) ToRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}
