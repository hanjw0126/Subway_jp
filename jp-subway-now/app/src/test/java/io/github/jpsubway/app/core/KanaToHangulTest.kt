package io.github.jpsubway.app.core

import io.github.jpsubway.app.core.i18n.KanaToHangul
import org.junit.Assert.assertEquals
import org.junit.Test

class KanaToHangulTest {
    private val cases = listOf(
        "しぶや" to "시부야", "おもてさんどう" to "오모테산도", "あおやまいっちょうめ" to "아오야마잇초메",
        "あかさかみつけ" to "아카사카미쓰케", "しんばし" to "신바시", "きょうばし" to "교바시",
        "こっかいぎじどうまえ" to "곳카이기지도마에", "とうきょう" to "도쿄", "おおてまち" to "오테마치",
        "しんおおつか" to "신오쓰카", "すいてんぐうまえ" to "스이텐구마에", "しんおおさか" to "신오사카",
        "どうぶつえんまえ" to "도부쓰엔마에", "てんのうじ" to "덴노지", "コスモスクエア" to "고스모스쿠에아",
    )

    @Test
    fun convertsLikePythonTool() {
        cases.forEach { (kana, ko) -> assertEquals(kana, ko, KanaToHangul.convert(kana)) }
    }
}
