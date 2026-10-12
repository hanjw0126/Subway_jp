"""인천교통공사 인천1·2호선 시간표 재구성 → app/src/main/assets/regions/seoul/timetable_incheon_<DAY|SAT|END>.json.gz

  python tools/incheon/build_timetable.py

공공데이터포털에는 역별 첫차·막차 시각표만 있으므로 다음으로 열차를 재구성한다 (실제 열차별 시각표가 아님):
  - 역간 소요시간: 첫차·막차 시각표의 인접 역 시각 차이 중 30초~10분인 값의 중앙값 (없으면 2분)
  - 운행 시간대: 종착역 기준 첫차~막차 (방향별)
  - 운행 간격: 인천교통공사가 공표한 시간대별 운행시격 (HEADWAYS)
  - 모든 열차는 종점까지 운행 (박촌행·계양행 등 구간 열차는 반영하지 않음)
  - 토요일은 토·휴일 시각표(휴일)와 같다
출력 형식은 tools/seoul/build_timetable.py 와 같다 (앱 SeoulTimetableSource 가 서울 시간표와 합쳐 읽는다).
상선 = 순번 감소(검단호수공원·검단오류 방면, desc), 하선 = 순번 증가(송도달빛축제공원·운연 방면, asc).
"""
import csv
import gzip
import json
import os
import re
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ROOT = os.path.dirname(TOOLS)
RAW = os.path.join(HERE, "raw")
SEED = os.path.join(TOOLS, "seed", "seoul.json")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "regions", "seoul")
STATS = os.path.join(RAW, "TIMETABLE_STATS.md")
DWELL = 20
DEFAULT_RUN = 120

LINES = {"IC1": "kr.Seoul.IC1", "IC2": "kr.Seoul.IC2"}

# 인천교통공사 공표 운행시격 (분) — (시작 HH:MM, 끝 HH:MM, 간격). 끝이 다음날이면 24시 넘게 적는다.
#  2호선: 공사 누리집 「열차운행시각표 > 2호선」 표 그대로
#  1호선: 공사 누리집 「열차운행시각표 > 1호선」 종착역 출발 시각의 시간대별 간격
HEADWAYS = {
    ("IC1", "WEEKDAY"): [("05:30", "06:30", 8.5), ("06:30", "07:00", 6.5), ("07:00", "10:00", 4.8),
                         ("10:00", "17:00", 9.0), ("17:00", "20:00", 6.0), ("20:00", "21:00", 8.5),
                         ("21:00", "25:30", 10.5)],
    ("IC1", "HOLIDAY"): [("05:30", "07:00", 10.0), ("07:00", "22:00", 8.0), ("22:00", "25:30", 10.5)],
    ("IC2", "WEEKDAY"): [("05:30", "06:30", 8.0), ("06:30", "07:46", 3.0), ("07:46", "08:16", 2.75),
                         ("08:16", "09:45", 3.0), ("09:45", "16:10", 6.2), ("16:10", "17:35", 4.7),
                         ("17:35", "20:10", 3.6), ("20:10", "23:04", 6.2), ("23:04", "25:06", 9.0)],
    ("IC2", "HOLIDAY"): [("05:30", "07:00", 10.0), ("07:00", "22:00", 5.5), ("22:00", "25:06", 8.5)],
}
# 앱 요일 코드 ← 원본 (토요일은 토·휴일 시각표)
DAY_FILES = {"DAY": "WEEKDAY", "SAT": "HOLIDAY", "END": "HOLIDAY"}


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def sec(t):
    """HH:MM[:SS] → 운행일 기준 초 (새벽 4시 전은 +24시간)"""
    t = (t or "").strip()
    if not t or not re.match(r"^\d{1,2}:\d{2}", t):
        return None
    p = [int(x) for x in t.split(":")]
    s = p[0] * 3600 + p[1] * 60 + (p[2] if len(p) > 2 else 0)
    return s + 86400 if p[0] < 4 else s


def hm(t):
    h, m = t.split(":")
    return int(h) * 3600 + int(m) * 60


def load_csv(path):
    with open(path, encoding="utf-8") as f:
        lines = [ln for ln in f if not ln.startswith("#")]
    return list(csv.DictReader(lines))


def col(row, *keys):
    for k in row:
        kk = (k or "").replace(" ", "")
        if all(x in kk for x in keys):
            return row[k]
    return ""


def running_times(rows):
    """인접 역 소요시간(초) — 역 순서(순번 증가) 기준 i→i+1"""
    out = []
    for i in range(len(rows) - 1):
        a, b = rows[i], rows[i + 1]
        cands = []
        # 하선(순번 증가 방향): b - a, 상선(순번 감소 방향): a - b
        for k1 in ("첫차", "막차"):
            x, y = sec(col(a, k1, "하선")), sec(col(b, k1, "하선"))
            if x is not None and y is not None:
                cands.append(y - x)
            x, y = sec(col(a, k1, "상선")), sec(col(b, k1, "상선"))
            if x is not None and y is not None:
                cands.append(x - y)
        good = [c for c in cands if 30 <= c <= 600]
        out.append(int(statistics.median(good)) if good else DEFAULT_RUN)
    return out


