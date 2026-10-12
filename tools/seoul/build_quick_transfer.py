"""국토교통부_철도역 빠른 환승 정보(CSV, tools/seoul/raw/quick_transfer.csv) → assets/regions/<지역>/quick_transfer.json

수도권 운영기관 행만 쓴다 (부산·대구 등 같은 'N호선' 이름이 섞이지 않게). 노선명은 앱 노선 키(kr.Seoul.<키>)로,
역명은 괄호·'역'을 떼고 적는다. 앱이 경로의 환승(내리는 노선·역·진행 방향 → 갈아탈 노선·방향)에 맞는 행을 찾는다.
출력: {"source", "rows": [[노선키, 역명, 종착역명(진행 방향), 환승노선키, 환승 뒤 다음 역명, 칸, 문]]}
"""
import csv
import json
import os
import re

OPERATORS = {"서울교통공사", "코레일", "공항철도주식회사", "서울시메트로9호선주식회사", "인천교통공사",
             "네오트랜스주식회사", "우이신설경전철주식회사"}
LINE_KEYS = {
    "경의중앙": "KJ", "경의중앙선": "KJ", "수인분당": "SB", "수인분당선": "SB", "공항철도": "AREX",
    "경춘": "GC", "경춘선": "GC", "신분당선": "SBD", "인천1호선": "IC1", "인천2호선": "IC2",
    "우이신설": "UI", "우이신설선": "UI", "경강": "GG", "경강선": "GG", "서해": "WS", "서해선": "WS",
    "신림선": "SL", "GTX-A": "GTXA",
}


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def line_key(name):
    name = (name or "").strip()
    m = re.match(r"0?(\d)호선$", name)
    return m.group(1) if m else LINE_KEYS.get(name)


def build(csv_path, out_path):
    with open(csv_path, encoding="utf-8") as f:
        lines = [ln for ln in f if not ln.startswith("#")]
    out, seen = [], set()
    for r in csv.DictReader(lines):
        if r.get("철도운영기관명") not in OPERATORS or r.get("환승철도운영기관명") not in OPERATORS:
            continue
        a, b = line_key(r.get("노선명")), line_key(r.get("환승선"))
        car = (r.get("차량순서") or "").strip()
        door = (r.get("차량출입문번호") or "").strip()
        if not a or not b or not car.isdigit():
            continue
        row = [a, norm(r.get("역명")), norm(r.get("종착역명")), b, norm(r.get("환승이후역명")),
               int(car), int(door) if door.isdigit() else 0]
        if tuple(row) not in seen:
            seen.add(tuple(row))
            out.append(row)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"source": "국토교통부_철도역 빠른 환승 정보 (공공데이터포털 15151816)", "rows": out},
                  f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"[quick-transfer] {len(out)}건 → {out_path}")
