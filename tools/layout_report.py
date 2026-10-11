"""노선도 품질 보고서 → docs/images/layout_report.md

지역별 지표(metrics)와, 반드시 없어야 하는 위반(같은 노선 교차·포개짐·역 위 통과)이 어느 노선·어느 역에서
생겼는지를 적는다. CI 로그를 볼 수 없어도 저장소에서 원인을 확인할 수 있도록 한다.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")
OUT = os.path.join(ROOT, "docs", "images", "layout_report.md")
sys.path.insert(0, HERE)

from layout_schematic import _bbhit, _bbox, _cross, _overlap, metrics  # noqa: E402


def _segments(layout):
    """(노선 ID, 점 목록) — 좌표는 역 간격 단위"""
    u = layout["unit"]
    out = []
    for l in layout["lines"]:
        for s in l["segments"]:
            out.append((l["lineId"], [(round(p[0] / u, 3), round(p[1] / u, 3)) for p in s["points"]]))
    return out


def _node_names(layout, network):
    u = layout["unit"]
    name_of = {s["group"]: s["name"]["ko"] for s in network["stations"]}
    return {(round(n["x"] / u, 3), round(n["y"] / u, 3)): name_of.get(n["group"], n["group"]) for n in layout["nodes"]}


def self_crossings(layout, network):
    """같은 노선의 두 구간이 교차하거나 포개지는 곳: [(노선명, '역-역', '역-역', 종류)]"""
    names = _node_names(layout, network)
    line_name = {l["id"]: l["name"]["ko"] for l in network["lines"]}
    segs = _segments(layout)
    boxes = [_bbox(q, 0.05) for _, q in segs]
    found = []
    for i, (li, q) in enumerate(segs):
        for j in range(i + 1, len(segs)):
            lj, r = segs[j]
            if li != lj or not _bbhit(boxes[i], boxes[j]):
                continue
            kind = None
            for s in zip(q, q[1:]):
                for t in zip(r, r[1:]):
                    if _overlap(s, t):
                        kind = "포개짐"
                    elif _cross(s, t):
                        kind = kind or "교차"
            if kind:
                a = f"{names.get(q[0], '?')}-{names.get(q[-1], '?')}"
                b = f"{names.get(r[0], '?')}-{names.get(r[-1], '?')}"
                found.append((line_name.get(li, li), a, b, kind))
    return found


def main():
    with open(os.path.join(ASSETS, "regions.json"), encoding="utf-8") as f:
        regions = [r["id"] for r in json.load(f)]
    lines = ["# 노선도 품질 보고서", "",
             "노선도 재생성(Rebuild maps) 때마다 갱신. "
             "selfCrossings(같은 노선 교차)·overlaps·nodeOnEdge 는 0 이어야 한다.", ""]
    for rid in regions:
        d = os.path.join(ASSETS, rid)
        with open(os.path.join(d, "layout.json"), encoding="utf-8") as f:
            lay = json.load(f)
        with open(os.path.join(d, "network.json"), encoding="utf-8") as f:
            net = json.load(f)
        m = metrics(lay)
        lines += [f"## {rid}", "", "```", json.dumps(m, ensure_ascii=False), "```", ""]
        bad = self_crossings(lay, net)
        if bad:
            lines += ["같은 노선 교차·포개짐:", ""]
            lines += [f"- {ln}: {a} × {b} ({k})" for ln, a, b, k in bad[:60]]
            lines.append("")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))


if __name__ == "__main__":
    main()
