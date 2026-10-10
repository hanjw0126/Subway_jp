package io.github.jpsubway.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jpsubway.app.BuildConfig
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.KoreaStrings
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.core.i18n.Strings
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.ui.common.MapLanguageOptions
import io.github.jpsubway.app.ui.common.appContainer
import io.github.jpsubway.app.ui.common.strings
import java.time.Instant
import java.time.format.DateTimeFormatter

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.session.state

    fun realtimeSource(s: Strings): String = when {
        c.settings.usingProxy() -> s.realtimeProxy
        c.settings.hasToken() -> s.realtimeDevKey
        else -> s.realtimeNone
    }

    fun refresh() {
        c.timetables.clearMemory()
        c.session.reload()
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(appContainer()) } }
    }
}

/** ODPT 이용 약관의 일본어 고지문 (화면 언어와 관계없이 항상 표시) */
const val ODPT_NOTICE_JA = "本アプリケーションが利用する公共交通データは、公共交通オープンデータセンターにおいて提供されるものです。" +
    "公共交通事業者により提供されたデータを元にしていますが、必ずしも正確・完全なものとは限りません。" +
    "本アプリケーションの表示内容について、公共交通事業者への直接の問合せは行わないでください。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = strings()
    val ui by Lang.ui.collectAsState()
    val data = (state as? RegionSession.State.Ready)?.data
    // 한국(서울): 시간표 없이 실시간 도착 API → 시간표 정보·다시 받기 대신 출처만
    val korea = data?.isLiveArrivals == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settings) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(s.language)
            MapLanguageOptions()

            Section(s.currentData)
            Text(s.realtimeSource(vm.realtimeSource(s)), fontSize = 13.sp)
            if (data != null) {
                Text(s.regionLine(data.region.id, data.dayType.toString()), fontSize = 13.sp)
                if (korea) {
                    Text(KoreaStrings.sourceLive(ui), fontSize = 13.sp)
                } else {
                    val fetched = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .format(Instant.ofEpochSecond(data.timetable.fetchedAtEpochSec).atZone(ServiceClock.JST))
                    Text(
                        s.timetableLine(if (data.timetable.isDemo) s.demoSource else data.timetable.source, data.timetable.trips.size, fetched),
                        fontSize = 13.sp,
                    )
                    data.timetableError?.let { Text(s.timetableError(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                }
            } else {
                Text(s.loading, fontSize = 13.sp)
            }
            if (!korea) OutlinedButton(onClick = { vm.refresh() }) { Text(s.refreshTimetable) }

            Section(s.sourcesAndDisclaimer)
            if (korea) {
                Text(KoreaStrings.notice(ui), fontSize = 13.sp)
                Text(KoreaStrings.osm(ui), fontSize = 13.sp)
            } else {
                Text(s.odptNotice, fontSize = 13.sp)
                if (ui != AppLanguage.JA) {
                    Text(ODPT_NOTICE_JA, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(s.c2026Notice, fontSize = 13.sp)
                Text(s.nameNote, fontSize = 13.sp)
            }

            Section(s.appInfo)
            Text(s.appInfoLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    HorizontalDivider()
}
