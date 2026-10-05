"""network.json → layout.json : 역 좌표로 8방향(octilinear) 도식 노선도를 자동 생성

1) 환승역 묶음(group)을 하나의 노드로 보고 위경도를 평면 좌표로 투영 (떨어진 노선 묶음은 본 노선망 가까이로 이동)
2) 스프링 완화 → 0.5 격자 스냅 → 국소 탐색으로 45° 정렬 구간 최대화
3) 충돌 복구(repair): '정차하지 않는 역 위를 지나감', '다른 구간과 포개짐', '교차' 를 비용으로 두고
   문제가 된 역을 주변 격자 칸으로 옮긴다. 45°가 아닌 구간의 꺾임 방향도 충돌이 적은 쪽을 고른다.
4) 평행 오프셋, 역명 방향, 종점 아이콘 위치, 노선 분류(선 디자인 / 필터 그룹)를 함께 출력
"""
import argparse
import json
import math
import os
import time
from collections import defaultdict

UNIT = 60.0
GRID = 0.5
MARGIN = 2.5
# 복잡 구간 완화
CROWD_RADIUS = 1.6
CROWD_START = 4
CROWD_SPAN = 8
CROWD_EXTRA = 0.3
CROWD_REPEL = 0.25
# 떨어진 노선 묶음(예: 다마 모노레일)을 본 노선망에서 이 거리(km)까지 당긴다
DETACHED_GAP_KM = 2.5
# 충돌 복구 비용
W_ON_EDGE = 60.0
W_OVERLAP = 60.0
W_CROSS = 14.0
W_CLOSE = 30.0
W_BEND = 1.0
ON_EDGE_DIST = 0.2
REPAIR_BUDGET_S = float(os.environ.get("LAYOUT_REPAIR_BUDGET", "900"))
ESCAPE_PASSES = int(os.environ.get("LAYOUT_ESCAPE_PASSES", "12"))
_REPEL = {}

SUBWAY_OPERATORS = {"TokyoMetro", "Toei", "YokohamaMunicipal", "OsakaMetro"}
MONORAIL_OPERATORS = {"TamaMonorail", "TokyoMonorail", "Yurikamome", "ShonanMonorail", "ChibaUrbanMonorail"}


def classify(operator, line_id):
    """노선 분류 → (선 디자인 category, 필터 그룹). 필터 그룹: subway / jr / private"""
    op = operator.split(":", 1)[-1]
    path = line_id.split(":", 1)[-1]
    if op.startswith("JR"):
        return "jr", "jr"
    if op == "Toei":
        if path.endswith(".Arakawa"):
            return "tram", "subway"
        if path.endswith(".NipporiToneri"):
            return "monorail", "subway"
        return "toei", "subway"
    if op in SUBWAY_OPERATORS:
        return "metro", "subway"
    if op in MONORAIL_OPERATORS:
        return "monorail", "private"
    return "private", "private"


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


def _components(nodes, edges):
    adj = defaultdict(set)
    for a, b in edges:
        adj[a].add(b)
        adj[b].add(a)
    seen, comps = set(), []
    for n in sorted(nodes):
        if n in seen:
            continue
        seen.add(n)
        stack, comp = [n], []
        while stack:
            u = stack.pop()
            comp.append(u)
            for v in adj[u]:
                if v not in seen:
                    seen.add(v)
                    stack.append(v)
        comps.append(comp)
    return sorted(comps, key=len, reverse=True)


