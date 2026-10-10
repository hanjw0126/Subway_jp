"""tools/seed/<region>.json → app/src/main/assets/regions/<region>/network.json"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ko_wiki  # noqa: E402
from kana_to_hangul import load_overrides, resolve_ko  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")
GROUP_RADIUS_M = 600


def dist_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def build(seed, overrides, wiki=None):
    """wiki: 한국어 위키백과 역명 색인 (None 이면 tools/seed/ko_wiki.json). 앱 표기와 다르면 name.koAlt 로 넣는다.

    역 항목: [코드, 영문, 일문, 가나, 위도, 경도, (역 ID), (한글명), (환승 묶음 키)]
      한글명이 있으면(한국 노선) 가나 변환·위키백과 병기 없이 그대로 쓴다.
      환승 묶음 키가 없으면 영문 역명으로 묶는다.
    """
    if wiki is None:
        wiki = ko_wiki.load_index()
    lines, stations, group_pos = [], [], {}
    for L in seed["lines"]:
        path = L["id"].split(":", 1)[-1]
        ids = []
        en2sid = {}
        for code, en, ja, kana, lat, lon, *rest in L["stations"]:
            sid = rest[0] if rest else f"odpt.Station:{path}.{en}"
            ko_given = rest[1] if len(rest) > 1 else None
            gkey = rest[2] if len(rest) > 2 and rest[2] else en
            en2sid[en] = sid
            g = f"g.{gkey}"
            if g in group_pos and dist_m(group_pos[g], (lat, lon)) > GROUP_RADIUS_M:
                g = f"g.{gkey}.{L['code']}"
            group_pos.setdefault(g, (lat, lon))
            if ko_given:
                name = {"ja": ja, "ko": ko_given, "en": en, "kana": kana}
            else:
                ko = resolve_ko(ja, kana, overrides)
                name = {"ja": ja, "ko": ko, "en": en, "kana": kana}
                alt = ko_wiki.lookup(wiki, ja, lat, lon)
                if alt and not ko_wiki.same_ko(alt, ko):
                    name["koAlt"] = alt
            stations.append({"id": sid, "lineId": L["id"], "code": code,
                             "name": name,
                             "lat": lat, "lon": lon, "group": g})
            ids.append(sid)

        def d(x):
            return {"id": x["id"], "name": {"ja": x["ja"], "ko": x["ko"], "en": x["en"], "kana": ""}}
        lines.append({"id": L["id"], "operator": L["operator"], "code": L["code"],
                      "name": {**L["name"], "kana": ""}, "color": L["color"], "stations": ids,
                      "directions": {"asc": d(L["asc"]), "desc": d(L["desc"])}, "loop": bool(L.get("loop", False)), "source": L.get("source", "")})
        if L.get("extraEdges"):
            lines[-1]["extraEdges"] = [[en2sid[a], en2sid[b]] for a, b in L["extraEdges"]]
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
    # 이름은 다르지만 가까운 역(예: 우에노오카치마치–나카오카치마치) → 도보 환승 자동 생성
    radius = seed.get("autoTransferRadiusM", 0)
    if radius:
        explicit = {frozenset((a, b)) for a, b, _ in seed.get("extraTransfers", [])}
        glines = {}
        for st in stations:
            glines.setdefault(st["group"], set()).add(st["lineId"])
        gs = sorted(by_group)
        for i, ga in enumerate(gs):
            for gb in gs[i + 1:]:
                if glines[ga] & glines[gb] or frozenset((ga, gb)) in explicit:
                    continue
                dm = dist_m(group_pos[ga], group_pos[gb])
                if dm <= radius:
                    sec = max(walk, int(round(60 + dm / 1.2)))
                    for a in by_group[ga]:
                        for b in by_group[gb]:
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
