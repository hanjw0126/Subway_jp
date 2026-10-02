"""헵번식 로마자(ODPT 영문 역명) → 히라가나. 장음 정보는 원래 없으므로 무시된다(한글 표기법도 장음 미표기)."""
import re
import unicodedata

VOW = "aiueo"
T = {"a": "あ", "i": "い", "u": "う", "e": "え", "o": "お"}
for _c, _ks in {"k": "かきくけこ", "g": "がぎぐげご", "s": "さしすせそ", "z": "ざじずぜぞ", "t": "たちつてと",
                "d": "だぢづでど", "n": "なにぬねの", "h": "はひふへほ", "b": "ばびぶべぼ", "p": "ぱぴぷぺぽ",
                "m": "まみむめも", "r": "らりるれろ"}.items():
    for _v, _k in zip(VOW, _ks):
        T[_c + _v] = _k
T.update({"shi": "し", "chi": "ち", "tsu": "つ", "fu": "ふ", "ji": "じ", "ya": "や", "yu": "ゆ", "yo": "よ",
          "wa": "わ", "wo": "を", "ja": "じゃ", "ju": "じゅ", "jo": "じょ", "sha": "しゃ", "shu": "しゅ",
          "sho": "しょ", "cha": "ちゃ", "chu": "ちゅ", "cho": "ちょ", "fa": "ふぁ", "fi": "ふぃ", "fe": "ふぇ",
          "fo": "ふぉ", "she": "しぇ", "che": "ちぇ", "je": "じぇ"})
for _c, _k in [("k", "き"), ("g", "ぎ"), ("n", "に"), ("h", "ひ"), ("b", "び"), ("p", "ぴ"), ("m", "み"), ("r", "り")]:
    for _v, _s in zip("auo", "ゃゅょ"):
        T[_c + "y" + _v] = _k + _s


def romaji_to_kana(s):
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    s = re.sub(r"[\(\[〈<（].*?[\)\]〉>）]", "", s)
    out, i = [], 0
    while i < len(s):
        c = s[i]
        nx = s[i + 1] if i + 1 < len(s) else ""
        if not ("a" <= c <= "z"):
            i += 1
            continue
        if c == "n" and (not ("a" <= nx <= "z") or (nx not in VOW and nx != "y")):
            out.append("ん"); i += 1; continue
        if c == "m" and nx in ("b", "p", "m") and nx:
            out.append("ん"); i += 1; continue
        if c == nx and c not in VOW:
            out.append("っ"); i += 1; continue
        if c == "t" and s[i + 1:i + 3] == "ch":
            out.append("っ"); i += 1; continue
        for L in (3, 2, 1):
            seg = s[i:i + L]
            if seg in T:
                out.append(T[seg]); i += L; break
        else:
            i += 1
    return "".join(out)
