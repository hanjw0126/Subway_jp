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
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.LineBadge
import io.github.jpsubway.app.ui.theme.FromGreen
import io.github.jpsubway.app.ui.theme.ToRed

/** 메인 화면: 전체 노선도 + 상단 검색창 + 역 탭 시 바텀시트(출발/도착/도착정보) + 하단 경로 선택 바 */
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
    var tapped by remember { mutableStateOf<String?>(null) }
    val data = (state as? RegionSession.State.Ready)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TextButton(onClick = onRegion) {
                        Text((data?.region?.name?.display() ?: "지역") + " ▾", fontWeight = FontWeight.Bold)
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
                            Text("역 검색 (초성 가능)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "설정") }
                },
            )
        },
        bottomBar = {
            if (data != null && (from != null || to != null)) {
                RouteSelectionBar(
                    fromName = from?.let { data.network.groupName(it).display() },
                    toName = to?.let { data.network.groupName(it).display() },
                    onRoute = onRoute,
                    onClear = vm::clearRoute,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                RegionSession.State.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is RegionSession.State.Error -> Text(
                    "노선 데이터를 불러오지 못했습니다\n${s.message}",
                    Modifier.align(Alignment.Center).padding(24.dp),
                )
                is RegionSession.State.Ready -> {
                    val d = s.data
                    SubwayMapCanvas(
                        layout = d.layout,
                        network = d.network,
                        fromGroup = from,
                        toGroup = to,
                        highlighted = tapped,
                        onStationTap = { tapped = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (d.timetable.isDemo) InfoChip("데모(가상) 시간표 표시 중")
                    }
                    val g = tapped
                    if (g != null) {
                        ModalBottomSheet(onDismissRequest = { tapped = null }) {
                            StationSheet(
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
}

@Composable
private fun StationSheet(network: Network, group: String, onFrom: () -> Unit, onTo: () -> Unit, onInfo: () -> Unit) {
    val name = network.groupName(group)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            network.linesOfGroup(group).distinctBy { it.id }.forEach { LineBadge(it) }
            Spacer(Modifier.width(4.dp))
            Text(name.display(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (name.ja.isNotBlank()) {
            Text("${name.ja}  ${name.en}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFrom, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = FromGreen)) { Text("출발") }
            Button(onClick = onTo, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ToRed)) { Text("도착") }
            OutlinedButton(onClick = onInfo, Modifier.weight(1f)) { Text("도착정보") }
        }
    }
}

@Composable
private fun RouteSelectionBar(fromName: String?, toName: String?, onRoute: () -> Unit, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("출발  " + (fromName ?: "선택 안 됨"), color = FromGreen, fontWeight = FontWeight.SemiBold)
                Text("도착  " + (toName ?: "선택 안 됨"), color = ToRed, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "선택 해제") }
            Button(onClick = onRoute, enabled = fromName != null && toName != null) { Text("경로 찾기") }
        }
    }
}
