package io.github.jpsubway.app.core.i18n

/**
 * 가나 → 한글 표기 (국립국어원 외래어 표기법).
 * 어두 か·た행 청음은 예사소리, 어중은 거센소리 / つ→쓰 / っ→ㅅ받침 / ん→ㄴ받침 / 장음 생략.
 * tools/kana_to_hangul.py 와 동일한 알고리즘입니다.
 */
object KanaToHangul {
    private val TABLE: Map<String, Pair<String, String>> = buildMap {
        fun load(src: String, keyLen: Int) {
            src.split(' ').filter { it.isNotBlank() }.forEach { e ->
                val v = e.substring(keyLen).split('|')
                put(e.substring(0, keyLen), v[0] to v.getOrElse(1) { v[0] })
            }
        }
        load(
            "あ아 い이 う우 え에 お오 か가|카 き기|키 く구|쿠 け게|케 こ고|코 が가 ぎ기 ぐ구 げ게 ご고 さ사 し시 す스 せ세 そ소 " +
                "ざ자 じ지 ず즈 ぜ제 ぞ조 た다|타 ち지|치 つ쓰 て데|테 と도|토 だ다 ぢ지 づ즈 で데 ど도 な나 に니 ぬ누 ね네 の노 " +
                "は하 ひ히 ふ후 へ헤 ほ호 ば바 び비 ぶ부 べ베 ぼ보 ぱ파 ぴ피 ぷ푸 ぺ페 ぽ포 ま마 み미 む무 め메 も모 " +
                "や야 ゆ유 よ요 ら라 り리 る루 れ레 ろ로 わ와 を오 ゔ부 ぁ아 ぃ이 ぅ우 ぇ에 ぉ오 ゃ야 ゅ유 ょ요",
            1,
        )
        load(
            "きゃ갸|캬 きゅ규|큐 きょ교|쿄 ぎゃ갸 ぎゅ규 ぎょ교 しゃ샤 しゅ슈 しょ쇼 じゃ자 じゅ주 じょ조 " +
                "ちゃ자|차 ちゅ주|추 ちょ조|초 にゃ냐 にゅ뉴 にょ뇨 ひゃ햐 ひゅ휴 ひょ효 びゃ뱌 びゅ뷰 びょ뵤 " +
                "ぴゃ퍄 ぴゅ퓨 ぴょ표 みゃ먀 みゅ뮤 みょ묘 りゃ랴 りゅ류 りょ료 ふぁ파 ふぃ피 ふぇ페 ふぉ포 てぃ티 でぃ디 うぃ위 うぇ웨 うぉ워",
            2,
        )
    }
    private val O = setOf(8, 12)                 // ㅗ ㅛ
    private val O_U = setOf(8, 12, 13, 17, 18)   // ㅗ ㅛ ㅜ ㅠ ㅡ

    fun convert(input: String): String {
        val s = toHiragana(input)
        val out = StringBuilder()
        var wordStart = true
        var lastVowel = -1
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == 'ー') { i++; continue }
            if (c == 'っ') { addFinal(out, 19); lastVowel = -1; i++; continue }
            if (c == 'ん') { addFinal(out, 4); lastVowel = -1; wordStart = false; i++; continue }
            if ((c == 'う' && lastVowel in O_U) || (c == 'お' && lastVowel in O)) { i++; continue }
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            val entry: Pair<String, String>? = when {
                two.isNotEmpty() && TABLE.containsKey(two) -> { i += 2; TABLE[two] }
                TABLE.containsKey(c.toString()) -> { i += 1; TABLE[c.toString()] }
                else -> null
            }
            if (entry == null) {
                out.append(c)
                i++
                wordStart = c.isWhitespace() || c == '・'
                lastVowel = -1
                continue
            }
            val syl = if (wordStart) entry.first else entry.second
            out.append(syl)
            lastVowel = jung(syl.last())
            wordStart = false
        }
        return out.toString()
    }

    fun toHiragana(s: String): String = buildString {
        s.forEach { append(if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it) }
    }

    private fun jung(ch: Char): Int {
        val c = ch.code - 0xAC00
        return if (c in 0 until 11172) (c / 28) % 21 else -1
    }

    private fun addFinal(sb: StringBuilder, jong: Int) {
        if (sb.isEmpty()) return
        val c = sb.last().code - 0xAC00
        if (c in 0 until 11172 && c % 28 == 0) sb.setCharAt(sb.lastIndex, (sb.last().code + jong).toChar())
    }
}
