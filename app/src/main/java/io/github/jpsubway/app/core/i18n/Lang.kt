package io.github.jpsubway.app.core.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 국가별 노선도 화면(노선도·역·경로·검색·설정)의 현재 언어.
 *  ui    = 화면 문구 언어 ([Strings])
 *  names = 역명·노선명 언어 (LocalizedName.display())
 * SettingsRepository 가 저장값을 여기에 반영한다. 메인(국가 선택) 화면은 별도 언어(appLanguage)를 쓴다.
 */
object Lang {
    private val _ui = MutableStateFlow(AppLanguage.KO)
    private val _names = MutableStateFlow(AppLanguage.KO)
    val ui: StateFlow<AppLanguage> = _ui.asStateFlow()
    val names: StateFlow<AppLanguage> = _names.asStateFlow()

    fun set(ui: AppLanguage, names: AppLanguage) {
        _ui.value = ui
        _names.value = names
    }

    /** 국가별 노선도 화면 언어 후보: [그 나라 언어, 영어, 한국어] */
    fun mapOptions(country: String): List<AppLanguage> = when (country) {
        "kr" -> listOf(AppLanguage.KO, AppLanguage.EN)
        else -> listOf(AppLanguage.JA, AppLanguage.EN, AppLanguage.KO)
    }
}
