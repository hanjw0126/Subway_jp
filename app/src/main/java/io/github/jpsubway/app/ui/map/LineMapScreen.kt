package io.github.jpsubway.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.core.i18n.KoreaStrings
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.domain.seoul.SeoulLive
import io.github.jpsubway.app.domain.seoul.SeoulTrains
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.LinePill
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings
import io.github.jpsubway.app.ui.theme.parseColor

/**
 * 노선 전체 지도: 한 노선(같은 회사·같은 색 지선 포함)만 노선도에 그려 화면에 맞춰 보여 준다.
 * group 은 강조할 역(상세정보에서 넘어온 역). 역을 누르면 그 역의 도착정보(이 노선 선택)로 이동한다.
 * 서울 노선은 실시간 열차 위치(15초마다)를 미니 열차 아이콘과 행선지로 함께 보여 준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineMapScreen(
    lineId: String,
    group: String?,
    onBack: () -> Unit,
    onOpenStation: (group: String, lineId: String) -> Unit,
    vm: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val s = strings()
    val names = nameLanguage()
    val ui by Lang.ui.collectAsState()
    val line = (state as? RegionSession.State.Ready)?.data?.network?.lineById?.get(lineId)
    // 서울 노선: 실시간 열차 위치 (일본 노선은 apiLine = null → 받지 않음)
    val apiLine = remember(lineId) { SeoulTrains.apiLineFor(lineId) }
    val liveVm: LineLiveViewModel = viewModel(key = "live-$lineId", factory = LineLiveViewModel.factory(apiLine))
    val live by liveVm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (line != null) LinePill(line) else Text(s.lineMap) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) }
                },
            )
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
                    // 같은 회사·같은 색 노선(본선/지선)은 한 노선으로 보고 함께 그린다
                    val focus = remember(d.network, lineId) {
                        val base = d.network.lineById[lineId]
                        if (base == null) {
                            setOf(lineId)
                        } else {
                            d.network.lines
                                .filter { it.id == lineId || (it.operator == base.operator && it.color.equals(base.color, ignoreCase = true)) }
                                .map { it.id }
                                .toSet()
                        }
                    }
                    val box = remember(d.layout, focus) { linesBounds(d.layout, focus) }
                    // 실시간 열차 아이콘 + 행선지 라벨 (역명 언어로)
                    val trains = remember(live.rows, d.layout, d.network, focus, names, ui) {
                        if (apiLine == null || live.rows.isEmpty()) {
                            emptyList()
                        } else {
                            val xy = d.layout.nodes.associate { it.group to (it.x to it.y) }
                            val byKo = d.network.stations.associateBy({ SeoulLive.apiStationName(it.name.ko) }, { it.name })
                            SeoulTrains.markers(live.rows, d.network, focus, xy).map { m ->
                                val dest = byKo[SeoulLive.apiStationName(m.destination)]?.display() ?: m.destination
                                val prefix = if (m.express) "[${KoreaStrings.trainType(ui, "급행")}] " else ""
                                val suffix = if (m.last) " · ${KoreaStrings.lastTrain(ui)}" else ""
                                m to (prefix + s.destination(dest) + suffix)
                            }
                        }
                    }
                    if (box == null) {
                        Text(s.noLineLayout, Modifier.align(Alignment.Center).padding(24.dp))
                    } else {
                        SubwayMapCanvas(
                            layout = d.layout,
                            network = d.network,
                            fromGroup = from,
                            toGroup = to,
                            highlighted = group,
                            onStationTap = { g ->
                                val lid = d.network.linesOfGroup(g).firstOrNull { it.id in focus }?.id ?: lineId
                                onOpenStation(g, lid)
                            },
                            modifier = Modifier.fillMaxSize(),
                            focusLines = focus,
                            initialFocus = box,
                            nameLanguage = names,
                            trains = trains,
                            trainColor = line?.let { parseColor(it.color) } ?: parseColor("#3B5BDB"),
                        )
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (apiLine != null && live.fetched) {
                                val err = live.error
                                InfoChip(
                                    if (err != null && trains.isEmpty()) "${KoreaStrings.positionError(ui)} ($err)"
                                    else KoreaStrings.liveTrains(ui, trains.size),
                                )
                            }
                            InfoChip(s.tapStationForArrivals)
                        }
                    }
                }
            }
        }
    }
}
