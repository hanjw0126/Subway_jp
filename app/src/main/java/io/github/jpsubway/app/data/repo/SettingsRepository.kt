package io.github.jpsubway.app.data.repo

import android.content.Context
import io.github.jpsubway.app.BuildConfig
import io.github.jpsubway.app.core.i18n.AppLanguage
import io.github.jpsubway.app.core.i18n.Lang
import io.github.jpsubway.app.data.remote.OdptClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 국가별 노선도 화면 언어 (ui = 화면 문구, names = 역명·노선명) */
data class MapLanguages(val ui: AppLanguage, val names: AppLanguage)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // 예전 버전에서 저장한 사용자 토큰은 더 이상 쓰지 않으므로 지운다
        if (prefs.contains(LEGACY_KEY_TOKEN)) prefs.edit().remove(LEGACY_KEY_TOKEN).apply()
    }

    private val _regionId = MutableStateFlow(prefs.getString(KEY_REGION, DEFAULT_REGION) ?: DEFAULT_REGION)
    val regionId: StateFlow<String> = _regionId.asStateFlow()

    fun setRegion(id: String) {
        prefs.edit().putString(KEY_REGION, id).apply()
        _regionId.value = id
    }

    /** 메인(국가 선택) 화면 언어: 한국어/영어/스페인어. 처음에는 기기 언어를 따른다 */
    private val _appLanguage = MutableStateFlow(
        AppLanguage.of(prefs.getString(KEY_APP_LANG, null))?.takeIf { it in AppLanguage.HOME } ?: AppLanguage.systemDefault(),
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: AppLanguage) {
        if (lang !in AppLanguage.HOME) return
        prefs.edit().putString(KEY_APP_LANG, lang.code).apply()
        _appLanguage.value = lang
    }

    /**
     * 일본 노선도 화면 언어 (후보: 일본어/영어/한국어).
     * 처음에는 메인 화면 언어가 후보에 있으면 그것, 없으면(스페인어) 영어.
     */
    private val _mapLanguages = MutableStateFlow(loadMapLanguages(COUNTRY_JP))
    val mapLanguages: StateFlow<MapLanguages> = _mapLanguages.asStateFlow()

    init {
        Lang.set(_mapLanguages.value.ui, _mapLanguages.value.names)
    }

    fun setMapLanguages(ui: AppLanguage, names: AppLanguage, country: String = COUNTRY_JP) {
        val opts = Lang.mapOptions(country)
        if (ui !in opts || names !in opts) return
        prefs.edit()
            .putString(KEY_MAP_UI + country, ui.code)
            .putString(KEY_MAP_NAMES + country, names.code)
            .apply()
        _mapLanguages.value = MapLanguages(ui, names)
        Lang.set(ui, names)
    }

    private fun loadMapLanguages(country: String): MapLanguages {
        val opts = Lang.mapOptions(country)
        val def = _appLanguage.value.takeIf { it in opts } ?: AppLanguage.EN
        fun read(key: String) = AppLanguage.of(prefs.getString(key, null))?.takeIf { it in opts } ?: def
        return MapLanguages(read(KEY_MAP_UI + country), read(KEY_MAP_NAMES + country))
    }

    /**
     * (debug 빌드 전용) local.properties 토큰 > 중계 서버.
     * 배포 APK 에는 ODPT 키가 들어가지 않고, 모든 요청은 중계 서버를 거친다.
     */
    fun consumerKey(): String = BuildConfig.ODPT_CONSUMER_KEY
        .ifBlank { if (BuildConfig.ODPT_PROXY_URL.isNotBlank()) OdptClient.PROXY_KEY else "" }

    fun usingProxy(): Boolean = consumerKey() == OdptClient.PROXY_KEY

    fun hasToken(): Boolean = consumerKey().isNotBlank()

    companion object {
        const val COUNTRY_JP = "jp"
        private const val KEY_REGION = "region"
        private const val KEY_APP_LANG = "app_language"
        private const val KEY_MAP_UI = "map_ui_"
        private const val KEY_MAP_NAMES = "map_names_"
        private const val LEGACY_KEY_TOKEN = "odpt_token"
        private const val DEFAULT_REGION = "tokyo"
    }
}
