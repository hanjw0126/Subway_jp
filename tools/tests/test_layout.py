import json
import os

from build_network_from_seed import build
from kana_to_hangul import load_overrides
from layout_schematic import build_layout, metrics

TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _net(region):
    with open(os.path.join(TOOLS, "seed", f"{region}.json"), encoding="utf-8") as f:
        return build(json.load(f), load_overrides())


def test_network_groups_and_transfers():
    net = _net("tokyo")
    by = {}
    for s in net["stations"]:
        by.setdefault(s["group"], []).append(s["lineId"])
    assert len(by["g.Ginza"]) == 2 and len(by["g.OmoteSando"]) == 2 and len(by["g.Otemachi"]) == 2
    assert any(t["walkSec"] == 240 for t in net["transfers"])


def test_layout_invariants():
    for region in ("tokyo", "osaka"):
        net = _net(region)
        lay = build_layout(net)
        m = metrics(lay)
        assert m["allOctilinear"], m
        assert m["minNodeDist"] >= 0.5, m
        assert {n["group"] for n in lay["nodes"]} == {s["group"] for s in net["stations"]}
        assert len({(n["x"], n["y"]) for n in lay["nodes"]}) == len(lay["nodes"])
