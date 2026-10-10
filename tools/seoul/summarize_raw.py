"""tools/seoul/raw/*.json → SUMMARY.md (노선·필드·연결 비율), LINES.md (노선별 외부코드 순 역 목록·좌표 후보)

변환기(노선 순서·분기 규칙)를 사람이 검토하며 작성하기 위한 요약이다.
"""
import collections
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")

# 실시간 API 를 지원하는 노선만 LINES.md 에 자세히 쓴다
REALTIME_LINES = ["01호선", "02호선", "03호선", "04호선", "05호선", "06호선", "07호선", "08호선", "09호선",
                  "GTX-A", "경강선", "경의선", "경춘선", "공항철도", "서해선", "수인분당선", "신림선", "신분당선", "우이신설경전철"]


def load(name):
    p = os.path.join(RAW, f"{name}.json")
    if not os.path.exists(p):
        return []
    with open(p, encoding="utf-8") as f:
        return json.load(f)["rows"]


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def fr_key(code):
    """외부코드 자연 정렬: P148 < P150, 211 < 211-1 < 212, A04 < A042"""
    m = re.match(r"^([A-Z]*)(\d+)(?:-(\d+))?$", code or "")
    if not m:
        return ("~", 0, 0, code or "")
    return (m.group(1), int(m.group(2)), int(m.group(3) or 0), code)


def main():
    lines_rows = load("SearchSTNBySubwayLineInfo")
    master = load("subwayStationMaster")
    out = ["# 서울 원본 데이터 요약", ""]

    out += ["## SearchSTNBySubwayLineInfo", f"- 행 수: {len(lines_rows)}",
            f"- 필드: {sorted(lines_rows[0].keys()) if lines_rows else []}", ""]
    out += ["| LINE_NUM | 역 수 | FR_CODE 예 (앞 6개) | FR_CODE 없음 |", "|---|---|---|---|"]
    by_line = collections.defaultdict(list)
    for r in lines_rows:
        by_line[r.get("LINE_NUM", "")].append(r)
    for ln in sorted(by_line):
        rs = by_line[ln]
        frs = sorted((r.get("FR_CODE") or "") for r in rs)
        missing = sum(1 for f in frs if not f)
        out.append(f"| {ln} | {len(rs)} | {', '.join(f for f in frs if f)[:80]} | {missing} |")
    out += ["", "샘플 3행:", "```json"]
    out += [json.dumps(r, ensure_ascii=False) for r in lines_rows[:3]]
    out += ["```", ""]

    out += ["## subwayStationMaster", f"- 행 수: {len(master)}",
            f"- 필드: {sorted(master[0].keys()) if master else []}", ""]
    routes = collections.Counter(r.get("ROUTE", "") for r in master)
    out += ["| ROUTE | 역 수 |", "|---|---|"]
    out += [f"| {k} | {v} |" for k, v in sorted(routes.items())]
    out += ["", "샘플 3행:", "```json"]
    out += [json.dumps(r, ensure_ascii=False) for r in master[:3]]
    out += ["```", ""]

    # 역명으로 좌표를 연결할 수 있는 비율 (노선별)
    mnames = collections.defaultdict(list)
    for r in master:
        mnames[norm(r.get("BLDN_NM", ""))].append(r)
    out += ["## 역명으로 좌표 연결", "", "| LINE_NUM | 연결됨 | 연결 안 됨 (앞 8개) |", "|---|---|---|"]
    for ln in sorted(by_line):
        rs = by_line[ln]
        miss = [r.get("STATION_NM", "") for r in rs if norm(r.get("STATION_NM", "")) not in mnames]
        out.append(f"| {ln} | {len(rs) - len(miss)}/{len(rs)} | {', '.join(miss[:8])} |")
    out.append("")
    with open(os.path.join(RAW, "SUMMARY.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("\n".join(out))

    # 노선별 외부코드 순 목록: FR_CODE STATION_CD 역명 | 좌표 후보(ROUTE 위도,경도)
    det = ["# 노선별 역 목록 (외부코드 자연 정렬)", "",
           "형식: `FR_CODE STATION_CD 역명 | ROUTE 위도,경도 ; ...` (좌표 후보는 역명이 같은 역사마스터 행)", ""]
    for ln in REALTIME_LINES:
        rs = sorted(by_line.get(ln, []), key=lambda r: fr_key(r.get("FR_CODE")))
        det += [f"## {ln} ({len(rs)})", "```"]
        for r in rs:
            cands = " ; ".join(f"{m.get('ROUTE')} {float(m.get('LAT') or 0):.4f},{float(m.get('LOT') or 0):.4f}"
                               for m in mnames.get(norm(r.get("STATION_NM", "")), []))
            det.append(f"{r.get('FR_CODE')} {r.get('STATION_CD')} {r.get('STATION_NM')} | {cands}")
        det += ["```", ""]
    with open(os.path.join(RAW, "LINES.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(det))


if __name__ == "__main__":
    main()
