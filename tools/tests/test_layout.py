import math

from build_network_from_seed import build
from kana_to_hangul import load_overrides
from layout_schematic import build_layout, classify, metrics
from seed_merge import app_regions, load_region_seed

# 노선도 품질 상한 (0 이 목표). 값을 올리면 노선도가 나빠진 것이므로 PR 에서 사유를 적을 것
LIMITS = {
    "tokyo": {"nodeOnEdge": 0, "overlaps": 0, "crossings": 26},
    "yokohama": {"nodeOnEdge": 0, "overlaps": 0, "crossings": 0},
    "osaka": {"nodeOnEdge": 0, "overlaps": 0, "crossings": 0},
}
_CACHE = {}


def _net(region):
    r = next(x for x in app_regions() if x["id"] == region)
    return build(load_region_seed(r), load_overrides())


def _lay(region):
    if region not in _CACHE:
        net = _net(region)
        _CACHE[region] = (net, build_layout(net))
    return _CACHE[region]


def test_network_groups_and_transfers():
    net = _net("tokyo")
    by = {}
    for s in net["stations"]:
        by.setdefault(s["group"], []).append(s["lineId"])
    assert len(by["g.Ginza"]) >= 2 and len(by["g.OmoteSando"]) >= 2 and len(by["g.Otemachi"]) >= 4
    assert any(t["walkSec"] == 240 for t in net["transfers"])


def test_tama_merged_into_tokyo():
    regions = app_regions()
    assert "tama" not in [r["id"] for r in regions]
    tokyo = next(r for r in regions if r["id"] == "tokyo")
    assert "odpt.Operator:TamaMonorail" in tokyo["operators"]
    assert any(l["operator"].endswith("TamaMonorail") for l in _net("tokyo")["lines"])


def test_layout_invariants():
    for region, lim in LIMITS.items():
        net, lay = _lay(region)
        m = metrics(lay)
        assert m["allOctilinear"], m
        assert m["minNodeDist"] >= 0.5, m
        assert {n["group"] for n in lay["nodes"]} == {s["group"] for s in net["stations"]}
        assert len({(n["x"], n["y"]) for n in lay["nodes"]}) == len(lay["nodes"])
        for k, v in lim.items():
            assert m[k] <= v, (region, m)


def test_categories_and_terminal_icons():
    assert classify("odpt.Operator:JR-East", "odpt.Railway:JR-East.Yamanote") == ("jr", "jr")
    assert classify("odpt.Operator:Tokyu", "odpt.Railway:Tokyu.Toyoko") == ("private", "private")
    assert classify("odpt.Operator:Toei", "odpt.Railway:Toei.Asakusa") == ("toei", "subway")
    assert classify("odpt.Operator:TamaMonorail", "odpt.Railway:TamaMonorail.TamaMonorail") == ("monorail", "private")
    _, lay = _lay("tokyo")
    by = {l["lineId"]: l for l in lay["lines"]}
    ginza = by["odpt.Railway:TokyoMetro.Ginza"]
    assert ginza["category"] == "metro" and ginza["filter"] == "subway" and ginza["code"] == "G"
    assert len(ginza["terminals"]) == 2
    assert sum(len(l["terminals"]) for l in lay["lines"]) >= 25


def test_terminal_icons_on_line_extension():
    """종점 아이콘은 해당 노선 종단 구간을 연장한 직선 위, 종점에 가까이 있어야 한다"""
    for region in LIMITS:
        _, lay = _lay(region)
        u = lay["unit"]
        nodes = [(n["x"], n["y"]) for n in lay["nodes"]]
        for l in lay["lines"]:
            ends = [(s["points"][0], s["points"][1]) for s in l["segments"]] + \
                   [(s["points"][-1], s["points"][-2]) for s in l["segments"]]
            for t in l["terminals"]:
                g = min(nodes, key=lambda n: math.dist(n, t))
                assert math.dist(g, t) <= 0.8 * u, (region, l["lineId"], t)
                ok = False
                for end, prev in ends:
                    if math.dist(end, g) > 0.05:
                        continue
                    v1 = (g[0] - prev[0], g[1] - prev[1])
                    v2 = (t[0] - g[0], t[1] - g[1])
                    n1, n2 = math.hypot(*v1), math.hypot(*v2)
                    if n1 < 1e-6 or n2 < 1e-6:
                        continue
                    if abs(v1[0] * v2[1] - v1[1] * v2[0]) / (n1 * n2) < 0.02 and v1[0] * v2[0] + v1[1] * v2[1] > 0:
                        ok = True
                        break
                assert ok, (region, l["lineId"], t)


def test_auto_transfer_and_oedo_closure():
    net, lay = _lay("tokyo")
    sid = {s["id"]: s for s in net["stations"]}
    pairs = {(sid[t["from"]]["name"]["en"], sid[t["to"]]["name"]["en"]) for t in net["transfers"]}
    pairs |= {(b, a) for a, b in pairs}
    assert ("UenoOkachimachi", "NakaOkachimachi") in pairs
    oedo = next(l for l in net["lines"] if l["id"].endswith("Toei.Oedo"))
    assert len(oedo["stations"]) == len(set(oedo["stations"]))
    assert oedo.get("extraEdges")
    seg = next(l for l in lay["lines"] if l["lineId"] == oedo["id"])["segments"]
    assert len(seg) == len(oedo["stations"])
