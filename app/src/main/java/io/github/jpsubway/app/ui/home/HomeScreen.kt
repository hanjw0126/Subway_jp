package io.github.jpsubway.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsubway.app.JpSubwayApp
import io.github.jpsubway.app.R
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.ui.splash.SplashNavy

/** 메인 화면 문구 (한국어/영어/스페인어) */
private class HomeText(
    val tagline: String,
    val language: String,
    val chooseCountry: String,
    val comingSoon: String,
    val footer: String,
)

private fun homeText(lang: AppLanguage) = when (lang) {
    AppLanguage.KO -> HomeText(
        tagline = "전 세계 지하철 실시간 도착정보",
        language = "언어",
        chooseCountry = "국가를 선택하세요",
        comingSoon = "준비 중",
        footer = "비공식 앱 · 데이터: ODPT, 서울 열린데이터광장",
    )
    AppLanguage.ES -> HomeText(
        tagline = "Llegadas del metro en tiempo real en todo el mundo",
        language = "Idioma",
        chooseCountry = "Elige un país",
        comingSoon = "Próximamente",
        footer = "App no oficial · Datos: ODPT, Seoul Open Data",
    )
    else -> HomeText(
        tagline = "Real-time metro arrivals worldwide",
        language = "Language",
        chooseCountry = "Choose a country",
        comingSoon = "Coming soon",
        footer = "Unofficial app · Data: ODPT, Seoul Open Data",
    )
}

/** 메인 화면: 언어 선택 + 국가 선택 버튼. 국가를 고르면 그 나라에서 마지막으로 본 지역의 노선도로 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenCountry: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as JpSubwayApp).container
    val settings = container.settings
    val lang by settings.appLanguage.collectAsStateWithLifecycle()
    val t = homeText(lang)

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(SplashNavy),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(t.tagline, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(t.language, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.HOME.forEach { l ->
                    FilterChip(
                        selected = l == lang,
                        onClick = { settings.setAppLanguage(l) },
                        label = { Text(l.label) },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(t.chooseCountry, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Countries.all.forEach { c ->
                CountryButton(c, lang, t.comingSoon) {
                    if (container.selectCountry(c.id)) onOpenCountry(c.id)
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(t.footer, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryButton(c: Country, lang: AppLanguage, comingSoon: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = c.available,
        modifier = Modifier.fillMaxWidth().alpha(if (c.available) 1f else 0.6f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.flag, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nameIn(lang), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(c.citiesIn(lang), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!c.available) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(comingSoon, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp)
                }
            }
        }
    }
}
