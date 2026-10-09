package io.github.jpsubway.app.core.i18n

import java.util.Locale

/**
 * 앱 화면 언어.
 *  - 메인(국가 선택) 화면: [HOME] = 한국어 / 영어 / 스페인어
 *  - 국가별 노선도 화면: [그 나라 언어, 영어, 한국어] (일본 = 일본어·영어·한국어)
 */
enum class AppLanguage(val code: String, val label: String) {
    KO("ko", "한국어"),
    EN("en", "English"),
    ES("es", "Español"),
    JA("ja", "日本語");

    companion object {
        val HOME = listOf(KO, EN, ES)

        fun of(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** 기기 언어가 메인 화면 지원 언어면 그것, 아니면 영어 */
        fun systemDefault(): AppLanguage = when (Locale.getDefault().language) {
            "ko" -> KO
            "es" -> ES
            else -> EN
        }
    }
}
