import importlib.util
import os

from build_network_from_seed import build
from kana_to_hangul import load_overrides

# tools/c2026/build_seed.py 와 모듈 이름이 겹치지 않도록 파일 경로로 불러온다
_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "seoul", "build_seed.py")
_spec = importlib.util.spec_from_file_location("seoul_build_seed", _PATH)
seoul_seed = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(seoul_seed)


def _seed():
    return seoul_seed.build()


def test_seed_shape():
    seed = _seed()
    ids = [L["id"] for L in seed["lines"]]
    assert len(ids) == len(set(ids)) and len(ids) >= 20
    by = {L["id"]: L for L in seed["lines"]}
    l2 = by["kr.Seoul.2"]
    assert l2["loop"] and len(l2["stations"]) == 43
    assert l2["asc"]["ko"] == "외선순환"
    l6 = by["kr.Seoul.6"]
    assert l6["extraEdges"], "6호선 응암순환이 닫혀야 한다"
    kj = [s[7] for s in by["kr.Seoul.KJ"]["stations"]]
    assert kj[0] == "지평" and kj[-1] == "임진강"
    i = kj.index("효창공원앞")
    assert kj[i - 1] == "용산" and kj[i + 1] == "공덕"
    for L in seed["lines"]:
        for s in L["stations"]:
            assert 33.0 < s[4] < 39.0 and 124.0 < s[5] < 131.0, (L["id"], s)
            assert s[7], s


def test_coordinates_follow_line_section():
    by = {L["id"]: L for L in _seed()["lines"]}
    yp5 = next(s for s in by["kr.Seoul.5"]["stations"] if s[7] == "양평")
    ypkj = next(s for s in by["kr.Seoul.KJ"]["stations"] if s[7] == "양평")
    assert yp5[5] < 127.0 < ypkj[5]  # 5호선 양평(서울 서쪽) ≠ 경의중앙선 양평(경기 양평군)


def test_network_groups_transfers():
    net = build(_seed(), load_overrides(), {})
    by = {}
    for s in net["stations"]:
        by.setdefault(s["group"], set()).add(s["lineId"])
    assert len(by["g.kr.서울"]) >= 4  # 1·4호선·경의중앙·공항철도·GTX-A
    names = {s["name"]["ko"] for s in net["stations"]}
    assert "강남" in names and "신도림" in names
    assert all("koAlt" not in s["name"] for s in net["stations"])
