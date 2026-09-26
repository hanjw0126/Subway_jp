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
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.domain.model.display
import io.github.jpsubway.app.ui.common.InfoChip
import io.github.jpsubway.app.ui.common.appContainer

class RegionViewModel(private val c: AppContainer) : ViewModel() {
    val regions = c.networks.regions
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지역 선택") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(vm.regions, key = { it.id }) { r ->
                ListItem(
                    modifier = Modifier.clickable { vm.select(r.id); onBack() },
                    headlineContent = { Text(r.name.display()) },
                    supportingContent = { Text(r.note.ifBlank { r.name.ja }) },
                    leadingContent = { RadioButton(selected = r.id == current, onClick = { vm.select(r.id); onBack() }) },
                    trailingContent = { InfoChip(if (r.realtime) "실시간" else "시간표") },
                )
            }
        }
    }
}
