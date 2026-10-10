"""tools/seoul/raw/*.json → tools/seoul/raw/SUMMARY.md (노선·필드·연결 비율 요약, 변환기 작성용)"""
import collections
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")


def load(name):
    p = os.path.join(RAW, f"{name}.json")
    if not os.path.exists(p):
        return []
    with open(p, encoding="utf-8") as f:
        return json.load(f)["rows"]


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


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


if __name__ == "__main__":
    main()
