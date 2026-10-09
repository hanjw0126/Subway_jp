package io.github.jpsubway.app.ui.map

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.LinePill
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings

/**
 * 노선 전체 지도: 한 노선(같은 회사·같은 색 지선 포함)만 노선도에 그려 화면에 맞춰 보여 준다.
 * group 은 강조할 역(상세정보에서 넘어온 역). 역을 누르면 그 역의 도착정보(이 노선 선택)로 이동한다.
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
    val line = (state as? RegionSession.State.Ready)?.data?.network?.lineById?.get(lineId)

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
                        )
                        InfoChip(s.tapStationForArrivals, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    }
                }
            }
        }
    }
}