def pull_detached(geo, edges, gap=DETACHED_GAP_KM):
    """본 노선망과 연결되지 않은 노선 묶음을 본 노선망 쪽으로 평행 이동해 빈 공간을 줄인다"""
    comps = _components(geo, edges)
    if len(comps) < 2:
        return geo
    geo = dict(geo)
    main = list(comps[0])
    for comp in comps[1:]:
        cm = (sum(geo[g][0] for g in main) / len(main), sum(geo[g][1] for g in main) / len(main))
        cc = (sum(geo[g][0] for g in comp) / len(comp), sum(geo[g][1] for g in comp) / len(comp))
        vx, vy = cc[0] - cm[0], cc[1] - cm[1]
        d = math.hypot(vx, vy) or 1.0
        ux, uy = vx / d, vy / d

        def mind(t):
            return min(math.dist((geo[a][0] - ux * t, geo[a][1] - uy * t), geo[b]) for a in comp for b in main)

        if mind(0.0) > gap:
            lo, hi = 0.0, d
            for _ in range(30):
                mid = (lo + hi) / 2
                if mind(mid) >= gap:
                    lo = mid
                else:
                    hi = mid
            for a in comp:
                geo[a] = (geo[a][0] - ux * lo, geo[a][1] - uy * lo)
        main += comp
    return geo


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
        # 격자 해시로 가까운 쌍만 계산 (정렬해서 기존 i<j 순서와 동일한 결과)
        rmax = max(repel.values())
        cells = defaultdict(list)
        for g in nodes:
            cells[(math.floor(pos[g][0] / rmax), math.floor(pos[g][1] / rmax))].append(g)
        pairs = []
        for (cx, cy), members in cells.items():
            near = [h for ddx in (-1, 0, 1) for ddy in (-1, 0, 1) for h in cells.get((cx + ddx, cy + ddy), ())]
            for a in members:
                for b in near:
                    if b > a:
                        pairs.append((a, b))
        for a, b in sorted(pairs):
            if True:
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


def _cell(p):
    return (round(p[0] / GRID), round(p[1] / GRID))


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
    taken = {_cell(p): g for g, p in pos.items()}

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
            cx, cy = _cell(pos[g])
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


# ---------------------------------------------------------------- 기하 판정

def _seg_point_dist(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    if L2 < 1e-12:
        return math.dist(p, a)
    t = max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / L2))
    return math.hypot(ax + t * dx - p[0], ay + t * dy - p[1])


def _ccw(a, b, c):
    return (c[1] - a[1]) * (b[0] - a[0]) - (b[1] - a[1]) * (c[0] - a[0])


def _cross(s1, s2):
    (p1, p2), (p3, p4) = s1, s2
    return _ccw(p3, p4, p1) * _ccw(p3, p4, p2) < -1e-9 and _ccw(p1, p2, p3) * _ccw(p1, p2, p4) < -1e-9


def _overlap(s1, s2, eps=0.05):
    """두 선분이 같은 직선 위에서 eps 보다 길게 겹치는가 (서로 다른 구간이 포개짐)"""
    (a, b), (c, d) = s1, s2
    ux, uy = b[0] - a[0], b[1] - a[1]
    vx, vy = d[0] - c[0], d[1] - c[1]
    L, M = math.hypot(ux, uy), math.hypot(vx, vy)
    if L < 1e-9 or M < 1e-9:
        return False
    if abs(ux * vy - uy * vx) / (L * M) > 1e-3:
        return False
    if abs((c[0] - a[0]) * uy - (c[1] - a[1]) * ux) / L > eps:
        return False
    t1 = ((c[0] - a[0]) * ux + (c[1] - a[1]) * uy) / L
    t2 = ((d[0] - a[0]) * ux + (d[1] - a[1]) * uy) / L
    return min(max(t1, t2), L) - max(min(t1, t2), 0.0) > eps


def _bbox(pts, pad=0.0):
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return (min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad)


def _bbhit(a, b):
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


def _seg_conflicts(pts, q):
    ov = cr = 0
    for s in zip(pts, pts[1:]):
        for t in zip(q, q[1:]):
            if _overlap(s, t):
                ov += 1
            elif _cross(s, t):
                cr += 1
    return ov, cr


def _on_path(p, q, d=ON_EDGE_DIST):
    return any(_seg_point_dist(p, a, b) < d for a, b in zip(q, q[1:]))


def route_options(a, b):
    """두 역을 잇는 8방향 경로 후보 (45°가 아니면 꺾임점 1개, 대각선 먼저/나중 두 가지)"""
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    if _is_oct(dx, dy):
        return [[a, b]]
    d = min(abs(dx), abs(dy))
    sx, sy = math.copysign(1, dx), math.copysign(1, dy)
    return [[a, (ax + sx * d, ay + sy * d), b], [a, (bx - sx * d, by - sy * d), b]]


