"""tools/seed/<region>.json → app/src/main/assets/regions/<region>/network.json"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kana_to_hangul import load_overrides, resolve_ko  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")
GROUP_RADIUS_M = 600


def dist_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def build(seed, overrides):
    lines, stations, group_pos = [], [], {}
    for L in seed["lines"]:
        path = L["id"].split(":", 1)[1]
        ids = []
        for code, en, ja, kana, lat, lon in L["stations"]:
            sid = f"odpt.Station:{path}.{en}"
            g = f"g.{en}"
            if g in group_pos and dist_m(group_pos[g], (lat, lon)) > GROUP_RADIUS_M:
                g = f"g.{en}.{L['code']}"
            group_pos.setdefault(g, (lat, lon))
            stations.append({"id": sid, "lineId": L["id"], "code": code,
                             "name": {"ja": ja, "ko": resolve_ko(ja, kana, overrides), "en": en, "kana": kana},
                             "lat": lat, "lon": lon, "group": g})
            ids.append(sid)

        def d(x):
            return {"id": x["id"], "name": {"ja": x["ja"], "ko": x["ko"], "en": x["en"], "kana": ""}}
        lines.append({"id": L["id"], "operator": L["operator"], "code": L["code"],
                      "name": {**L["name"], "kana": ""}, "color": L["color"], "stations": ids,
                      "directions": {"asc": d(L["asc"]), "desc": d(L["desc"])}, "loop": bool(L.get("loop", False))})
    by_group = {}
    for s in stations:
        by_group.setdefault(s["group"], []).append(s["id"])
    walk = seed.get("transferWalkSec", 180)
    transfers = []
    for g, ids in sorted(by_group.items()):
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                transfers.append({"from": ids[i], "to": ids[j], "walkSec": walk})
    for ga, gb, sec in seed.get("extraTransfers", []):
        for a in by_group.get(ga, []):
            for b in by_group.get(gb, []):
                transfers.append({"from": a, "to": b, "walkSec": sec})
    return {"schemaVersion": 1, "regionId": seed["regionId"], "source": "seed",
            "lines": lines, "stations": stations, "transfers": transfers}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("seed")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    with open(a.seed, encoding="utf-8") as f:
        seed = json.load(f)
    net = build(seed, load_overrides())
    out = a.out or os.path.join(ASSETS, seed["regionId"], "network.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(net, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"[network] {seed['regionId']}: lines={len(net['lines'])} stations={len(net['stations'])} transfers={len(net['transfers'])}")


if __name__ == "__main__":
    main()
