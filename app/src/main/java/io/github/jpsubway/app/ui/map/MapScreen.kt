package io.github.jpsubway.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.inLanguage
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.LineBadge
import io.github.jpsubway.app.ui.common.MapLanguageDialog
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.ToRed

/** 노선 필터 그룹 (layout.json 의 lines[].filter) */
private val FilterOrder = listOf("subway", "jr", "private")

private fun filterLabel(s: Strings, f: String) = when (f) {
    "subway" -> s.filterSubway
    "jr" -> s.filterJr
    "private" -> s.filterPrivate
    else -> f
}

/** 노선도 화면: 전체 노선도 + 상단 검색창·언어·설정 + 노선 필터 + 역 탭 시 바텀시트(출발/도착/도착정보) + 하단 경로 선택 바 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenStation: (String) -> Unit,
    onSearch: () -> Unit,
    onRoute: () -> Unit,
    onRegion: () -> Unit,
    onSettings: () -> Unit,
    vm: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val s = strings()
    val names = nameLanguage()
    val ui by Lang.ui.collectAsState()
    var tapped by remember { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(FilterOrder.toSet()) }
    var showLanguage by remember { mutableStateOf(false) }
    val data = (state as? RegionSession.State.Ready)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onRegion) {
                        Text((data?.region?.name?.inLanguage(names) ?: s.region) + " ▾", fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Surface(
                        Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onSearch),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.searchHint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                actions = {
                    // 언어: 현재 화면 언어 코드(KO / JA / EN)를 보여 주고 누르면 언어 선택
                    TextButton(onClick = { showLanguage = true }) {
                        Text(languageMark(ui), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = s.settings) }
                },
            )
        },
        bottomBar = {
            if (data != null && (from != null || to != null)) {
                RouteSelectionBar(
                    s = s,
                    fromName = from?.let { data.network.groupName(it).inLanguage(names) },
                    toName = to?.let { data.network.groupName(it).inLanguage(names) },
                    onRoute = onRoute,
                    onClear = vm::clearRoute,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val st = state) {
                RegionSession.State.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is RegionSession.State.Error -> Text(
                    "${s.loadError}\n${st.message}",
                    Modifier.align(Alignment.Center).padding(24.dp),
                )
                is RegionSession.State.Ready -> {
                    val d = st.data
                    val available = remember(d.layout) {
                        FilterOrder.filter { f -> d.layout.lines.any { it.filter == f } }
                    }
                    SubwayMapCanvas(
                        layout = d.layout,
                        network = d.network,
                        fromGroup = from,
                        toGroup = to,
                        highlighted = tapped,
                        onStationTap = { tapped = it },
                        modifier = Modifier.fillMaxSize(),
                        visibleFilters = filters,
                        nameLanguage = names,
                    )
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (available.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                available.forEach { f ->
                                    val on = f in filters
                                    FilterChip(
                                        selected = on,
                                        onClick = {
                                            val next = if (on) filters - f else filters + f
                                            if (next.any { it in available }) filters = next
                                        },
                                        label = { Text(filterLabel(s, f)) },
                                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                                    )
                                }
                            }
                        }
                        if (d.timetable.isDemo) InfoChip(s.demoTimetable)
                    }
                    val g = tapped
                    if (g != null) {
                        ModalBottomSheet(onDismissRequest = { tapped = null }) {
                            StationSheet(
                                s = s,
                                names = names,
                                network = d.network,
                                group = g,
                                onFrom = {
                                    tapped = null
                                    if (vm.setFrom(g)) onRoute()
                                },
                                onTo = {
                                    tapped = null
                                    if (vm.setTo(g)) onRoute()
                                },
                                onInfo = {
                                    tapped = null
                                    onOpenStation(g)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showLanguage) MapLanguageDialog(onDismiss = { showLanguage = false })
}

private fun languageMark(l: AppLanguage) = when (l) {
    AppLanguage.JA -> "あ"
    AppLanguage.KO -> "가"
    else -> "A"
}

@Composable
private fun StationSheet(
    s: Strings,
    names: AppLanguage,
    network: Network,
    group: String,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onInfo: () -> Unit,
) {
    val name = network.groupName(group)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            network.linesOfGroup(group).distinctBy { it.id }.forEach { LineBadge(it) }
            Spacer(Modifier.width(4.dp))
            Text(name.inLanguage(names), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        // 보조 표기: 지금 역명 언어가 아닌 나머지 표기
        val others = listOf(AppLanguage.JA to name.ja, AppLanguage.KO to name.ko, AppLanguage.EN to name.en)
            .filter { (l, v) -> l != names && v.isNotBlank() }
            .joinToString("  ") { it.second }
        if (others.isNotBlank()) {
            Text(others, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFrom, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = FromGreen)) { Text(s.from) }
            Button(onClick = onTo, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ToRed)) { Text(s.to) }
            OutlinedButton(onClick = onInfo, Modifier.weight(1f)) { Text(s.arrivalInfo, maxLines = 1) }
        }
    }
}

@Composable
private fun RouteSelectionBar(s: Strings, fromName: String?, toName: String?, onRoute: () -> Unit, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.from + "  " + (fromName ?: s.notSelected), color = FromGreen, fontWeight = FontWeight.SemiBold)
                Text(s.to + "  " + (toName ?: s.notSelected), color = ToRed, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = s.clearSelection) }
            Button(onClick = onRoute, enabled = fromName != null && toName != null) { Text(s.findRoute) }
        }
    }
}
