import json

import seed_merge
from layout_schematic import bundle_lanes, metrics
from seed_merge import app_regions


def test_bundle_lanes_parallel():
    paths = {("A", "B"): [(0, 0), (1, 0), (2, 1)], ("A", "C"): [(0, 0), (1, 0), (2, -1)], ("B", "D"): [(2, 1), (3, 1)]}
    edges = {("A", "B"): ["L1"], ("A", "C"): ["L2"], ("B", "D"): ["L1"]}
    lanes = bundle_lanes(paths, edges, ["L1", "L2"])
    assert lanes[(("A", "B"), "L1")] == -0.5 and lanes[(("A", "C"), "L2")] == 0.5
    assert lanes[(("B", "D"), "L1")] == 0
    lay = {"unit": 1.0, "nodes": [{"x": x, "y": y} for x, y in [(0, 0), (2, 1), (2, -1), (3, 1)]],
           "lines": [{"segments": [{"points": paths[("A", "B")], "offset": -0.5}, {"points": paths[("B", "D")], "offset": 0}]},
                     {"segments": [{"points": paths[("A", "C")], "offset": 0.5}]}]}
    m = metrics(lay)
    assert m["overlaps"] == 0 and m["bundled"] == 1


def test_c2026_seed_merge(tmp_path, monkeypatch):
    r = next(x for x in app_regions() if x["id"] == "tokyo")
    base = seed_merge.load_region_seed(r)
    assert not any(L.get("source") == "c2026" for L in base["lines"])
    fake = {"lines": [dict(base["lines"][0], id="odpt.Railway:JR-East.Test", source="c2026"), base["lines"][1]],
            "extraTransfers": []}
    (tmp_path / "tokyo.json").write_text(json.dumps(fake), encoding="utf-8")
    monkeypatch.setattr(seed_merge, "C2026_DIR", str(tmp_path))
    merged = seed_merge.load_region_seed(r)
    assert sum(L.get("source") == "c2026" for L in merged["lines"]) == 1
    assert len(merged["lines"]) == len(base["lines"]) + 1
    monkeypatch.setenv("C2026_DISABLE", "1")
    assert not any(L.get("source") == "c2026" for L in seed_merge.load_region_seed(r)["lines"])
