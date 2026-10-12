"""레일포털(KRIC) Open API 시험 → tools/kric/raw/PROBE.md

1) 도시철도 전체노선정보(subwayRouteInfo, 수도권 mreaWideCd=01): 운영기관·노선·역 코드
2) 열차별 운행시각표(subwayTimetable): 노선마다 첫 역 평일(dayCd=8) 한 번 → 제공 여부·응답 형식
키(KRIC_KEY)는 보고서에 적지 않는다. 노선 목록 원본은 tools/kric/raw/route_info.json 에 저장.
"""
import collections
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
BASE = "https://openapi.kric.go.kr/openapi/"
KEY = os.environ.get("KRIC_KEY", "").strip()
# 노선코드를 모를 때 시험할 후보 (샘플 문서의 A1 등)
LN_CANDIDATES = [str(i) for i in range(1, 10)] + ["A1", "K1", "K2", "K4", "K5", "D1", "E1", "G1", "I1", "I2",
                                                "S1", "U1", "UI", "WS", "SL", "GTX", "KK", "KJ", "SU", "B1"]

log = []


def note(s=""):
    print(s)
    log.append(s.replace(KEY, "***") if KEY else s)


def call(service, op, **params):
    params = {"serviceKey": KEY, "format": "json", **params}
    url = f"{BASE}{service}/{op}?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "WorldWideMetro/1.0"}), timeout=60) as r:
            text = r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code} {e.read()[:200].decode('utf-8', 'replace')}"
    except Exception as e:  # noqa: BLE001
        return None, f"실패 {e}"
    try:
        return json.loads(text), text[:300]
    except json.JSONDecodeError:
        return None, text[:300]


def rows_of(data):
    """응답에서 행 목록을 찾는다 (body / response.body.items 등 형식이 다양)"""
    if isinstance(data, list):
        return data
    if not isinstance(data, dict):
        return []
    for k in ("body", "items", "item", "data", "row"):
        v = data.get(k)
        if isinstance(v, list):
            return v
        if isinstance(v, dict):
            r = rows_of(v)
            if r:
                return r
    for v in data.values():
        if isinstance(v, (dict, list)):
            r = rows_of(v)
            if r:
                return r
    return []


def main():
    os.makedirs(RAW, exist_ok=True)
    note("# 레일포털(KRIC) Open API 시험")
    note()
    if not KEY:
        note("- KRIC_KEY 없음")
        open(os.path.join(RAW, "PROBE.md"), "w", encoding="utf-8").write("\n".join(log) + "\n")
        return 0
    note("## 1) 도시철도 전체노선정보 (subwayRouteInfo, mreaWideCd=01)")
    data, raw = call("trainUseInfo", "subwayRouteInfo", mreaWideCd="01")
    rows = rows_of(data)
    note(f"- 노선코드 없이: {len(rows)}행 / 응답 앞부분: {raw[:200]!r}")
    all_rows = list(rows)
    if not rows:
        for ln in LN_CANDIDATES:
            d, raw = call("trainUseInfo", "subwayRouteInfo", mreaWideCd="01", lnCd=ln)
            r = rows_of(d)
            if r:
                note(f"- lnCd={ln}: {len(r)}행")
                all_rows += r
            time.sleep(0.2)
    if all_rows:
        note(f"- 필드: {sorted(all_rows[0].keys())}")
        note(f"- 예: {json.dumps(all_rows[0], ensure_ascii=False)}")
        with open(os.path.join(RAW, "route_info.json"), "w", encoding="utf-8") as f:
            json.dump(all_rows, f, ensure_ascii=False, indent=1)
    # 운영기관·노선별 역 수
    def g(r, *ks):
        for k in ks:
            if r.get(k) not in (None, ""):
                return str(r[k])
        return ""
    combos = collections.OrderedDict()
    for r in all_rows:
        key = (g(r, "railOprIsttCd"), g(r, "lnCd"), g(r, "lnNm", "routNm"))
        combos.setdefault(key, []).append(r)
    note()
    note("| 운영기관 코드 | 노선 코드 | 노선명 | 역 수 | 첫 역 |")
    note("|---|---|---|---|---|")
    for (opr, ln, nm), rs in combos.items():
        note(f"| {opr} | {ln} | {nm} | {len(rs)} | {g(rs[0], 'stinCd')} {g(rs[0], 'stinNm')} |")
    note()
    note("## 2) 열차별 운행시각표 (subwayTimetable, 평일 dayCd=8, 노선별 첫 역)")
    shown = False
    for (opr, ln, nm), rs in combos.items():
        mid = rs[len(rs) // 2]
        d, raw = call("trainUseInfo", "subwayTimetable", railOprIsttCd=opr, dayCd="8", lnCd=ln, stinCd=g(mid, "stinCd"))
        tr = rows_of(d)
        note(f"- {opr}/{ln} {nm} · {g(mid, 'stinNm')}({g(mid, 'stinCd')}): {len(tr)}행" + ("" if tr else f" / {raw[:160]!r}"))
        if tr and not shown:
            note(f"  - 필드: {sorted(tr[0].keys())}")
            for x in tr[:3]:
                note(f"  - 예: {json.dumps(x, ensure_ascii=False)}")
            shown = True
        time.sleep(0.3)
    with open(os.path.join(RAW, "PROBE.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
