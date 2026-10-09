package io.github.jpsubway.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Choseong
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.domain.model.Network
import io.github.jpsubway.app.domain.model.detailed
import io.github.jpsubway.app.domain.model.inLanguage
import io.github.jpsubway.app.ui.common.LineBadge
import io.github.jpsubway.app.ui.common.appContainer
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.session.state
    fun setFrom(g: String) {
        if (c.routeSelection.to.value == g) c.routeSelection.setTo(null)
        c.routeSelection.setFrom(g)
    }
    fun setTo(g: String) {
        if (c.routeSelection.from.value == g) c.routeSelection.setFrom(null)
        c.routeSelection.setTo(g)
    }
    companion object {
        val Factory = viewModelFactory { initializer { SearchViewModel(appContainer()) } }
    }
}

/** 한글·초성(ㅅㅈㅋ → 신주쿠)·한국어 위키백과 표기·일본어·가나·영문·역번호(G09) 검색 */
internal fun searchGroups(net: Network, groups: List<String>, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return groups
    return groups.filter { g ->
        val n = net.groupName(g)
        Choseong.matches(n.ko, q) ||
            (n.koAlt.isNotBlank() && Choseong.matches(n.koAlt, q)) ||
            n.ja.contains(q) || n.kana.contains(q) ||
            n.en.contains(q, ignoreCase = true) ||
            net.stationsByGroup[g].orEmpty().any { it.code.equals(q, ignoreCase = true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSearchScreen(
    mode: String,
    onBack: () -> Unit,
    onOpenStation: (String) -> Unit,
    vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = strings()
    val names = nameLanguage()
    val net = (state as? RegionSession.State.Ready)?.data?.network
    var q by rememberSaveable { mutableStateOf("") }
    val groups = remember(net, names) { net?.let { n -> n.stationsByGroup.keys.sortedBy { n.groupName(it).inLanguage(names) } }.orEmpty() }
    val results = remember(net, groups, q) { if (net == null) emptyList() else searchGroups(net, groups, q) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (mode) { "from" -> s.pickFrom; "to" -> s.pickTo; else -> s.stationSearch }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(s.searchPlaceholder) },
                singleLine = true,
            )
            if (net != null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it }) { g ->
                        val name = net.groupName(g)
                        // 보조 표기: 지금 역명 언어가 아닌 나머지 표기
                        val others = listOf(AppLanguage.JA to name.ja, AppLanguage.KO to name.ko, AppLanguage.EN to name.en)
                            .filter { (l, v) -> l != names && v.isNotBlank() }
                            .joinToString("  ") { it.second }
                        ListItem(
                            modifier = Modifier.clickable {
                                when (mode) {
                                    "from" -> { vm.setFrom(g); onBack() }
                                    "to" -> { vm.setTo(g); onBack() }
                                    else -> onOpenStation(g)
                                }
                            },
                            leadingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    net.linesOfGroup(g).distinctBy { it.id }.forEach { LineBadge(it) }
                                }
                            },
                            // 한국어 역명이면 위키백과 표기를 괄호로 (위키백과 표기로 검색해도 왜 나왔는지 알 수 있게)
                            headlineContent = { Text(name.detailed(names)) },
                            supportingContent = { Text(others) },
                        )
                    }
                }
            }
        }
    }
}
