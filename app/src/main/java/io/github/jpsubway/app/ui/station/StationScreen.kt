package io.github.jpsubway.app.ui.station

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.domain.arrival.delayLabel
import io.github.jpsubway.app.domain.arrival.minutesLabel
import io.github.jpsubway.app.domain.arrival.phaseLabel
import io.github.jpsubway.app.domain.model.Arrival
import io.github.jpsubway.app.domain.model.DirectionBoard
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.ui.common.LinePill
import io.github.jpsubway.app.ui.common.StatusBanner
import io.github.jpsubway.app.ui.common.trainTypeKo
import io.github.jpsubway.app.ui.theme.DelayOrange
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.ToRed
import io.github.jpsubway.app.ui.theme.parseColor

/** 역 도착정보 화면: 노선 탭(환승역) 또는 노선 이름(단일 노선 역) → 이전역·현재역·다음역 띠 → 방면별 "N분 후" 카드 2개 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    group: String,
    onBack: () -> Unit,
    onRoute: () -> Unit,
    vm: StationViewModel = viewModel(key = "station-$group", factory = StationViewModel.factory(group)),
) {
    val u by vm.ui.collectAsStateWithLifecycle()
    val lineColor = u.line?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(u.name.display(), fontWeight = FontWeight.Bold)
                        if (u.name.ja.isNotBlank()) Text(u.name.ja, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (vm.setFrom()) onRoute() else onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FromGreen),
                    ) { Text("출발") }
                    Button(
                        onClick = { if (vm.setTo()) onRoute() else onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ToRed),
                    ) { Text("도착") }
                }
            }
        },
    ) { padding ->
        val net = u.network
        when {
            u.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            u.error != null || net == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(u.error ?: "데이터 없음")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (u.lines.size > 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            u.lines.forEach { l ->
                                Box(Modifier.clip(RoundedCornerShape(50)).clickable { vm.selectLine(l.id) }) {
                                    LinePill(l, selected = l.id == u.line?.id)
                                }
                            }
                        }
                    }
                } else {
                    // 환승역이 아닌 역: 선택할 탭은 없지만 노선 이름은 보여 준다
                    val only = u.lines.firstOrNull()
                    if (only != null) {
                        item { Row { LinePill(only) } }
                    }
                }
                item { StationStrip(u, net, lineColor) }
                val st = u.status
                if (st != null && !st.isNormal) item { StatusBanner(listOf(st), net) }
                items(u.boards) { b -> DirectionCard(b, net, lineColor, u.nowSec) }
                item {
                    val extra = u.realtimeError?.let { " · 실시간 정보 오류: $it" }.orEmpty()
                    Text(u.sourceLabel + extra, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "이전역 ← 현재역 → 다음역" 띠 */
@Composable
private fun StationStrip(u: StationViewModel.Ui, net: Network, color: Color) {
    val left = u.boards.getOrNull(1)?.prevStationId      // asc 방향 열차가 오는 쪽
    val right = u.boards.getOrNull(1)?.nextStationId
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(left?.let { "‹ " + net.stationName(it) }.orEmpty(), Modifier.weight(1f).padding(top = 32.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(shape = RoundedCornerShape(50), color = Color.White, border = androidx.compose.foundation.BorderStroke(3.dp, color)) {
                Text(
                    (u.station?.code?.let { if (it.isNotBlank()) "$it " else "" } ?: "") + u.name.display(),
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212529),
                )
            }
            Text(
                right?.let { net.stationName(it) + " ›" }.orEmpty(),
                Modifier.weight(1f).padding(top = 32.dp),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun DirectionCard(b: DirectionBoard, net: Network, color: Color, nowSec: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                val dirName = b.direction.name.display().ifBlank { b.nextStationId?.let { net.stationName(it) } ?: "" }
                Text("$dirName 방면", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                b.nextStationId?.let {
                    Text("다음 역 ${net.stationName(it)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (b.arrivals.isEmpty()) {
                val ended = b.lastDepSec != null && nowSec > b.lastDepSec
                Text(if (ended) "오늘 운행이 종료되었습니다" else "도착 예정 열차가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                b.arrivals.forEachIndexed { i, a ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    ArrivalRow(a, net, first = i == 0)
                }
            }
            if (b.firstDepSec != null || b.lastDepSec != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    listOfNotNull(
                        b.firstDepSec?.let { "첫차 ${ServiceClock.format(it)}" },
                        b.lastDepSec?.let { "막차 ${ServiceClock.format(it)}" },
                    ).joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArrivalRow(a: Arrival, net: Network, first: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val type = trainTypeKo(a.trip.trainType)
            Text(
                (if (type.isNotBlank()) "[$type] " else "") + net.stationName(a.trip.destinationId) + "행",
                fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${a.phaseLabel()} · 예정 ${ServiceClock.format(a.scheduledSec)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.delayLabel()?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(it, fontSize = 12.sp, color = DelayOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            a.minutesLabel(),
            fontSize = if (first) 22.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (a.minutes <= 1) ToRed else MaterialTheme.colorScheme.onSurface,
        )
    }
}
