package io.github.jpsubway.app.domain.status

/** ODPT 운행정보(일본어) → 한국어 요약 */
object StatusTranslator {
    private val rules = listOf(
        "運転を見合わせ" to "운행 중단",
        "見合わせ" to "운행 중단",
        "運転再開" to "운행 재개",
        "直通運転を中止" to "직통운전 중지",
        "折り返し運転" to "일부 구간 회차 운행",
        "ダイヤが乱れ" to "열차 시각 혼란",
        "遅れ" to "지연 운행",
        "遅延" to "지연 운행",
        "運休" to "일부 열차 운휴",
    )

    fun isNormal(ja: String): Boolean = ja.isBlank() || rules.none { ja.contains(it.first) }

    fun toKorean(ja: String): String {
        if (isNormal(ja)) return "평상시대로 운행"
        return rules.filter { ja.contains(it.first) }.map { it.second }.distinct().joinToString(" · ")
    }
}
