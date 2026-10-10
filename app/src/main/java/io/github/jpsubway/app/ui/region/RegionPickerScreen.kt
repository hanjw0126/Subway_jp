package io.github.jpsubway.app.ui.region

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.domain.model.inLanguage
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.appContainer
import io.github.jpsubway.app.ui.common.nameLanguage
import io.github.jpsubway.app.ui.common.strings

class RegionViewModel(private val c: AppContainer) : ViewModel() {
    /** 일본 지역만 (한국 등 다른 나라는 메인 화면에서 국가를 골라 들어간다) */
    val regions = c.networks.regions.filter { it.country == "jp" }
    val current = c.settings.regionId
    fun select(id: String) {
        if (id != current.value) {
            c.routeSelection.clear()
            c.settings.setRegion(id)
        }
    }
    companion object {
        val Factory = viewModelFactory { initializer { RegionViewModel(appContainer()) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerScreen(onBack: () -> Unit, vm: RegionViewModel = viewModel(factory = RegionViewModel.Factory)) {
    val current by vm.current.collectAsStateWithLifecycle()
    val s = strings()
    val names = nameLanguage()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.pickRegion) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(vm.regions, key = { it.id }) { r ->
                // 보조 줄: 한국어 화면이면 지역 설명(note), 아니면 일본어 지역명
                val sub = if (names == AppLanguage.JA) r.name.en.ifBlank { r.name.ko } else r.note.takeIf { names == AppLanguage.KO }.orEmpty().ifBlank { r.name.ja }
                ListItem(
                    modifier = Modifier.clickable { vm.select(r.id); onBack() },
                    headlineContent = { Text(r.name.inLanguage(names)) },
                    supportingContent = { Text(sub) },
                    leadingContent = { RadioButton(selected = r.id == current, onClick = { vm.select(r.id); onBack() }) },
                    trailingContent = { InfoChip(if (r.realtime) s.realtime else s.timetable) },
                )
            }
        }
    }
}
