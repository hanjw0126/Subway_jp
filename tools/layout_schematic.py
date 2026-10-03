"""network.json → layout.json : 역 좌표로 8방향(octilinear) 도식 노선도를 자동 생성

1) 환승역 묶음(group)을 하나의 노드로 보고 위경도를 평면 좌표로 투영
2) 스프링 완화: 각 구간을 가장 가까운 45° 방향 + 균일 길이로 당기고, 원래 위치로 약하게 복원, 가까운 노드는 밀어냄
3) 0.5 격자에 스냅 후 국소 탐색으로 45° 정렬 구간 수를 최대화
4) 45°가 아닌 구간은 꺾임점 1개로 분해 (대각선 + 직선)
5) 여러 노선이 공유하는 구간은 평행 오프셋, 역명 라벨은 겹침이 최소인 방향에 배치
"""
import argparse
import json
import math
import os
from collections import defaultdict

UNIT = 60.0
GRID = 0.5
MARGIN = 2.0
# 복잡 구간 완화: 반경 CROWD_RADIUS 안에 역이 CROWD_START 개 넘게 몰린 곳은
# 구간 목표 길이를 최대 +CROWD_EXTRA, 비인접 역 최소 간격을 최대 +CROWD_REPEL 만큼 늘린다.
CROWD_RADIUS = 1.6
CROWD_START = 4
CROWD_SPAN = 8
CROWD_EXTRA = 0.3
CROWD_REPEL = 0.25
_REPEL = {}


def _crowding(geo_n, r=CROWD_RADIUS):
    items = list(geo_n.items())
    return {g: sum(1 for h, (u, v) in items if h != g and (u - x) ** 2 + (v - y) ** 2 < r * r) for g, (x, y) in items}


def _is_oct(dx, dy, eps=1e-6):
    return abs(dx) < eps or abs(dy) < eps or abs(abs(dx) - abs(dy)) < eps


def project(net):
    groups = defaultdict(list)
    for s in net["stations"]:
        groups[s["group"]].append(s)
    lat0 = sum(s["lat"] for s in net["stations"]) / len(net["stations"])
    lon0 = sum(s["lon"] for s in net["stations"]) / len(net["stations"])
    geo = {}
    for g, ss in groups.items():
        lat = sum(s["lat"] for s in ss) / len(ss)
        lon = sum(s["lon"] for s in ss) / len(ss)
        geo[g] = ((lon - lon0) * math.cos(math.radians(lat0)) * 111.32, -(lat - lat0) * 110.57)
    return groups, geo


def edges_of(net):
    sid2g = {s["id"]: s["group"] for s in net["stations"]}
    edges = {}
    for line in net["lines"]:
        gs = [sid2g[s] for s in line["stations"]]
        if line.get("loop"):
            gs = gs + gs[:1]
        pairs = list(zip(gs, gs[1:])) + [(sid2g[a], sid2g[b]) for a, b in line.get("extraEdges", [])]
        for a, b in pairs:
            if a == b:
                continue
            k = (a, b) if a < b else (b, a)
            edges.setdefault(k, [])
            if line["id"] not in edges[k]:
                edges[k].append(line["id"])
    return edges


