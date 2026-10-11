"""환승역별 환승 소요시간(서울교통공사, tools/seoul/raw/transfer_times.csv) → 서울 network.json 의 transfers.walkSec

CSV: 연번, 호선(출발 호선 번호), 환승역명, 환승노선(갈아탈 노선명), 환승거리(m), 환승소요시간(분:초)
같은 환승역의 두 노선 역 사이 환승에 그 소요시간을 쓴다. 반대 방향 값이 없으면 같은 값을 쓴다.
표에 없는 환승은 기본값(seed 의 transferWalkSec)을 그대로 둔다.
"""
import csv
import json
import re

# 환승노선 이름 → 앱 노선 키 (kr.Seoul.<키>)
LINE_KEYS = {
    "경의중앙선": "KJ", "중앙선": "KJ", "공항철도": "AREX", "경춘선": "GC", "수인분당선": "SB",
    "분당선": "SB", "신분당선": "SBD", "우이신설선": "UI", "서해선": "WS", "GTX-A": "GTXA",
    "신림선": "SL", "경강선": "GG", "국철": "1", "경원선": "1", "경부선": "1", "경인선": "1",
}


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def line_key(name):
    name = (name or "").strip()
    m = re.match(r"0?(\d)(호선)?$", name)
    if m:
        return m.group(1)
    return LINE_KEYS.get(name)


def _sec(t):
    parts = [int(x) for x in (t or "0").strip().split(":")]
    return parts[0] * 60 + parts[1] if len(parts) == 2 else parts[0]


def load(path):
    """{(출발 키, 역명, 도착 키): 초}"""
    with open(path, encoding="utf-8") as f:
        lines = [ln for ln in f if not ln.startswith("#")]
    table = {}
    for r in csv.DictReader(lines):
        a, b = line_key(r.get("호선")), line_key(r.get("환승노선"))
        if not a or not b:
            continue
        try:
            table[(a, norm(r.get("환승역명")), b)] = _sec(r.get("환승소요시간"))
        except ValueError:
            continue
    return table


def apply(network_path, csv_path):
    table = load(csv_path)
    with open(network_path, encoding="utf-8") as f:
        net = json.load(f)
    st = {s["id"]: s for s in net["stations"]}

    def key_of(sid):
        return st[sid]["lineId"].split(".")[2]

    hit = 0
    for t in net["transfers"]:
        a, b = st.get(t["from"]), st.get(t["to"])
        if not a or not b or not a["lineId"].startswith("kr.Seoul."):
            continue
        ka, kb = key_of(t["from"]), key_of(t["to"])
        name = norm(a["name"]["ko"])
        sec = table.get((ka, name, kb)) or table.get((kb, name, ka))
        if sec:
            t["walkSec"] = sec
            hit += 1
    with open(network_path, "w", encoding="utf-8") as f:
        json.dump(net, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"[transfers] {net['regionId']}: 환승시간 {len(table)}건 중 {hit}개 환승에 적용")
