#!/usr/bin/env python3
"""빌드 결과 요약 (노선 수·역 수·노선도 지표). 데이터 본문은 출력하지 않는다."""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
from layout_schematic import metrics  # noqa: E402

A = os.path.join(os.path.dirname(TOOLS), "app", "src", "main", "assets", "regions")
print("| 지역 | 노선 (챌린지) | 역 | 노선도 지표 |\n|---|---|---|---|")
for r in sorted(os.listdir(A)):
    p = os.path.join(A, r)
    if not os.path.isdir(p):
        continue
    net = json.load(open(os.path.join(p, "network.json"), encoding="utf-8"))
    lay = json.load(open(os.path.join(p, "layout.json"), encoding="utf-8"))
    c = [l for l in net["lines"] if l.get("source") == "c2026"]
    print(f"| {r} | {len(net['lines'])} ({len(c)}) | {len(net['stations'])} | {metrics(lay)} |")
    for l in c:
        print(f"- {l['code']} {l['name']['ko']} · 역 {len(l['stations'])}")