def departures(first, last, bands):
    """첫차~막차 사이를 시간대별 간격으로 채운 출발 시각 (첫차·막차 포함)"""
    out, t = [first], first
    while True:
        step = next((m for a, b, m in bands if hm(a) <= t < hm(b)), bands[-1][2])
        t = int(round(t + step * 60))
        if t >= last - 60:
            break
        out.append(t)
    if last > out[-1]:
        out.append(last)
    return out


def build_line(key, day_src, seed_line):
    path = os.path.join(RAW, f"{key}_{day_src}.csv")
    if not os.path.exists(path):
        return [], f"{key} {day_src}: 원본 없음"
    rows = load_csv(path)
    sid_by_name = {norm(st[7]): st[6] for st in seed_line["stations"]}
    idx = {st[6]: i for i, st in enumerate(seed_line["stations"])}
    rows = [r for r in rows if norm(col(r, "역명")) in sid_by_name]
    rows.sort(key=lambda r: idx[sid_by_name[norm(col(r, "역명"))]])
    if len(rows) < 2:
        return [], f"{key} {day_src}: 역 연결 실패"
    sids = [sid_by_name[norm(col(r, "역명"))] for r in rows]
    run = running_times(rows)
    bands = HEADWAYS[(key, day_src)]
    trips = []
    for d in (0, 1):  # 0 = 하선(asc), 1 = 상선(desc)
        seq = sids if d == 0 else list(reversed(sids))
        legs = run if d == 0 else list(reversed(run))
        origin = rows[0] if d == 0 else rows[-1]
        kind = "하선" if d == 0 else "상선"
        first, last = sec(col(origin, "첫차", kind)), sec(col(origin, "막차", kind))
        if first is None or last is None:
            continue
        if last < first:
            last += 86400
        for n, t0 in enumerate(departures(first, last, bands)):
            arr, dwell, t = [], [], t0
            for i in range(len(seq)):
                arr.append(t)
                dw = 0 if i in (0, len(seq) - 1) else DWELL
                dwell.append(dw)
                if i < len(legs):
                    t += dw + legs[i]
            trips.append((d, f"{key}-{'D' if d == 0 else 'U'}{n:03d}", seq, arr, dwell))
    total = sum(run)
    return trips, f"{key} {day_src}: 역 {len(sids)}곳, 전 구간 {total // 60}분 {total % 60}초, 열차 {len(trips)}편"


def main():
    with open(SEED, encoding="utf-8") as f:
        seed = json.load(f)
    by_id = {L["id"]: L for L in seed["lines"]}
    line_ids = [L["id"] for L in seed["lines"]]
    if not all(lid in by_id for lid in LINES.values()):
        print("[incheon] seed 에 인천 노선이 없습니다 — tools/seoul/build_seed.py 를 먼저 실행하세요", file=sys.stderr)
        return 1
    report = ["# 인천 시간표 재구성 통계", "",
              "공공데이터포털 인천교통공사 첫차·막차 시각표 + 공표 운행시격으로 재구성 (실제 열차별 시각표 아님)", ""]
    os.makedirs(OUT_DIR, exist_ok=True)
    for day, src in DAY_FILES.items():
        stations, sidx, out = [], {}, []
        for key, lid in LINES.items():
            trips, msg = build_line(key, src, by_id[lid])
            report.append(f"- {day}: {msg}")
            for d, code, seq, arr, dwell in trips:
                for s in seq:
                    if s not in sidx:
                        sidx[s] = len(stations)
                        stations.append(s)
                out.append([line_ids.index(lid), code, d, sidx[seq[-1]], 0,
                            [sidx[s] for s in seq], arr, dwell])
        doc = {"source": "인천교통공사 첫차·막차 열차운행시각표(공공데이터포털) + 공표 운행시격 재구성",
               "dayType": day, "lines": line_ids, "stations": stations, "trips": out}
        data = json.dumps(doc, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        path = os.path.join(OUT_DIR, f"timetable_incheon_{day}.json.gz")
        with open(path, "wb") as f:
            with gzip.GzipFile(fileobj=f, mode="wb", mtime=0, compresslevel=9) as gz:
                gz.write(data)
        print(f"[incheon] {day}: 열차 {len(out)}편 → {os.path.relpath(path, ROOT)} ({os.path.getsize(path)} bytes)")
    with open(STATS, "w", encoding="utf-8") as f:
        f.write("\n".join(report) + "\n")
    print("\n".join(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
