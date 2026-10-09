package io.github.jpsubway.app.ui.station

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Strings
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
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings
import io.github.jpsubway.app.ui.common.trainTypeLabel
import io.github.jpsubway.app.ui.theme.DelayOrange
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.ToRed
import io.github.jpsubway.app.ui.theme.parseColor

/**
 * 역 도착정보 화면: 노선 탭(환승역) 또는 노선 이름(단일 노선 역) → 이전역·현재역·다음역 띠 → 방면별 "N분 후" 카드 2개.
 *  - 역명(상단 제목 또는 역 띠 가운데)을 누르면 선택한 노선의 전체 지도
 *  - 좌우로 밀거나 역 띠 양옆 역명을 누르면 같은 노선의 이웃 역으로 이동
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    group: String,
    onBack: () -> Unit,
    onRoute: () -> Unit,
    onOpenLineMap: (lineId: String, group: String) -> Unit,
    initialLine: String? = null,
    vm: StationViewModel = viewModel(
        key = "station-$group-${initialLine.orEmpty()}",
        factory = StationViewModel.factory(group, initialLine),
    ),
) {
    val u by vm.ui.collectAsStateWithLifecycle()
    val s = strings()
    val names = nameLanguage()
    // 다음 화면 전환 방향: 1 = 오른쪽 역으로, -1 = 왼쪽 역으로, 0 = 제자리(노선 변경 등)
    var slide by remember { mutableIntStateOf(0) }
    val openLineMap: () -> Unit = {
        val cur = vm.ui.value
        cur.line?.let { onOpenLineMap(it.id, cur.group) }
    }
    val move: (String, Int) -> Unit = { stationId, dir ->
        slide = dir
        vm.moveTo(stationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(enabled = u.line != null, onClick = openLineMap)) {
                        Text(u.name.display(), fontWeight = FontWeight.Bold)
                        // 보조 표기: 역명 언어가 일본어면 한국어, 아니면 일본어
                        val sub = if (names == AppLanguage.JA) u.name.ko else u.name.ja
                        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) }
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
                    ) { Text(s.from) }
                    Button(
                        onClick = { if (vm.setTo()) onRoute() else onBack() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ToRed),
                    ) { Text(s.to) }
                }
            }
        },
    ) { padding ->
        when {
            u.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            u.error != null || u.network == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(u.error ?: s.noData)
            }
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        val threshold = 72.dp.toPx()
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val cur = vm.ui.value
                                val toRight = total <= -threshold // 손가락을 왼쪽으로 밀면 오른쪽(다음) 역
                                val toLeft = total >= threshold
                                val target = when {
                                    toRight -> cur.rightStationId
                                    toLeft -> cur.leftStationId
                                    else -> null
                                }
                                if (target != null) move(target, if (toRight) 1 else -1)
                            },
                            onHorizontalDrag = { change, amount ->
                                total += amount
                                change.consume()
                            },
                        )
                    },
            ) {
                AnimatedContent(
                    targetState = u,
                    contentKey = { it.station?.id ?: it.group },
                    transitionSpec = {
                        val d = slide
                        if (d == 0) {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        } else {
                            slideInHorizontally(tween(220)) { w -> w * d } togetherWith
                                slideOutHorizontally(tween(220)) { w -> -w * d }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "station",
                ) { st ->
                    val net = st.network
                    if (net != null) {
                        StationBody(
                            u = st,
                            net = net,
                            s = s,
                            onSelectLine = { id ->
                                slide = 0
                                vm.selectLine(id)
                            },
                            onMove = move,
                            onOpenLineMap = openLineMap,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationBody(
    u: StationViewModel.Ui,
    net: Network,
    s: Strings,
    onSelectLine: (String) -> Unit,
    onMove: (stationId: String, dir: Int) -> Unit,
    onOpenLineMap: () -> Unit,
) {
    val lineColor = u.line?.let { parseColor(it.color) } ?: MaterialTheme.colorScheme.primary
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (u.lines.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    u.lines.forEach { l ->
                        Box(Modifier.clip(RoundedCornerShape(50)).clickable { onSelectLine(l.id) }) {
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
        item { StationStrip(u, net, lineColor, onMove, onOpenLineMap) }
        item {
            Text(
                s.stationHint,
                Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                color = hintColor,
                textAlign = TextAlign.Center,
            )
        }
        val st = u.status
        if (st != null && !st.isNormal) item { StatusBanner(listOf(st), net) }
        items(u.boards) { b -> DirectionCard(b, net, lineColor, u.nowSec, s) }
        item {
            val extra = u.realtimeError?.let { " · ${s.realtimeError}: $it" }.orEmpty()
            Text(u.sourceLabel + extra, fontSize = 12.sp, color = hintColor)
        }
    }
}

/** "이전역 ← 현재역 → 다음역" 띠. 양옆 역명 = 이웃 역으로 이동, 가운데 역명 = 노선 전체 지도 */
@Composable
private fun StationStrip(
    u: StationViewModel.Ui,
    net: Network,
    color: Color,
    onMove: (stationId: String, dir: Int) -> Unit,
    onOpenLineMap: () -> Unit,
) {
    val left = u.leftStationId
    val right = u.rightStationId
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                left?.let { "‹ " + net.stationName(it) }.orEmpty(),
                Modifier
                    .weight(1f)
                    .clickable(enabled = left != null) { if (left != null) onMove(left, -1) }
                    .padding(top = 32.dp),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenLineMap),
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(3.dp, color),
            ) {
                Text(
                    (u.station?.code?.let { if (it.isNotBlank()) "$it " else "" } ?: "") + u.name.display(),
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212529),
                )
            }
            Text(
                right?.let { net.stationName(it) + " ›" }.orEmpty(),
                Modifier
                    .weight(1f)
                    .clickable(enabled = right != null) { if (right != null) onMove(right, 1) }
                    .padding(top = 32.dp),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun DirectionCard(b: DirectionBoard, net: Network, color: Color, nowSec: Int, s: Strings) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                val dirName = b.direction.name.display().ifBlank { b.nextStationId?.let { net.stationName(it) } ?: "" }
                Text(s.bound(dirName), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                b.nextStationId?.let {
                    Text(s.nextStation(net.stationName(it)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (b.arrivals.isEmpty()) {
                val ended = b.lastDepSec != null && nowSec > b.lastDepSec
                Text(if (ended) s.serviceEnded else s.noUpcoming, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                b.arrivals.forEachIndexed { i, a ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    ArrivalRow(a, net, first = i == 0, s = s)
                }
            }
            if (b.firstDepSec != null || b.lastDepSec != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    listOfNotNull(
                        b.firstDepSec?.let { s.firstTrain(ServiceClock.format(it)) },
                        b.lastDepSec?.let { s.lastTrain(ServiceClock.format(it)) },
                    ).joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArrivalRow(a: Arrival, net: Network, first: Boolean, s: Strings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val type = trainTypeLabel(a.trip.trainType)
            Text(
                (if (type.isNotBlank()) "[$type] " else "") + s.destination(net.stationName(a.trip.destinationId)),
                fontWeight = if (first) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${a.phaseLabel()} · ${s.scheduled(ServiceClock.format(a.scheduledSec))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
