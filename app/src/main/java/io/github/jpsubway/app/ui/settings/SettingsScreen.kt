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
import io.github.jpsubway.app.core.time.ServiceClock
import io.github.jpsubway.app.di.AppContainer
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.ui.common.appContainer
import java.time.Instant
import java.time.format.DateTimeFormatter

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.session.state
    val realtimeSource: String = when {
        c.settings.usingProxy() -> "실시간 중계 서버"
        c.settings.hasToken() -> "개발용 ODPT 키"
        else -> "사용 안 함 (데모 시간표)"
    }

    fun refresh() {
        c.timetables.clearMemory()
        c.session.reload()
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(appContainer()) } }
    }
}

const val ODPT_NOTICE_JA = "本アプリケーションが利用する公共交通データは、公共交通オープンデータセンターにおいて提供されるものです。" +
    "公共交通事業者により提供されたデータを元にしていますが、必ずしも正確・完全なものとは限りません。" +
    "本アプリケーションの表示内容について、公共交通事業者への直接の問合せは行わないでください。"
const val ODPT_NOTICE_KO = "이 앱이 사용하는 대중교통 데이터는 공공교통 오픈데이터센터(ODPT)가 제공합니다. " +
    "사업자가 제공한 데이터를 바탕으로 하지만 정확성·완전성을 보장하지 않습니다. " +
    "앱 표시 내용에 대해 철도 사업자에게 직접 문의하지 마세요. 이 앱은 각 철도 사업자와 관련 없는 비공식 앱입니다."

const val C2026_NOTICE_KO = "JR 동일본·도부·세이부·도큐·게이오·게이큐·오다큐·소테쓰 노선은 「공공교통 오픈데이터 챌린지 2026」에서 " +
    "제공하는 데이터를 이용합니다. 챌린지 기간이 끝나면 해당 노선은 앱에서 제외될 수 있습니다."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val data = (state as? RegionSession.State.Ready)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("현재 데이터")
            Text("실시간 정보: ${vm.realtimeSource}", fontSize = 13.sp)
            if (data != null) {
                val fetched = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(Instant.ofEpochSecond(data.timetable.fetchedAtEpochSec).atZone(ServiceClock.JST))
                Text("지역: ${data.region.id} · 요일 구분: ${data.dayType}", fontSize = 13.sp)
                Text("시간표: ${if (data.timetable.isDemo) "데모(가상)" else data.timetable.source} · 열차 ${data.timetable.trips.size}편 · 받은 시각 $fetched (JST)", fontSize = 13.sp)
                data.timetableError?.let { Text("시간표 오류: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            } else {
                Text("불러오는 중…", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { vm.refresh() }) { Text("시간표 다시 받기") }

            Section("데이터 출처 및 면책")
            Text(ODPT_NOTICE_KO, fontSize = 13.sp)
            Text(ODPT_NOTICE_JA, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(C2026_NOTICE_KO, fontSize = 13.sp)
            Text("역명 한글 표기는 국립국어원 외래어 표기법(일본어)을 따라 자동 변환 후 수동 보정했습니다.", fontSize = 13.sp)

            Section("앱 정보")
            Text("월드 와이드 메트로 · 버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · MIT License", fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    HorizontalDivider()
}
