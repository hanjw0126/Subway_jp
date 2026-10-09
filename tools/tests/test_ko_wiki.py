from build_network_from_seed import build
from kana_to_hangul import load_overrides
from ko_wiki import lookup, norm_ja, norm_ko, same_ko


def test_normalize():
    assert norm_ja("新宿駅") == "新宿"
    assert norm_ja("本町駅 (大阪府)") == "本町"
    assert norm_ja("駅") == "駅"
    assert norm_ko("신주쿠역") == "신주쿠"
    assert norm_ko("혼마치역 (오사카부)") == "혼마치"
    assert same_ko("신주쿠 산초메", "신주쿠산초메")
    assert not same_ko("시부야", "시부야마")


def test_lookup_picks_nearest_same_name():
    idx = {"本町": [(34.6828, 135.5003, "혼마치"), (35.6900, 139.6900, "혼초")]}
    assert lookup(idx, "本町", 34.683, 135.500) == "혼마치"
    assert lookup(idx, "本町", 35.6901, 139.6899) == "혼초"
    assert lookup(idx, "本町", 33.0, 130.0) is None  # 멀리 떨어진 동명 역은 무시
    assert lookup(idx, "新宿", 35.69, 139.70) is None


def test_build_adds_ko_alt_only_when_different():
    seed = {
        "regionId": "t",
        "lines": [{
            "id": "odpt.Railway:T.A", "operator": "odpt.Operator:T", "code": "A", "color": "#000000",
            "name": {"ja": "A線", "ko": "A선", "en": "A"},
            "asc": {"id": "asc", "ja": "", "ko": "", "en": ""}, "desc": {"id": "desc", "ja": "", "ko": "", "en": ""},
            "stations": [
                ["A01", "Shinjuku", "新宿", "しんじゅく", 35.6896, 139.7006],
                ["A02", "Hommachi", "本町", "ほんまち", 35.6900, 139.6900],
            ],
        }],
    }
    first = build(seed, load_overrides(), {})
    app_ko = {s["code"]: s["name"]["ko"] for s in first["stations"]}
    assert all("koAlt" not in s["name"] for s in first["stations"])  # 색인이 비면 그대로
    # 위키 표기가 앱 표기와 같으면 koAlt 없음, 다르면 koAlt
    wiki = {"新宿": [(35.6896, 139.7006, app_ko["A01"])], "本町": [(35.6900, 139.6900, app_ko["A02"] + "다른표기")]}
    net = build(seed, load_overrides(), wiki)
    by = {s["code"]: s["name"] for s in net["stations"]}
    assert "koAlt" not in by["A01"]
    assert by["A02"]["koAlt"] == app_ko["A02"] + "다른표기"
