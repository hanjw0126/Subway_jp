"""역 위경도 → 도식 노선도 생성용 평면 좌표(km).

기본은 등거리 투영(역 평균 위치 기준). network.json 의 layoutHints 로 지역별 보정을 더한다.
  radialPower (0~1): 중심(역 좌표 중앙값)에서의 거리 r 을 r0·(r/r0)^power 로 바꿔
                     도심은 넓히고 외곽은 좁힌다 (수도권처럼 멀리 뻗는 노선이 많은 경우).
                     r0 = 중심에서 역까지 거리의 중앙값.
강 등 다른 지리 요소도 같은 projection/to_xy 로 옮겨야 노선도와 어긋나지 않는다.
"""
import math
from collections import defaultdict


def _flat(lat, lon, lat0, lon0):
    return ((lon - lon0) * math.cos(math.radians(lat0)) * 111.32, -(lat - lat0) * 110.57)


def _group_means(net):
    groups = defaultdict(list)
    for s in net["stations"]:
        groups[s["group"]].append(s)
    means = {g: (sum(s["lat"] for s in ss) / len(ss), sum(s["lon"] for s in ss) / len(ss)) for g, ss in groups.items()}
    return groups, means


def projection(net):
    """지역 투영 매개변수 (to_xy 에 넘긴다)"""
    st = net["stations"]
    lat0 = sum(s["lat"] for s in st) / len(st)
    lon0 = sum(s["lon"] for s in st) / len(st)
    proj = {"lat0": lat0, "lon0": lon0, "radial": None}
    power = (net.get("layoutHints") or {}).get("radialPower")
    if power and 0 < power < 1:
        _, means = _group_means(net)
        pts = [_flat(la, lo, lat0, lon0) for la, lo in means.values()]
        xs = sorted(x for x, _ in pts)
        ys = sorted(y for _, y in pts)
        cx, cy = xs[len(xs) // 2], ys[len(ys) // 2]
        rs = sorted(math.hypot(x - cx, y - cy) for x, y in pts)
        r0 = rs[len(rs) // 2] or 1.0
        proj["radial"] = (cx, cy, r0, power)
    return proj


def to_xy(lat, lon, proj):
    x, y = _flat(lat, lon, proj["lat0"], proj["lon0"])
    rp = proj.get("radial")
    if not rp:
        return x, y
    cx, cy, r0, p = rp
    dx, dy = x - cx, y - cy
    r = math.hypot(dx, dy)
    if r < 1e-9:
        return x, y
    k = r0 * (r / r0) ** p / r
    return cx + dx * k, cy + dy * k


def project(net):
    """환승 묶음(group)별 역 목록과 평면 좌표"""
    groups, means = _group_means(net)
    proj = projection(net)
    return groups, {g: to_xy(la, lo, proj) for g, (la, lo) in means.items()}
