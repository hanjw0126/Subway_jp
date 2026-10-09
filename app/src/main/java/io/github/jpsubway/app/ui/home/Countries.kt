package io.github.jpsubway.app.ui.home

import io.github.jpsubway.app.core.i18n.AppLanguage

/**
 * 메인 화면의 국가 버튼. available = false 면 "준비 중"으로 표시하고 누를 수 없다.
 * 국가를 늘릴 때는 여기에 한 줄 추가하고, 내비게이션에서 id 로 해당 노선도를 연결한다.
 */
data class Country(
    val id: String,
    val flag: String,
    val name: Map<AppLanguage, String>,
    val cities: Map<AppLanguage, String>,
    val available: Boolean,
) {
    fun nameIn(lang: AppLanguage) = name[lang] ?: name.getValue(AppLanguage.EN)
    fun citiesIn(lang: AppLanguage) = cities[lang] ?: cities.getValue(AppLanguage.EN)
}

object Countries {
    const val JAPAN = "jp"
    const val KOREA = "kr"

    val all = listOf(
        Country(
            id = JAPAN,
            flag = "\uD83C\uDDEF\uD83C\uDDF5",
            name = mapOf(AppLanguage.KO to "일본", AppLanguage.EN to "Japan", AppLanguage.ES to "Japón"),
            cities = mapOf(
                AppLanguage.KO to "도쿄 · 요코하마 · 오사카",
                AppLanguage.EN to "Tokyo · Yokohama · Osaka",
                AppLanguage.ES to "Tokio · Yokohama · Osaka",
            ),
            available = true,
        ),
        Country(
            id = KOREA,
            flag = "\uD83C\uDDF0\uD83C\uDDF7",
            name = mapOf(AppLanguage.KO to "한국", AppLanguage.EN to "South Korea", AppLanguage.ES to "Corea del Sur"),
            cities = mapOf(AppLanguage.KO to "서울", AppLanguage.EN to "Seoul", AppLanguage.ES to "Seúl"),
            available = false,
        ),
    )
}
