package io.github.jpsubway.app.core.i18n

/** 초성 검색: "ㅅㅈㅋ" → 신주쿠 */
object Choseong {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    fun of(s: String): String = buildString {
        s.forEach { c ->
            val code = c.code - 0xAC00
            append(if (code in 0 until 11172) CHO[code / 588] else c)
        }
    }

    fun isChoseongQuery(q: String): Boolean = q.isNotEmpty() && q.all { it in CHO }

    fun matches(name: String, query: String): Boolean {
        val q = query.replace(" ", "")
        if (q.isEmpty()) return true
        val n = name.replace(" ", "")
        return if (isChoseongQuery(q)) of(n).contains(q) else n.contains(q, ignoreCase = true)
    }
}