def relax(geo, edges, iters=700):
    lens = sorted(math.dist(geo[a], geo[b]) for a, b in edges)
    med = lens[len(lens) // 2]
    geo_n = {g: (x / med, y / med) for g, (x, y) in geo.items()}
    crowd = _crowding(geo_n)

    def spread(g):
        return min(1.0, max(0.0, (crowd[g] - CROWD_START) / CROWD_SPAN))

    target = {e: (1.0 if math.dist(geo_n[e[0]], geo_n[e[1]]) < 1.8 else 1.5) * (1 + CROWD_EXTRA * max(spread(e[0]), spread(e[1])))
              for e in edges}
    repel = {g: 1.0 + CROWD_REPEL * spread(g) for g in geo_n}
    _REPEL.clear()
    _REPEL.update(repel)
    pos = dict(geo_n)
    nodes = sorted(pos)
    adj = set(edges) | {(b, a) for a, b in edges}
    deg = defaultdict(int)
    for a, b in edges:
        deg[a] += 1
        deg[b] += 1
    for it in range(iters):
        alpha = 0.6 * (1 - it / iters) + 0.05
        disp = {g: [0.0, 0.0] for g in nodes}
        for (a, b) in edges:
            vx, vy = pos[b][0] - pos[a][0], pos[b][1] - pos[a][1]
            k = round(math.atan2(vy, vx) / (math.pi / 4))
            L = target[(a, b)]
            ex, ey = math.cos(k * math.pi / 4) * L - vx, math.sin(k * math.pi / 4) * L - vy
            disp[a][0] -= ex / 2 / deg[a]
            disp[a][1] -= ey / 2 / deg[a]
            disp[b][0] += ex / 2 / deg[b]
            disp[b][1] += ey / 2 / deg[b]
        for g in nodes:
            disp[g][0] += 0.02 * (geo_n[g][0] - pos[g][0])
            disp[g][1] += 0.02 * (geo_n[g][1] - pos[g][1])
        for i in range(len(nodes)):
            a = nodes[i]
            for j in range(i + 1, len(nodes)):
                b = nodes[j]
                if (a, b) in adj:
                    continue
                dx, dy = pos[b][0] - pos[a][0], pos[b][1] - pos[a][1]
                d = math.hypot(dx, dy)
                rr = max(repel[a], repel[b])
                if d < rr:
                    if d < 1e-6:
                        dx, dy, d = 0.01, 0.0, 0.01
                    f = (rr - d) / 2 / d
                    disp[a][0] -= dx * f
                    disp[a][1] -= dy * f
                    disp[b][0] += dx * f
                    disp[b][1] += dy * f
        for g in nodes:
            pos[g] = (pos[g][0] + alpha * disp[g][0], pos[g][1] + alpha * disp[g][1])
    return pos, geo_n, target


def snap(pos, edges):
    deg = defaultdict(int)
    for a, b in edges:
        deg[a] += 1
        deg[b] += 1
    taken, out = {}, {}
    for g in sorted(pos, key=lambda g: (-deg[g], g)):
        x, y = pos[g]
        cx, cy = round(x / GRID), round(y / GRID)
        best = None
        for r in range(0, 8):
            cands = [(cx + dx, cy + dy) for dx in range(-r, r + 1) for dy in range(-r, r + 1)
                     if max(abs(dx), abs(dy)) == r and (cx + dx, cy + dy) not in taken]
            if cands:
                best = min(cands, key=lambda c: (c[0] * GRID - x) ** 2 + (c[1] * GRID - y) ** 2)
                break
        taken[best] = g
        out[g] = (best[0] * GRID, best[1] * GRID)
    return out


def refine(pos, geo_n, edges, target, passes=25):
    nbrs = defaultdict(list)
    for (a, b) in edges:
        nbrs[a].append((b, target[(a, b)]))
        nbrs[b].append((a, target[(a, b)]))
    taken = {(round(x / GRID), round(y / GRID)): g for g, (x, y) in pos.items()}

    def cost(g, p):
        c = 0.0
        near = {n for n, _ in nbrs[g]}
        for n, L in nbrs[g]:
            dx, dy = pos[n][0] - p[0], pos[n][1] - p[1]
            if not _is_oct(dx, dy):
                c += 1.0
            d = math.hypot(dx, dy)
            c += 0.35 * abs(d - L)
            if d < 0.99:
                c += 3.0
        for h, q in pos.items():
            if h != g and h not in near and math.dist(p, q) < 0.99 * max(_REPEL.get(g, 1.0), _REPEL.get(h, 1.0)):
                c += 1.5
        return c + 0.08 * math.dist(p, geo_n[g])

    for _ in range(passes):
        moved = 0
        for g in sorted(pos):
            cx, cy = round(pos[g][0] / GRID), round(pos[g][1] / GRID)
            best_c, best_cell = cost(g, pos[g]), (cx, cy)
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    cell = (cx + dx, cy + dy)
                    if cell == (cx, cy) or cell in taken:
                        continue
                    c = cost(g, (cell[0] * GRID, cell[1] * GRID))
                    if c < best_c - 1e-6:
                        best_c, best_cell = c, cell
            if best_cell != (cx, cy):
                del taken[(cx, cy)]
                taken[best_cell] = g
                pos[g] = (best_cell[0] * GRID, best_cell[1] * GRID)
                moved += 1
        if not moved:
            break
    return pos


def route_edge(a, b, pos):
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    if _is_oct(dx, dy):
        return [a, b]
    d = min(abs(dx), abs(dy))
    sx, sy = math.copysign(1, dx), math.copysign(1, dy)
    opts = [(ax + sx * d, ay + sy * d), (bx - sx * d, by - sy * d)]
    others = [p for p in pos.values() if p != a and p != b]
    return [a, max(opts, key=lambda o: min([math.dist(o, p) for p in others] or [9])), b]


def place_labels(pos, edges, names, polylines):
    dirs = [(1, 0), (-1, 0), (0, -1), (0, 1), (1, -1), (1, 1), (-1, -1), (-1, 1)]
    pref = {(1, 0): 0.0, (-1, 0): 0.15, (0, -1): 0.4, (0, 1): 0.4}
    inc = defaultdict(list)
    for (a, b) in edges:
        for u, v in ((a, b), (b, a)):
            dx, dy = pos[v][0] - pos[u][0], pos[v][1] - pos[u][1]
            n = math.hypot(dx, dy) or 1
            inc[u].append((dx / n, dy / n))
    samples = []
    for pts in polylines:
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            n = max(1, int(math.dist((x1, y1), (x2, y2)) / 0.2))
            samples += [(x1 + (x2 - x1) * t / n, y1 + (y2 - y1) * t / n) for t in range(n + 1)]
    placed, out = [], {}

    def box(p, d, w, h, gap=0.22):
        x, y = p
        left = x + gap if d[0] > 0 else (x - gap - w if d[0] < 0 else x - w / 2)
        top = y + gap if d[1] > 0 else (y - gap - h if d[1] < 0 else y - h / 2)
        return (left, top, left + w, top + h)

    def inter(r1, r2):
        return max(0, min(r1[2], r2[2]) - max(r1[0], r2[0])) * max(0, min(r1[3], r2[3]) - max(r1[1], r2[1]))

    for g in sorted(pos, key=lambda g: (-len(inc[g]), g)):
        w, h = 0.23 * len(names[g]) + 0.1, 0.3
        best = None
        for d in dirs:
            r = box(pos[g], d, w, h)
            n = math.hypot(*d)
            s = pref.get(d, 0.6)
            s += sum(3.0 for u in inc[g] if (d[0] * u[0] + d[1] * u[1]) / n > 0.6)
            s += sum(10 * inter(r, q) for q in placed)
            s += sum(0.6 for (x, y) in samples if r[0] < x < r[2] and r[1] < y < r[3])
            s += sum(4.0 for h2, q in pos.items() if h2 != g and r[0] - 0.12 < q[0] < r[2] + 0.12 and r[1] - 0.12 < q[1] < r[3] + 0.12)
            if best is None or s < best[0]:
                best = (s, d, r)
        placed.append(best[2])
        out[g] = best[1]
    return out


def build_layout(net):
    groups, geo = project(net)
    edges = edges_of(net)
    pos, geo_n, target = relax(geo, edges)
    pos = snap(pos, edges)
    pos = refine(pos, geo_n, edges, target)
    minx = min(x for x, _ in pos.values()) - MARGIN
    miny = min(y for _, y in pos.values()) - MARGIN
    pos = {g: (x - minx, y - miny) for g, (x, y) in pos.items()}
    maxx = max(x for x, _ in pos.values()) + MARGIN
    maxy = max(y for _, y in pos.values()) + MARGIN
    line_order = [l["id"] for l in net["lines"]]
    routed = {e: route_edge(pos[e[0]], pos[e[1]], pos) for e in edges}
    names = {g: ss[0]["name"]["ko"] for g, ss in groups.items()}
    labels = place_labels(pos, edges, names, list(routed.values()))
    lines_out = []
    for line in net["lines"]:
        segs = []
        for e, lids in edges.items():
            if line["id"] not in lids:
                continue
            ordered = sorted(lids, key=line_order.index)
            off = ordered.index(line["id"]) - (len(ordered) - 1) / 2
            segs.append({"points": [[round(x * UNIT, 2), round(y * UNIT, 2)] for x, y in routed[e]], "offset": off})
        lines_out.append({"lineId": line["id"], "color": line["color"], "segments": segs})
    nodes = []
    for g in sorted(pos):
        ss = groups[g]
        nodes.append({"group": g, "x": round(pos[g][0] * UNIT, 2), "y": round(pos[g][1] * UNIT, 2),
                      "labelKo": ss[0]["name"]["ko"], "labelJa": ss[0]["name"]["ja"],
                      "labelDx": labels[g][0], "labelDy": labels[g][1],
                      "interchange": len({s["lineId"] for s in ss}) > 1, "stationIds": [s["id"] for s in ss]})
    return {"schemaVersion": 1, "regionId": net["regionId"], "unit": UNIT,
            "width": round(maxx * UNIT, 2), "height": round(maxy * UNIT, 2), "nodes": nodes, "lines": lines_out}


def metrics(layout):
    segs, bends = [], 0
    for l in layout["lines"]:
        for s in l["segments"]:
            pts = s["points"]
            bends += len(pts) - 2
            segs += list(zip(pts, pts[1:]))
    octo = all(_is_oct(b[0] - a[0], b[1] - a[1], 1e-3) for a, b in segs)
    uniq = {}
    for a, b in segs:
        uniq[tuple(sorted((tuple(a), tuple(b))))] = (a, b)
    segs = list(uniq.values())

    def ccw(a, b, c):
        return (c[1] - a[1]) * (b[0] - a[0]) - (b[1] - a[1]) * (c[0] - a[0])

    cross = 0
    for i in range(len(segs)):
        for j in range(i + 1, len(segs)):
            (p1, p2), (p3, p4) = segs[i], segs[j]
            if {tuple(p1), tuple(p2)} & {tuple(p3), tuple(p4)}:
                continue
            if ccw(p1, p2, p3) * ccw(p1, p2, p4) < 0 and ccw(p3, p4, p1) * ccw(p3, p4, p2) < 0:
                cross += 1
    pts = [(n["x"], n["y"]) for n in layout["nodes"]]
    mind = min(math.dist(pts[i], pts[j]) for i in range(len(pts)) for j in range(i + 1, len(pts))) / layout["unit"]
    return {"nodes": len(pts), "bends": bends, "allOctilinear": octo, "crossings": cross, "minNodeDist": round(mind, 2)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("network")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    with open(a.network, encoding="utf-8") as f:
        net = json.load(f)
    lay = build_layout(net)
    out = a.out or os.path.join(os.path.dirname(a.network), "layout.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(lay, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"[layout] {net['regionId']}: {metrics(lay)}")


if __name__ == "__main__":
    main()