def _score(on, ov, cr):
    return W_ON_EDGE * on + W_OVERLAP * ov + W_CROSS * cr


def _conf(e, pts, paths, boxes, pos, skip):
    on = ov = cr = 0
    bb = _bbox(pts, 0.3)
    for f, q in paths.items():
        if f == e or f in skip or not _bbhit(bb, boxes[f]):
            continue
        o, c = _seg_conflicts(pts, q)
        ov += o
        cr += c
    for h, p in pos.items():
        if h in e:
            continue
        if bb[0] <= p[0] <= bb[2] and bb[1] <= p[1] <= bb[3] and _on_path(p, pts):
            on += 1
    return on, ov, cr


def violations(paths, pos):
    """(지표, 정차하지 않는 역 통과·포개짐 관련 역, 교차 관련 역)"""
    items = list(paths.items())
    boxes = [_bbox(q, 0.05) for _, q in items]
    on = ov = cr = 0
    hard, soft = set(), set()
    for i, (e, q) in enumerate(items):
        for j in range(i + 1, len(items)):
            if not _bbhit(boxes[i], boxes[j]):
                continue
            f, r = items[j]
            o, x = _seg_conflicts(q, r)
            if o:
                ov += o
                hard.update(e)
                hard.update(f)
            if x:
                cr += x
                soft.update(e)
                soft.update(f)
        bb = _bbox(q, ON_EDGE_DIST)
        for h, p in pos.items():
            if h in e:
                continue
            if bb[0] <= p[0] <= bb[2] and bb[1] <= p[1] <= bb[3] and _on_path(p, q):
                on += 1
                hard.add(h)
                hard.update(e)
    return {"nodeOnEdge": on, "overlaps": ov, "crossings": cr}, hard, soft


def _node_eval(g, p, pos, inc, nbrs, paths, boxes, geo_n, target):
    old = pos[g]
    pos[g] = p
    skip = set(inc[g])
    local = {}
    c = 0.0
    for e in inc[g]:
        best = None
        for pts in route_options(pos[e[0]], pos[e[1]]):
            on, ov, cr = _conf(e, pts, paths, boxes, pos, skip)
            for q in local.values():
                o, x = _seg_conflicts(pts, q)
                ov += o
                cr += x
            s = _score(on, ov, cr) + (W_BEND if len(pts) > 2 else 0.0)
            if best is None or s < best[0]:
                best = (s, pts)
        local[e] = best[1]
        c += best[0]
        d = math.dist(pos[e[0]], pos[e[1]])
        c += 0.35 * abs(d - target[e])
        if d < 0.99:
            c += W_CLOSE
    for f, q in paths.items():
        if f in skip:
            continue
        b = boxes[f]
        if b[0] - 0.2 <= p[0] <= b[2] + 0.2 and b[1] - 0.2 <= p[1] <= b[3] + 0.2 and _on_path(p, q):
            c += W_ON_EDGE
    rg = _REPEL.get(g, 1.0)
    for h, q in pos.items():
        if h != g and h not in nbrs[g] and math.dist(p, q) < 0.99 * max(rg, _REPEL.get(h, 1.0)):
            c += W_CLOSE / 3
    c += 0.08 * math.dist(p, geo_n[g])
    pos[g] = old
    return c, local


