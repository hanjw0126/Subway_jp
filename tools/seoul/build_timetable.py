"""서울교통공사_서울 도시철도 열차운행시각표(CSV) → 앱 시간표
   app/src/main/assets/regions/seoul/timetable_<DAY|SAT|END>.json.gz

  python tools/seoul/build_timetable.py <timetable.csv>

- 같은 (호선, 요일, 열차코드)의 행을 시각 순으로 이어 열차 한 편(trip)으로 만든다.
  같은 역이 다시 나오거나 30분 이상 비면(순환선을 여러 바퀴 도는 열차 등) 다음 편으로 나눈다.
- 역사코드 → 서울시 노선별 역 목록(SearchSTNBySubwayLineInfo)의 외부코드 → 앱 역 ID(kr.Seoul.<노선>[.지선].<외부코드>).
  본선→지선 직통 열차(예: 1호선 경부선)는 정차역마다 그 역이 있는 지선의 역을 고른다 (노선을 이어 가는 쪽 우선).
  경로 탐색(RAPTOR)은 열차를 정차역 순서로 묶으므로 한 편이 여러 지선을 거쳐도 된다.
- 출력(하루 종류별, gzip): {"source", "lines": [노선 ID], "stations": [역 ID],
    "trips": [[노선#, 열차코드, 방향(0=asc,1=desc), 종착역#, 급행(0/1), [역#...], [도착초...], [정차초...]]]}
  도착초는 운행일 기준 초(24시 넘는 값 그대로), 정차초 = 출발 - 도착.
- 변환 통계: tools/seoul/raw/TIMETABLE_STATS.md (편 수, 앱 역으로 연결하지 못한 역)
"""
import collections
import csv
import gzip
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ROOT = os.path.dirname(TOOLS)
RAW = os.path.join(HERE, "raw")
SEED = os.path.join(TOOLS, "seed", "seoul.json")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "seoul")
STATS = os.path.join(RAW, "TIMETABLE_STATS.md")
DAYS = ("DAY", "SAT", "END")
SPLIT_GAP_SEC = 30 * 60


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def sec(t):
    t = (t or "").strip()
    if not t:
        return None
    parts = [int(x) for x in t.split(":")]
    while len(parts) < 3:
        parts.append(0)
    return parts[0] * 3600 + parts[1] * 60 + parts[2]


def station_index(seed, lines_raw):
    """(호선 '1'~'9', 역사코드) → [앱 역 ID], 역 ID → 노선 ID, (호선, 역명) → [역 ID]"""
    fr_by_cd = {}
    for r in lines_raw:
        m = re.match(r"0?(\d)호선$", r.get("LINE_NUM", ""))
        if m:
            fr_by_cd[(m.group(1), r["STATION_CD"])] = r["FR_CODE"]
    by_fr = collections.defaultdict(list)
    line_of, by_name = {}, collections.defaultdict(list)
    for L in seed["lines"]:
        key = L["id"].split(".")[2]
        for st in L["stations"]:
            sid = st[6]
            by_fr[(key, st[0])].append(sid)
            line_of[sid] = L["id"]
            by_name[(key, norm(st[7]))].append(sid)
    idx = {k: by_fr.get((k[0], fr), []) for k, fr in fr_by_cd.items()}
    # 역사코드가 역 목록에 없거나 외부코드가 다르면 역명으로 찾는다
    return idx, line_of, by_name


def split_trips(stops):
    """시각 순 정차 목록 → 열차 편 목록 (같은 역 재등장·긴 공백에서 나눔)"""
    out, cur, seen = [], [], set()
    for s in sorted(stops, key=lambda s: (s["arr"], s["dep"])):
        if cur and (s["cd"] in seen or s["arr"] - cur[-1]["dep"] > SPLIT_GAP_SEC):
            out.append(cur)
            cur, seen = [], set()
        cur.append(s)
        seen.add(s["cd"])
    if cur:
        out.append(cur)
    return out


def choose_sids(line_no, stops, idx, line_of, by_name, missing):
    """정차역마다 앱 역 ID 하나씩 (앞 역과 같은 노선 우선, 없으면 다음 역과 같은 노선).
    역사코드로 못 찾으면 같은 호선의 같은 역명으로 찾고, 그래도 없으면 missing 에 센다."""
    cands = []
    for s in stops:
        c = idx.get((line_no, s["cd"])) or by_name.get((line_no, s["name"]), [])
        if c:
            cands.append((s, c))
        else:
            missing[(line_no, s["cd"], s["name"])] += 1
    chosen, prev_line = [], None
    for i, (s, cs) in enumerate(cands):
        pick = None
        if prev_line:
            pick = next((x for x in cs if line_of[x] == prev_line), None)
        if pick is None and i + 1 < len(cands):
            nxt = {line_of[x] for x in cands[i + 1][1]}
            pick = next((x for x in cs if line_of[x] in nxt), None)
        pick = pick or cs[0]
        chosen.append((s, pick))
        prev_line = line_of[pick]
    return chosen


