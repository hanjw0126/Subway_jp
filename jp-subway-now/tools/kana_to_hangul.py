"""일본어 가나 → 한글 표기 변환 (국립국어원 외래어 표기법 기준).

- 어두의 か·た행 청음은 예사소리(가/다), 어중·어말은 거센소리(카/타)
- つ → 쓰, っ → ㅅ 받침, ん → ㄴ 받침, 장음(おう/おお/うう, ー)은 적지 않음
Kotlin 구현(core/i18n/KanaToHangul.kt)과 동일한 알고리즘을 유지합니다.
"""
import csv
import os

TABLE = {}


def _load(src, n):
    for e in src.split():
        k, v = e[:n], e[n:].split("|")
        TABLE[k] = (v[0], v[1] if len(v) > 1 else v[0])


_load("あ아 い이 う우 え에 お오 か가|카 き기|키 く구|쿠 け게|케 こ고|코 が가 ぎ기 ぐ구 げ게 ご고 さ사 し시 す스 せ세 そ소 "
      "ざ자 じ지 ず즈 ぜ제 ぞ조 た다|타 ち지|치 つ쓰 て데|테 と도|토 だ다 ぢ지 づ즈 で데 ど도 な나 に니 ぬ누 ね네 の노 "
      "は하 ひ히 ふ후 へ헤 ほ호 ば바 び비 ぶ부 べ베 ぼ보 ぱ파 ぴ피 ぷ푸 ぺ페 ぽ포 ま마 み미 む무 め메 も모 "
      "や야 ゆ유 よ요 ら라 り리 る루 れ레 ろ로 わ와 を오 ゔ부 ぁ아 ぃ이 ぅ우 ぇ에 ぉ오 ゃ야 ゅ유 ょ요", 1)
_load("きゃ갸|캬 きゅ규|큐 きょ교|쿄 ぎゃ갸 ぎゅ규 ぎょ교 しゃ샤 しゅ슈 しょ쇼 じゃ자 じゅ주 じょ조 "
      "ちゃ자|차 ちゅ주|추 ちょ조|초 にゃ냐 にゅ뉴 にょ뇨 ひゃ햐 ひゅ휴 ひょ효 びゃ뱌 びゅ뷰 びょ뵤 "
      "ぴゃ퍄 ぴゅ퓨 ぴょ표 みゃ먀 みゅ뮤 みょ묘 りゃ랴 りゅ류 りょ료 ふぁ파 ふぃ피 ふぇ페 ふぉ포 てぃ티 でぃ디 うぃ위 うぇ웨 うぉ워", 2)

O = {8, 12}                 # ㅗ ㅛ
O_U = {8, 12, 13, 17, 18}   # ㅗ ㅛ ㅜ ㅠ ㅡ


def to_hiragana(s):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def _jung(ch):
    c = ord(ch) - 0xAC00
    return (c // 28) % 21 if 0 <= c < 11172 else -1


def _add_final(out, jong):
    if not out:
        return
    c = ord(out[-1]) - 0xAC00
    if 0 <= c < 11172 and c % 28 == 0:
        out[-1] = chr(ord(out[-1]) + jong)


def convert(text):
    s = to_hiragana(text)
    out, word_start, last, i = [], True, -1, 0
    while i < len(s):
        c = s[i]
        if c == "ー":
            i += 1
            continue
        if c == "っ":
            _add_final(out, 19)
            last = -1
            i += 1
            continue
        if c == "ん":
            _add_final(out, 4)
            last = -1
            word_start = False
            i += 1
            continue
        if (c == "う" and last in O_U) or (c == "お" and last in O):
            i += 1
            continue
        two = s[i:i + 2]
        if len(two) == 2 and two in TABLE:
            e = TABLE[two]
            i += 2
        elif c in TABLE:
            e = TABLE[c]
            i += 1
        else:
            out.append(c)
            i += 1
            word_start = c.isspace() or c == "・"
            last = -1
            continue
        syl = e[0] if word_start else e[1]
        out.append(syl)
        last = _jung(syl[-1])
        word_start = False
    return "".join(out)


def load_overrides(path=None):
    path = path or os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed", "ko_overrides.csv")
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return {row["ja"]: row["ko"] for row in csv.DictReader(f) if row.get("ja") and row.get("ko")}


def resolve_ko(ja, kana, overrides, given=""):
    """우선순위: 수동 보정 > 원본 ko 표기 > 가나 자동 변환"""
    if ja in overrides:
        return overrides[ja]
    if given:
        return given
    return convert(kana or ja)


if __name__ == "__main__":
    import sys
    for w in sys.argv[1:]:
        print(w, "->", convert(w))