def repair(pos, geo_n, edges, target, passes=int(os.environ.get("LAYOUT_REPAIR_PASSES", "48")), budget_s=REPAIR_BUDGET_S):
    """충돌 복구. pos 를 직접 고치고 구간별 경로(꺾임점 포함)를 돌려준다"""
    t0 = time.monotonic()
    edges = list(edges)
    inc = defaultdict(list)
    nbrs = defaultdict(set)
    for e in edges:
        inc[e[0]].append(e)
        inc[e[1]].append(e)
        nbrs[e[0]].add(e[1])
        nbrs[e[1]].add(e[0])
    paths, boxes = {}, {}
    for _ in range(2):
        for e in edges:
            opts = route_options(pos[e[0]], pos[e[1]])
            best = min(opts, key=lambda pts: _score(*_conf(e, pts, paths, boxes, pos, ())))
            paths[e] = best
            boxes[e] = _bbox(best)
    taken = {_cell(p): g for g, p in pos.items()}
    for it in range(passes):
        _, hard, soft = violations(paths, pos)
        work = hard or soft
        if not work or time.monotonic() - t0 > budget_s:
            break
        radius = 2 if it < passes // 2 else 3
        moved = 0
        for g in sorted(work):
            if time.monotonic() - t0 > budget_s:
                break
            cur_c, cur_local = _node_eval(g, pos[g], pos, inc, nbrs, paths, boxes, geo_n, target)
            best = (cur_c, None, cur_local)
            cx, cy = _cell(pos[g])
            for dx in range(-radius, radius + 1):
                for dy in range(-radius, radius + 1):
                    cell = (cx + dx, cy + dy)
                    if cell in taken:
                        continue
                    c, local = _node_eval(g, (cell[0] * GRID, cell[1] * GRID), pos, inc, nbrs, paths, boxes, geo_n, target)
                    if c < best[0] - 1e-6:
                        best = (c, cell, local)
            if best[1] is not None:
                del taken[(cx, cy)]
                taken[best[1]] = g
                pos[g] = (best[1][0] * GRID, best[1][1] * GRID)
                moved += 1
            for e, q in best[2].items():
                paths[e] = q
                boxes[e] = _bbox(q)
        if not moved and radius == 3:
            break
    # 국소 최소에 갇힌 충돌: 충돌 역과 이웃 역을 더 넓은 범위(반경 4~6)에서 다시 찾는다
    for it in range(ESCAPE_PASSES):
        if time.monotonic() - t0 > budget_s:
            break
        _, hard, _ = violations(paths, pos)
        if not hard:
            break
        work = set(hard)
        for g in hard:
            work |= nbrs[g]
        radius = 4 + min(it // 3, 2)
        for g in sorted(work):
            if time.monotonic() - t0 > budget_s:
                break
            cur_c, cur_local = _node_eval(g, pos[g], pos, inc, nbrs, paths, boxes, geo_n, target)
            best = (cur_c, None, cur_local)
            cx, cy = _cell(pos[g])
            for dx in range(-radius, radius + 1):
                for dy in range(-radius, radius + 1):
                    cell = (cx + dx, cy + dy)
                    if cell in taken:
                        continue
                    c, local = _node_eval(g, (cell[0] * GRID, cell[1] * GRID), pos, inc, nbrs, paths, boxes, geo_n, target)
                    if c < best[0] - 1e-6:
                        best = (c, cell, local)
            if best[1] is not None:
                del taken[(cx, cy)]
                taken[best[1]] = g
                pos[g] = (best[1][0] * GRID, best[1][1] * GRID)
            for e, q in best[2].items():
                paths[e] = q
                boxes[e] = _bbox(q)
    return paths


def bundle_lanes(paths, edges, line_order):
    """같은 역에서 출발해 포개지는 서로 다른 구간을 평행선 다발로 묶어 노선별 오프셋을 정한다.

    8방향 노선도에서는 한 역에서 나갈 수 있는 방향이 8개뿐이라, 이웃 역이 9곳 이상인 역(오테마치 등)은
    포개짐을 피할 수 없다. 실제 노선도처럼 나란히 그려 구분한다. 반환: {(edge, lineId): offset}"""
    keys = list(paths)
    parent = {e: e for e in keys}

    def find(e):
        while parent[e] != e:
            parent[e] = parent[parent[e]]
            e = parent[e]
        return e
    inc = defaultdict(list)
    for e in keys:
        inc[e[0]].append(e)
        inc[e[1]].append(e)
    for g in sorted(inc, key=str):
        es = inc[g]
        for i in range(len(es)):
            for j in range(i + 1, len(es)):
                if _seg_conflicts(paths[es[i]], paths[es[j]])[0]:
                    parent[find(es[i])] = find(es[j])
    groups = defaultdict(list)
    for e in keys:
        groups[find(e)].append(e)
    rank = {l: i for i, l in enumerate(line_order)}
    lanes = {}
    for es in groups.values():
        es.sort(key=lambda e: (min(rank.get(l, 1 << 20) for l in edges[e]), str(e)))
        slots = [(e, l) for e in es for l in sorted(edges[e], key=lambda l: rank.get(l, 1 << 20))]
        n = len(slots)
        for k, (e, l) in enumerate(slots):
            lanes[(e, l)] = k - (n - 1) / 2
    return lanes


# ---------------------------------------------------------------- 라벨·아이콘

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


def terminal_icons(net, pos, paths, dist=0.75):
    """노선 양 끝(종점) 바깥쪽에 노선 아이콘 자리를 잡는다. 같은 색 노선이 이어지는 분기점(지선 끝)은 제외"""
    sid2g = {s["id"]: s["group"] for s in net["stations"]}
    at = defaultdict(set)
    for line in net["lines"]:
        for s in line["stations"]:
            at[sid2g[s]].add((line["id"], line["color"].upper()))
    allsegs = [s for q in paths.values() for s in zip(q, q[1:])]
    placed, out = [], {}
    for line in net["lines"]:
        out[line["id"]] = []
        if line.get("loop"):
            continue
        gs = [sid2g[s] for s in line["stations"]]
        adj = defaultdict(set)
        for a, b in zip(gs, gs[1:]):
            if a != b:
                adj[a].add(b)
                adj[b].add(a)
        for a, b in line.get("extraEdges", []):
            ga, gb = sid2g[a], sid2g[b]
            if ga != gb:
                adj[ga].add(gb)
                adj[gb].add(ga)
        color = line["color"].upper()
        for g in dict.fromkeys([gs[0], gs[-1]]):
            if len(adj[g]) != 1:
                continue
            if any(lid != line["id"] and col == color for lid, col in at[g]):
                continue
            n = next(iter(adj[g]))
            q = paths.get((g, n)) or paths.get((n, g))
            if q is None:
                continue
            prev = q[1] if q[0] == pos[g] else q[-2]
            ang = math.atan2(pos[g][1] - prev[1], pos[g][0] - prev[0])
            order = sorted(range(8), key=lambda k: abs(math.remainder(k * math.pi / 4 - ang, 2 * math.pi)))
            best = None
            for k in order:
                p = (pos[g][0] + math.cos(k * math.pi / 4) * dist, pos[g][1] + math.sin(k * math.pi / 4) * dist)
                if all(math.dist(p, q2) >= 0.5 for h, q2 in pos.items() if h != g) \
                        and all(math.dist(p, q2) >= 0.55 for q2 in placed) \
                        and all(_seg_point_dist(p, a, b) >= 0.3 for a, b in allsegs):
                    best = p
                    break
            if best is None:
                k = order[0]
                best = (pos[g][0] + math.cos(k * math.pi / 4) * dist, pos[g][1] + math.sin(k * math.pi / 4) * dist)
            placed.append(best)
            out[line["id"]].append(best)
    return out


def build_layout(net):
    groups, geo = project(net)
    edges = edges_of(net)
    geo = pull_detached(geo, edges)
    pos, geo_n, target = relax(geo, edges)
    pos = snap(pos, edges)
    pos = refine(pos, geo_n, edges, target)
    paths = repair(pos, geo_n, edges, target)
    icons = terminal_icons(net, pos, paths)
    pts_all = list(pos.values()) + [p for v in icons.values() for p in v]
    minx = min(x for x, _ in pts_all) - MARGIN
    miny = min(y for _, y in pts_all) - MARGIN
    maxx = max(x for x, _ in pts_all) - minx + MARGIN
    maxy = max(y for _, y in pts_all) - miny + MARGIN

    def sh(p):
        return (p[0] - minx, p[1] - miny)

    pos = {g: sh(p) for g, p in pos.items()}
    paths = {e: [sh(p) for p in q] for e, q in paths.items()}
    icons = {k: [sh(p) for p in v] for k, v in icons.items()}
    names = {g: ss[0]["name"]["ko"] for g, ss in groups.items()}
    labels = place_labels(pos, edges, names, list(paths.values()))
    line_order = [l["id"] for l in net["lines"]]
    lanes = bundle_lanes(paths, edges, line_order)
    lines_out = []
    for line in net["lines"]:
        segs = []
        for e, lids in edges.items():
            if line["id"] not in lids:
                continue
            off = lanes[(e, line["id"])]
            segs.append({"points": [[round(x * UNIT, 2), round(y * UNIT, 2)] for x, y in paths[e]], "offset": off})
        cat, flt = classify(line.get("operator", ""), line["id"])
        lines_out.append({"lineId": line["id"], "color": line["color"], "code": line.get("code", ""),
                          "category": cat, "filter": flt,
                          "terminals": [[round(x * UNIT, 2), round(y * UNIT, 2)] for x, y in icons.get(line["id"], [])],
                          "segments": segs})
    nodes = []
    for g in sorted(pos):
        ss = groups[g]
        nodes.append({"group": g, "x": round(pos[g][0] * UNIT, 2), "y": round(pos[g][1] * UNIT, 2),
                      "labelKo": ss[0]["name"]["ko"], "labelJa": ss[0]["name"]["ja"],
                      "labelDx": labels[g][0], "labelDy": labels[g][1],
                      "interchange": len({s["lineId"] for s in ss}) > 1, "stationIds": [s["id"] for s in ss]})
    return {"schemaVersion": 2, "regionId": net["regionId"], "unit": UNIT,
            "width": round(maxx * UNIT, 2), "height": round(maxy * UNIT, 2), "nodes": nodes, "lines": lines_out,
            "ghosts": []}


def metrics(layout):
    u = layout["unit"]
    polys, bends, octo = {}, 0, True
    offs = defaultdict(set)
    for l in layout["lines"]:
        for s in l["segments"]:
            pts = [(round(p[0] / u, 3), round(p[1] / u, 3)) for p in s["points"]]
            bends += len(pts) - 2
            octo = octo and all(_is_oct(b[0] - a[0], b[1] - a[1], 1e-3) for a, b in zip(pts, pts[1:]))
            k = (min(pts[0], pts[-1]), max(pts[0], pts[-1]))
            polys[k] = pts
            offs[k].add(round(float(s.get("offset", 0)), 2))
    pos = {}
    for n in layout["nodes"]:
        p = (round(n["x"] / u, 3), round(n["y"] / u, 3))
        pos[p] = p
    v, _, _ = violations(polys, pos)
    # 포개짐 재계산: 같은 역에서 출발해 서로 다른 오프셋(평행선 다발)으로 그려지는 것은 bundled 로 따로 센다
    items = list(polys.items())
    bxs = [_bbox(q, 0.05) for _, q in items]
    ov = bundled = 0
    for i, (e, q) in enumerate(items):
        for j in range(i + 1, len(items)):
            if not _bbhit(bxs[i], bxs[j]):
                continue
            f, r = items[j]
            o, _x = _seg_conflicts(q, r)
            if not o:
                continue
            if (set(e) & set(f)) and not (offs[e] & offs[f]):
                bundled += o
            else:
                ov += o
    v["overlaps"] = ov
    v["bundled"] = bundled
    pl = list(pos)
    mind = min(math.dist(pl[i], pl[j]) for i in range(len(pl)) for j in range(i + 1, len(pl)))
    icons = sum(len(l.get("terminals", [])) for l in layout["lines"])
    return {"nodes": len(pl), "bends": bends, "allOctilinear": octo, **v, "minNodeDist": round(mind, 2), "icons": icons}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("network")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()
    with open(a.network, encoding="utf-8") as f:
        net = json.load(f)
    t0 = time.monotonic()
    lay = build_layout(net)
    out = a.out or os.path.join(os.path.dirname(a.network), "layout.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(lay, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"[layout] {net['regionId']}: {metrics(lay)} ({time.monotonic() - t0:.0f}s)")


if __name__ == "__main__":
    main()
