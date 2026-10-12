"""레일포털(KRIC) Open API 시험 → tools/kric/raw/PROBE.md

0) 키 형식 확인: 원래 값 / URL 디코딩 값 / 인코딩 없이 그대로 붙이기 — 문서의 샘플 요청으로 어느 것이 되는지
1) 도시철도 전체노선정보(subwayRouteInfo, 수도권 mreaWideCd=01): 운영기관·노선·역 코드
2) 열차별 운행시각표(subwayTimetable): 노선마다 가운데 역 평일(dayCd=8) 한 번 → 제공 여부·응답 형식
키 값은 보고서에 적지 않는다 (길이와 특수문자 포함 여부만). 노선 목록 원본은 tools/kric/raw/route_info.json.
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
KEY_RAW = os.environ.get("KRIC_KEY", "").strip()
LN_CANDIDATES = [str(i) for i in range(1, 10)] + ["A1", "K1", "K2", "K4", "K5", "D1", "E1", "G1", "I1", "I2",
                                                "S1", "U1", "UI", "WS", "SL", "GTX", "KK", "KJ", "SU", "B1"]
# 문서 샘플 요청 (키 형식 확인용)
SAMPLES = [
    ("trainUseInfo", "subwayTimetable", {"railOprIsttCd": "S1", "dayCd": "8", "lnCd": "1", "stinCd": "150"}),
    ("convenientInfo", "stationInfo", {"railOprIsttCd": "KR", "lnCd": "1", "stinCd": "135", "stinNm": "용산"}),
    ("trainUseInfo", "subwayRouteInfo", {"mreaWideCd": "01", "lnCd": "A1"}),
]

log = []
KEY = KEY_RAW
MODE = "encode"  # encode = urlencode 로 붙임, raw = 그대로 붙임


def note(s=""):
    print(s.replace(KEY_RAW, "***") if KEY_RAW else s)
    log.append(s.replace(KEY_RAW, "***") if KEY_RAW else s)


def call(service, op, key=None, mode=None, **params):
    key = KEY if key is None else key
    mode = MODE if mode is None else mode
    q = urllib.parse.urlencode({"format": "json", **params})
    k = urllib.parse.quote(key, safe="") if mode == "encode" else key
    url = f"{BASE}{service}/{op}?serviceKey={k}&{q}"
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


def check_key():
    """어떤 키 형식이 통하는지 문서 샘플로 확인. 통하는 (키, 방식)을 돌려준다"""
    global KEY, MODE
    note("## 0) 키 형식 확인")
    note(f"- 길이 {len(KEY_RAW)}, '%' 포함 {'%' in KEY_RAW}, '+' 포함 {'+' in KEY_RAW}, "
         f"'/' 포함 {'/' in KEY_RAW}, '=' 포함 {'=' in KEY_RAW}, 공백 포함 {any(c.isspace() for c in KEY_RAW)}")
    variants = [("원래 값·인코딩", KEY_RAW, "encode"), ("원래 값·그대로", KEY_RAW, "raw"),
                ("디코딩 값·인코딩", urllib.parse.unquote(KEY_RAW), "encode")]
    ok = None
    for label, key, mode in variants:
        for service, op, params in SAMPLES:
            d, raw = call(service, op, key=key, mode=mode, **params)
            n = len(rows_of(d))
            note(f"- {label} · {op}: {n}행 / {raw[:140]!r}")
            if n and ok is None:
                ok = (key, mode)
            time.sleep(0.2)
    if ok:
        KEY, MODE = ok
        note(f"- 사용할 형식: {'디코딩 값' if ok[0] != KEY_RAW else '원래 값'} · {'인코딩' if ok[1] == 'encode' else '그대로'}")
    else:
        note("- 통하는 형식 없음")
    return ok


def main():
    os.makedirs(RAW, exist_ok=True)
    note("# 레일포털(KRIC) Open API 시험")
    note()
    if not KEY_RAW:
        note("- KRIC_KEY 없음")
    elif check_key():
        note()
        note("## 1) 도시철도 전체노선정보 (subwayRouteInfo, mreaWideCd=01)")
        data, raw = call("trainUseInfo", "subwayRouteInfo", mreaWideCd="01")
        all_rows = list(rows_of(data))
        note(f"- 노선코드 없이: {len(all_rows)}행")
        if not all_rows:
            for ln in LN_CANDIDATES:
                d, _ = call("trainUseInfo", "subwayRouteInfo", mreaWideCd="01", lnCd=ln)
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

        def g(r, *ks):
            for k in ks:
                if r.get(k) not in (None, ""):
                    return str(r[k])
            return ""
        combos = collections.OrderedDict()
        for r in all_rows:
            combos.setdefault((g(r, "railOprIsttCd"), g(r, "lnCd"), g(r, "lnNm", "routNm")), []).append(r)
        note()
        note("| 운영기관 코드 | 노선 코드 | 노선명 | 역 수 | 첫 역 |")
        note("|---|---|---|---|---|")
        for (opr, ln, nm), rs in combos.items():
            note(f"| {opr} | {ln} | {nm} | {len(rs)} | {g(rs[0], 'stinCd')} {g(rs[0], 'stinNm')} |")
        note()
        note("## 2) 열차별 운행시각표 (subwayTimetable, 평일 dayCd=8, 노선별 가운데 역)")
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