def build(rows, seed, lines_raw):
    idx, line_of, by_name = station_index(seed, lines_raw)
    line_ids = [L["id"] for L in seed["lines"]]
    order = {L["id"]: {st[6]: i for i, st in enumerate(L["stations"])} for L in seed["lines"]}
    loop = {L["id"] for L in seed["lines"] if L.get("loop")}
    groups = collections.defaultdict(list)
    for r in rows:
        arr, dep = sec(r.get("열차도착시간")), sec(r.get("열차출발시간"))
        if arr is None and dep is None:
            continue
        arr = dep if arr is None else arr
        dep = arr if dep is None else max(dep, arr)
        groups[(r["호선"].strip(), r["주중주말"].strip(), r["열차코드"].strip())].append({
            "cd": r["역사코드"].strip(), "name": norm(r["역사명"]), "arr": arr, "dep": dep, "dir": r["방향"].strip(),
            "exp": r["급행여부"].strip() == "1", "dest": norm(r["도착역"]),
        })
    out = {d: {"stations": [], "sidx": {}, "trips": []} for d in DAYS}
    missing = collections.Counter()
    for (line_no, day, code), stops in sorted(groups.items()):
        if day not in out:
            continue
        o = out[day]
        for trip in split_trips(stops):
            chosen = choose_sids(line_no, trip, idx, line_of, by_name, missing)
            if len(chosen) < 2:
                continue
            lid = collections.Counter(line_of[sid] for _, sid in chosen).most_common(1)[0][0]
            first = trip[0]
            if lid in loop:
                d = 0 if first["dir"] == "OUT" else 1  # 외선 = 순번 증가(asc)
            else:
                pos = [order[lid][sid] for _, sid in chosen if sid in order[lid]]
                d = 0 if len(pos) < 2 or pos[-1] >= pos[0] else 1
            dest_sids = by_name.get((line_no, first["dest"]), [])
            dest = next((x for x in dest_sids if line_of[x] == lid), dest_sids[0] if dest_sids else chosen[-1][1])
            sids = [sid for _, sid in chosen] + [dest]
            for sid in sids:
                if sid not in o["sidx"]:
                    o["sidx"][sid] = len(o["stations"])
                    o["stations"].append(sid)
            o["trips"].append([
                line_ids.index(lid), code, d, o["sidx"][dest], 1 if any(s["exp"] for s in trip) else 0,
                [o["sidx"][sid] for _, sid in chosen],
                [s["arr"] for s, _ in chosen],
                [s["dep"] - s["arr"] for s, _ in chosen],
            ])
    return out, line_ids, missing


def main():
    if len(sys.argv) < 2:
        sys.exit("사용법: build_timetable.py <timetable.csv>")
    with open(sys.argv[1], encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    with open(SEED, encoding="utf-8") as f:
        seed = json.load(f)
    with open(os.path.join(RAW, "SearchSTNBySubwayLineInfo.json"), encoding="utf-8") as f:
        lines_raw = json.load(f)["rows"]
    src = ""
    p = os.path.join(RAW, "timetable_source.txt")
    if os.path.exists(p):
        src = open(p, encoding="utf-8").read().strip()
    out, line_ids, missing = build(rows, seed, lines_raw)
    os.makedirs(OUT_DIR, exist_ok=True)
    report = ["# 서울 열차운행시각표 변환 통계", "", f"원본: {src}", ""]
    for day, o in out.items():
        doc = {"source": src, "dayType": day, "lines": line_ids, "stations": o["stations"], "trips": o["trips"]}
        data = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        path = os.path.join(OUT_DIR, f"timetable_{day}.json.gz")
        # mtime=0 → 내용이 같으면 파일도 같다 (불필요한 커밋 방지)
        with open(path, "wb") as f:
            with gzip.GzipFile(fileobj=f, mode="wb", mtime=0, compresslevel=9) as gz:
                gz.write(data)
        stops = sum(len(t[5]) for t in o["trips"])
        per_line = collections.Counter(line_ids[t[0]] for t in o["trips"])
        line = f"- {day}: 열차 {len(o['trips'])}편, 정차 {stops}, 역 {len(o['stations'])}, {os.path.getsize(path)} bytes"
        print(line)
        report.append(line)
        report.append(f"  - 노선별 편 수: {dict(sorted(per_line.items()))}")
    total = sum(missing.values())
    report += ["", f"## 앱 역으로 연결하지 못한 정차 {total}건 (호선, 역사코드, 역명: 건수)", ""]
    report += [f"- {k[0]}호선 {k[1]} {k[2]}: {v}" for k, v in missing.most_common(80)]
    with open(STATS, "w", encoding="utf-8") as f:
        f.write("\n".join(report) + "\n")
    print(f"앱 역으로 연결하지 못한 정차 {total}건")


if __name__ == "__main__":
    main()
