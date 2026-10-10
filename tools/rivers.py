"""지리 좌표의 강 중심선을 도식 노선도 좌표로 옮겨 layout.json 에 rivers 로 넣는다.

노선도는 실제 지도를 크게 변형한 것이라 하나의 변환식으로 옮길 수 없다.
1) 노선도 생성과 같은 투영(geo_projection: 방사 압축 포함)으로 강과 역을 평면에 옮기고,
2) 강의 각 점마다 가까운 역 K곳의 '평면 위치 → 노선도 위치'를 거리 가중 평균해 옮긴다.
가장 가까운 역에서 TRIM_KM 보다 먼 강 양 끝은 잘라 낸다 (역이 드문 외곽에서 크게 튀는 것 방지).
마지막에 이동 평균(SMOOTH_WIN)으로 노선도 격자 때문에 생긴 굴곡을 펴 준다.
"""
import json
import math
import os

import geo_projection

K = 6
WIDTH_UNITS = 0.55  # 강 폭 (역 간격 배수)
TRIM_KM = 3.0
SMOOTH_WIN = 3  # 앞뒤 점 개수 (점 간격 약 350 m)


def _km(la1, lo1, la2, lo2):
    dx = (lo2 - lo1) * math.cos(math.radians((la1 + la2) / 2)) * 111.32
    dy = (la2 - la1) * 110.57
    return math.hypot(dx, dy)


def _smooth(pts, win=SMOOTH_WIN):
    if len(pts) < 3:
        return pts
    out = []
    for i in range(len(pts)):
        lo, hi = max(0, i - win), min(len(pts), i + win + 1)
        out.append((sum(p[0] for p in pts[lo:hi]) / (hi - lo), sum(p[1] for p in pts[lo:hi]) / (hi - lo)))
    return out


def warp(layout, network, latlon):
    """[[위도, 경도], ...] → 노선도 좌표 [(x, y), ...] (양 끝 잘라냄)"""
    groups, geo = geo_projection.project(network)
    proj = geo_projection.projection(network)
    st = network["stations"]
    # 실제 거리로 강 양 끝 자르기
    near_km = [min(_km(la, lo, s["lat"], s["lon"]) for s in st) for la, lo in latlon]
    idx = [i for i, d in enumerate(near_km) if d <= TRIM_KM]
    if len(idx) < 2:
        return []
    latlon = latlon[idx[0]: idx[-1] + 1]
    nodes = [(geo[n["group"]], (n["x"], n["y"])) for n in layout["nodes"] if n["group"] in geo]
    if len(nodes) < 2:
        return []
    # 노선도 축척: 각 역과 평면상 가장 가까운 역 사이의 (노선도 거리 / 평면 거리) 중앙값
    ratios = []
    for i, (g, l) in enumerate(nodes):
        j = min((k for k in range(len(nodes)) if k != i), key=lambda k: math.dist(g, nodes[k][0]))
        dg = math.dist(g, nodes[j][0])
        if dg > 1e-6:
            ratios.append(math.dist(l, nodes[j][1]) / dg)
    ratios.sort()
    scale = ratios[len(ratios) // 2]
    out = []
    for lat, lon in latlon:
        p = geo_projection.to_xy(lat, lon, proj)
        near = sorted(nodes, key=lambda nd: math.dist(p, nd[0]))[:K]
        sw = sx = sy = 0.0
        for g, l in near:
            w = 1.0 / (math.dist(p, g) ** 2 + 0.05)
            sw += w
            sx += w * (l[0] + scale * (p[0] - g[0]))
            sy += w * (l[1] + scale * (p[1] - g[1]))
        out.append((sx / sw, sy / sw))
    return _smooth(out)


def attach(region_dir, river_files):
    lp = os.path.join(region_dir, "layout.json")
    with open(lp, encoding="utf-8") as f:
        layout = json.load(f)
    with open(os.path.join(region_dir, "network.json"), encoding="utf-8") as f:
        network = json.load(f)
    W, H = layout["width"], layout["height"]
    rivers = []
    for path in river_files:
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            r = json.load(f)
        pts = [(x, y) for x, y in warp(layout, network, r["points"])
               if -0.02 * W <= x <= 1.02 * W and -0.02 * H <= y <= 1.02 * H]
        if len(pts) < 2:
            continue
        rivers.append({"id": r.get("id", ""), "name": r.get("name", {}),
                       "width": round(layout["unit"] * WIDTH_UNITS, 2),
                       "points": [[round(x, 2), round(y, 2)] for x, y in pts]})
    layout["rivers"] = rivers
    with open(lp, "w", encoding="utf-8") as f:
        json.dump(layout, f, ensure_ascii=False, indent=1)
        f.write("\n")
    if rivers:
        print(f"[rivers] {layout['regionId']}: " + ", ".join(f"{r['id']}({len(r['points'])}점)" for r in rivers))
