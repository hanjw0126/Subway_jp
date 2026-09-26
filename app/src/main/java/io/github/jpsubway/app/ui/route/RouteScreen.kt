package io.github.jpsubway.app.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.domain.routing.Journey
import io.github.jpsubway.app.domain.routing.RideLeg
import io.github.jpsubway.app.domain.routing.WalkLeg
import io.github.jpsubway.app.ui.common.LineBadge
import io.github.jpsubway.app.ui.common.minutesText
import io.github.jpsubway.app.ui.common.trainTypeKo
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.ToRed
import io.github.jpsubway.app.ui.theme.parseColor

private val OFFSETS = listOf(0 to "지금 출발", 10 to "10분 후", 30 to "30분 후", 60 to "1시간 후")

/** 경로 검색: 출발/도착 → 출발 시각 → [최단시간 | 최소환승] 탭 → 경로 카드 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    onBack: () -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    vm: RouteViewModel = viewModel(factory = RouteViewModel.Factory),
) {
    val u by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val sorted = remember(u.journeys, tab) {
        if (tab == 0) u.journeys.sortedWith(compareBy<Journey>({ it.arriveSec }, { it.transfers }))
        else u.journeys.sortedWith(compareBy<Journey>({ it.transfers }, { it.arriveSec }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("경로 검색") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StationField("출발", u.fromName, FromGreen, onPickFrom)
                        StationField("도착", u.toName, ToRed, onPickTo)
                    }
                    TextButton(onClick = vm::swap) { Text("⇅", fontSize = 22.sp) }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OFFSETS.forEach { (m, label) ->
                    FilterChip(selected = u.offsetMin == m, onClick = { vm.setOffset(m) }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            if (u.journeys.isNotEmpty()) {
                TabRow(selectedTabIndex = tab, Modifier.padding(top = 8.dp)) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("최단시간") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("최소환승") })
                }
            }
            val net = u.network
            when {
                u.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                u.message != null || net == null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(u.message ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(sorted) { i, j -> JourneyCard(j, net, expandedInitially = i == 0) }
                    item {
                        Text(
                            (if (u.isDemo) "데모 시간표 기준 · " else "시간표 기준 · ") + "출발 ${ServiceClock.format(u.departSec)} 이후 열차, 환승 최소 1분",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationField(label: String, name: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(name.ifBlank { "역을 선택하세요" }, color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun JourneyCard(j: Journey, net: Network, expandedInitially: Boolean) {
    var expanded by remember(j) { mutableStateOf(expandedInitially) }
    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(minutesText(j.durationSec), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${ServiceClock.format(j.departSec)} → ${ServiceClock.format(j.arriveSec)} · 환승 ${j.transfers}회",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LegBar(j, net)
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                j.legs.forEach { leg ->
                    when (leg) {
                        is RideLeg -> RideRow(leg, net)
                        is WalkLeg -> Text(
                            "🚶 환승 도보 약 ${minutesText(leg.durationSec)}",
                            Modifier.padding(start = 34.dp, top = 4.dp, bottom = 4.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideRow(leg: RideLeg, net: Network) {
    val line = net.lineById[leg.trip.lineId]
    Row(Modifier.padding(vertical = 4.dp)) {
        if (line != null) LineBadge(line, 24.dp) else Spacer(Modifier.width(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("${net.stationName(leg.fromStationId)} 승차  ${ServiceClock.format(leg.depSec)}", fontWeight = FontWeight.SemiBold)
            val type = trainTypeKo(leg.trip.trainType)
            Text(
                (if (type.isNotBlank()) "[$type] " else "") + "${net.stationName(leg.trip.destinationId)}행 · ${leg.stopCount}개 역 · ${minutesText(leg.arrSec - leg.depSec)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${net.stationName(leg.toStationId)} 하차  ${ServiceClock.format(leg.arrSec)}")
        }
    }
}

/** 소요시간 비율 막대: 노선색 = 탑승, 회색 = 도보, 옅은 회색 = 대기 */
@Composable
private fun LegBar(j: Journey, net: Network) {
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        var t = j.departSec
        j.legs.forEach { leg ->
            when (leg) {
                is RideLeg -> {
                    val wait = leg.depSec - t
                    if (wait > 0) Box(Modifier.weight(wait.toFloat()).fillMaxHeight().background(Color(0xFFE9ECEF)))
                    val color = parseColor(net.lineById[leg.trip.lineId]?.color ?: "")
                    Box(Modifier.weight((leg.arrSec - leg.depSec).coerceAtLeast(30).toFloat()).fillMaxHeight().background(color))
                    t = leg.arrSec
                }
                is WalkLeg -> {
                    Box(Modifier.weight(leg.durationSec.coerceAtLeast(30).toFloat()).fillMaxHeight().background(Color(0xFFADB5BD)))
                    t += leg.durationSec
                }
            }
        }
    }
}
