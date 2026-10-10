"""지리 좌표의 강 중심선을 도식 노선도 좌표로 옮겨 layout.json 에 rivers 로 넣는다.

노선도는 실제 지도를 크게 변형한 것이라 하나의 변환식으로 옮길 수 없다.
강의 각 점마다 가까운 역 K곳의 '실제 위치 → 노선도 위치'를 거리 가중 평균해 옮긴다
(각 역에서 강 점까지의 실제 변위를 노선도 축척으로 더함). 그래서 역과 강의 남북 관계가 대체로 유지된다.
"""
import json
import math
import os

K = 6
WIDTH_UNITS = 0.55  # 강 폭 (역 간격 배수)


def _proj(lat, lon, lat0, lon0):
    return ((lon - lon0) * math.cos(math.radians(lat0)) * 111.32, -(lat - lat0) * 110.57)


def _smooth(pts, win=1):
    if len(pts) < 3:
        return pts
    out = []
    for i in range(len(pts)):
        lo, hi = max(0, i - win), min(len(pts), i + win + 1)
        xs = [p[0] for p in pts[lo:hi]]
        ys = [p[1] for p in pts[lo:hi]]
        out.append((sum(xs) / len(xs), sum(ys) / len(ys)))
    return out


def warp(layout, network, latlon):
    """[[위도, 경도], ...] → 노선도 좌표 [(x, y), ...]"""
    st = network["stations"]
    lat0 = sum(s["lat"] for s in st) / len(st)
    lon0 = sum(s["lon"] for s in st) / len(st)
    acc = {}
    for s in st:
        a = acc.setdefault(s["group"], [0.0, 0.0, 0])
        a[0] += s["lat"]
        a[1] += s["lon"]
        a[2] += 1
    nodes = []
    for n in layout["nodes"]:
        a = acc.get(n["group"])
        if a:
            nodes.append((_proj(a[0] / a[2], a[1] / a[2], lat0, lon0), (n["x"], n["y"])))
    if len(nodes) < 2:
        return []
    # 노선도 축척: 각 역과 실제로 가장 가까운 역 사이의 (노선도 거리 / 실제 거리) 중앙값
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
        p = _proj(lat, lon, lat0, lon0)
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
