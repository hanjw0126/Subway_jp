"""지역 간 환승 표시: 다른 지역 노선이 이 지역의 역(300m 이내)을 지나가면,
그 노선의 앞뒤 최대 2역 구간을 'ghosts' 로 layout.json 에 넣는다 (앱에서 반투명으로 그림)"""
import json
import math
import os

from layout_schematic import UNIT, classify


def _dist_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a["lat"], a["lon"], b["lat"], b["lon"]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def _step(prev, nxt):
    ang = math.atan2(-(nxt["lat"] - prev["lat"]) * 110.57, (nxt["lon"] - prev["lon"]) * math.cos(math.radians(prev["lat"])) * 111.32)
    k = round(ang / (math.pi / 4))
    return math.cos(k * math.pi / 4) * UNIT, math.sin(k * math.pi / 4) * UNIT


def ghosts_for(net_a, lay_a, net_b, region_b, radius_m=300, steps=2):
    node_by_group = {n["group"]: n for n in lay_a["nodes"]}
    lines_a = {l["id"] for l in net_a["lines"]}
    st_b = {s["id"]: s for s in net_b["stations"]}
    line_b = {l["id"]: l for l in net_b["lines"]}
    out, seen = [], set()
    for sa in net_a["stations"]:
        node = node_by_group.get(sa["group"])
        if node is None:
            continue
        for sb in net_b["stations"]:
            if sb["lineId"] in lines_a:
                continue
            d = _dist_m(sa, sb)
            if d > radius_m and not (d < 600 and sa["name"].get("en") == sb["name"].get("en")):
                continue
            line = line_b[sb["lineId"]]
            i = line["stations"].index(sb["id"])
            cat, _ = classify(line.get("operator", ""), line["id"])
            for direction in (-1, 1):
                pts, prev = [[node["x"], node["y"]]], sb
                for k in range(1, steps + 1):
                    j = i + direction * k
                    if j < 0 or j >= len(line["stations"]):
                        break
                    nxt = st_b[line["stations"][j]]
                    dx, dy = _step(prev, nxt)
                    pts.append([round(pts[-1][0] + dx, 2), round(pts[-1][1] + dy, 2)])
                    prev = nxt
                key = (line["id"], tuple(map(tuple, pts)))
                if len(pts) >= 2 and key not in seen:
                    seen.add(key)
                    out.append({"lineId": line["id"], "color": line["color"], "regionId": region_b,
                                "code": line.get("code", ""), "category": cat, "points": pts})
    return out


def add_ghosts(assets, region_ids):
    nets, lays = {}, {}
    for r in region_ids:
        with open(os.path.join(assets, r, "network.json"), encoding="utf-8") as f:
            nets[r] = json.load(f)
        with open(os.path.join(assets, r, "layout.json"), encoding="utf-8") as f:
            lays[r] = json.load(f)
    for ra in region_ids:
        ghosts = []
        for rb in region_ids:
            if rb != ra:
                ghosts += ghosts_for(nets[ra], lays[ra], nets[rb], rb)
        lays[ra]["ghosts"] = ghosts
        with open(os.path.join(assets, ra, "layout.json"), "w", encoding="utf-8") as f:
            json.dump(lays[ra], f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"[cross-region] {ra}: ghosts={len(ghosts)}")
