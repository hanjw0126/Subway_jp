"""서울 열린데이터광장 원본(tools/seoul/raw) + 아래 노선 규칙 → tools/seed/seoul.json (일본 seed 와 같은 형식)

- 노선별 역 순서는 외부코드(FR_CODE) 목록을 직접 정해 둔다. 지선은 같은 색의 별도 노선(예: 1호선 경부선).
- 좌표는 역사마스터에서 같은 역명을 찾되, 그 노선 구간(ROUTE)을 우선하고 앞 역에서 가까운 후보를 고른다.
  개명된 역은 별칭으로 찾고, 그래도 없으면 앞뒤 역 사이로 보간한다.
- 한국어 역명은 원본 그대로, 환승 묶음은 '역'을 뗀 한국어 역명(서울역 = GTX 서울)으로 한다.
"""
import collections
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
RAW = os.path.join(HERE, "raw")
OUT = os.path.join(TOOLS, "seed", "seoul.json")
OPERATOR = "kr:Seoul"


def rng(prefix, a, b, width=0):
    """외부코드 구간 (양 끝 포함, 내림차순 가능). 원본에 없는 코드는 나중에 빠진다"""
    step = 1 if b >= a else -1
    return [f"{prefix}{str(n).zfill(width)}" for n in range(a, b + step, step)]


# 노선 규칙. num = 원본 LINE_NUM, routes = 좌표를 고를 때 우선할 역사마스터 ROUTE,
# paths = [{id(지선 접미사), ko/en/ja(지선 이름), order(FR_CODE 순서), loop, extra(추가 간선 FR_CODE 쌍)}]
LINES = [
    {"key": "1", "ko": "1호선", "en": "Line 1", "ja": "1号線", "color": "#0052A4", "code": "1", "num": "01호선",
     "routes": ["1호선", "경원선", "경부선", "경인선", "장항선", "중앙선"],
     "paths": [
         {"order": ["100-3", "100-2", "100-1", "100"] + rng("", 101, 161)},
         {"id": "Gyeongbu", "ko": "경부선", "en": "Gyeongbu", "ja": "京釜線", "order": ["141"] + rng("P", 142, 177)},
         {"id": "Gwangmyeong", "ko": "광명", "en": "Gwangmyeong", "ja": "光明", "order": ["P144", "P144-1"]},
         {"id": "Seodongtan", "ko": "서동탄", "en": "Seodongtan", "ja": "西東灘", "order": ["P157", "P157-1"]},
     ]},
    {"key": "2", "ko": "2호선", "en": "Line 2", "ja": "2号線", "color": "#00A84D", "code": "2", "num": "02호선",
     "routes": ["2호선"],
     "paths": [
         {"order": rng("", 201, 243), "loop": True},
         {"id": "Seongsu", "ko": "성수지선", "en": "Seongsu Branch", "ja": "聖水支線",
          "order": ["211", "211-1", "211-2", "211-3", "211-4"]},
         {"id": "Sinjeong", "ko": "신정지선", "en": "Sinjeong Branch", "ja": "新亭支線",
          "order": ["234", "234-1", "234-2", "234-3", "234-4"]},
     ]},
    {"key": "3", "ko": "3호선", "en": "Line 3", "ja": "3号線", "color": "#EF7C1C", "code": "3", "num": "03호선",
     "routes": ["3호선", "일산선"], "paths": [{"order": rng("", 309, 352)}]},
    {"key": "4", "ko": "4호선", "en": "Line 4", "ja": "4号線", "color": "#00A5DE", "code": "4", "num": "04호선",
     "routes": ["4호선", "진접선", "과천선", "안산선"], "paths": [{"order": rng("", 405, 456)}]},
    {"key": "5", "ko": "5호선", "en": "Line 5", "ja": "5号線", "color": "#996CAC", "code": "5", "num": "05호선",
     "routes": ["5호선"],
     "paths": [
         {"order": rng("", 510, 558)},
         {"id": "Macheon", "ko": "마천", "en": "Macheon", "ja": "馬川", "order": ["548"] + rng("P", 549, 555)},
     ]},
    # 6호선: 응암순환(응암→역촌→불광→독바위→연신내→구산→응암) + 응암~신내
    {"key": "6", "ko": "6호선", "en": "Line 6", "ja": "6号線", "color": "#CD7C2F", "code": "6", "num": "06호선",
     "routes": ["6호선"],
     "paths": [{"order": ["615", "614", "613", "612", "611", "610"] + rng("", 616, 648), "extra": [["615", "610"]]}]},
    {"key": "7", "ko": "7호선", "en": "Line 7", "ja": "7号線", "color": "#747F00", "code": "7", "num": "07호선",
     "routes": ["7호선", "7호선(인천)"], "paths": [{"order": rng("", 709, 761)}]},
    {"key": "8", "ko": "8호선", "en": "Line 8", "ja": "8号線", "color": "#E6186C", "code": "8", "num": "08호선",
     "routes": ["8호선", "별내선"], "paths": [{"order": rng("", 804, 827)}]},
    {"key": "9", "ko": "9호선", "en": "Line 9", "ja": "9号線", "color": "#BDB092", "code": "9", "num": "09호선",
     "routes": ["9호선", "9호선(연장)"], "paths": [{"order": rng("", 901, 938)}]},
    # 경의중앙선: 지평 … 용산 → 효창공원앞 → 공덕 … 임진강, 지선 서울역 → 신촌 → 가좌
    {"key": "KJ", "ko": "경의중앙선", "en": "Gyeongui-Jungang Line", "ja": "京義・中央線", "color": "#77C4A3",
     "code": "경의", "num": "경의선", "routes": ["경의중앙선", "중앙선", "경원선", "경부선"],
     "paths": [
         {"order": rng("K", 138, 110) + ["K826"] + rng("K", 312, 337)},
         {"id": "Seoul", "ko": "서울역", "en": "Seoul Station", "ja": "ソウル駅", "order": ["P313", "P312", "K315"]},
     ]},
    {"key": "AREX", "ko": "공항철도", "en": "AREX", "ja": "空港鉄道", "color": "#0090D2", "code": "공항",
     "num": "공항철도", "routes": ["공항철도1호선"],
     "paths": [{"order": ["A01", "A02", "A03", "A04", "A042", "A05", "A06", "A07", "A071", "A072",
                         "A08", "A09", "A10", "A11"]}]},
    {"key": "GC", "ko": "경춘선", "en": "Gyeongchun Line", "ja": "京春線", "color": "#0C8E72", "code": "경춘",
     "num": "경춘선", "routes": ["경춘선", "중앙선", "경원선", "1호선"],
     "paths": [
         {"order": rng("P", 117, 140)},
         {"id": "Kwangwoon", "ko": "광운대", "en": "Kwangwoon Univ.", "ja": "光運大", "order": ["P116", "P120"]},
     ]},
    {"key": "SB", "ko": "수인분당선", "en": "Suin-Bundang Line", "ja": "水仁・盆唐線", "color": "#FABE00",
     "code": "수인", "num": "수인분당선", "routes": ["분당선", "수인선", "안산선", "경부선", "경원선"],
     "paths": [{"order": rng("K", 209, 272)}]},
    {"key": "SBD", "ko": "신분당선", "en": "Shinbundang Line", "ja": "新盆唐線", "color": "#D4003B", "code": "신분",
     "num": "신분당선", "routes": ["신분당선", "신분당선(연장)", "신분당선(연장2)"],
     "paths": [{"order": rng("D", 4, 19)}]},
    {"key": "GG", "ko": "경강선", "en": "Gyeonggang Line", "ja": "京江線", "color": "#003DA5", "code": "경강",
     "num": "경강선", "routes": ["경강선"], "paths": [{"order": rng("K", 409, 420)}]},
    {"key": "WS", "ko": "서해선", "en": "Seohae Line", "ja": "西海線", "color": "#8FC31F", "code": "서해",
     "num": "서해선", "routes": ["서해선", "경의중앙선", "일산선"], "paths": [{"order": rng("S", 7, 28, 2)}]},
    {"key": "UI", "ko": "우이신설선", "en": "Ui LRT", "ja": "牛耳新設線", "color": "#B0CE18", "code": "우이",
     "num": "우이신설경전철", "routes": ["우이신설선"], "paths": [{"order": rng("", 941, 953)}]},
    {"key": "SL", "ko": "신림선", "en": "Sillim Line", "ja": "新林線", "color": "#6789CA", "code": "신림",
     "num": "신림선", "routes": ["신림선"], "paths": [{"order": rng("S", 401, 411)}]},
    {"key": "GTXA", "ko": "GTX-A", "en": "GTX-A", "ja": "GTX-A", "color": "#9A6292", "code": "A", "num": "GTX-A",
     "routes": ["수도권 광역급행철도"], "paths": [{"order": rng("X", 101, 111)}]},
]

# 역사마스터에 예전 이름으로 남아 있거나 표기가 다른 역
ALIASES = {
    "자양": ["뚝섬유원지"],
    "4·19민주묘지": ["4.19민주묘지", "4·19 민주묘지"],
    "운정중앙": ["운정"],
    "평택지제": ["지제"],
    "한국항공대": ["화전"],
}


def norm(n):
    n = re.sub(r"\(.*?\)", "", n or "").strip()
    return n[:-1] if len(n) > 1 and n.endswith("역") else n


def _km(a, b):
    if a is None or b is None:
        return 0.0
    dx = (a[1] - b[1]) * math.cos(math.radians((a[0] + b[0]) / 2)) * 111.32
    dy = (a[0] - b[0]) * 110.57
    return math.hypot(dx, dy)


def _load(name):
    with open(os.path.join(RAW, f"{name}.json"), encoding="utf-8") as f:
        return json.load(f)["rows"]


def _coords(names, routes, cand, prev):
    cs = [x for n in names for x in cand.get(n, [])]
    pref = [x for x in cs if x[0] in routes] or cs
    if not pref:
        return None
    best = min(pref, key=lambda x: _km(prev, (x[1], x[2])))
    return (best[1], best[2])


def _fill_gaps(pts):
    """좌표가 없는 역: 앞뒤 역 사이로 선형 보간 (한쪽만 있으면 그 옆에 조금 떼어 둔다)"""
    known = [i for i, p in enumerate(pts) if p is not None]
    if not known:
        return pts
    out = list(pts)
    for i, p in enumerate(pts):
        if p is not None:
            continue
        lo = max((k for k in known if k < i), default=None)
        hi = min((k for k in known if k > i), default=None)
        if lo is not None and hi is not None:
            t = (i - lo) / (hi - lo)
            out[i] = (pts[lo][0] + (pts[hi][0] - pts[lo][0]) * t, pts[lo][1] + (pts[hi][1] - pts[lo][1]) * t)
        else:
            k = lo if lo is not None else hi
            d = 0.006 * (i - k)
            out[i] = (pts[k][0] + d, pts[k][1] + d)
    return out


def build():
    rows = _load("SearchSTNBySubwayLineInfo")
    master = _load("subwayStationMaster")
    by_code = {(r["LINE_NUM"], r["FR_CODE"]): r for r in rows}
    cand = collections.defaultdict(list)
    for m in master:
        try:
            cand[norm(m["BLDN_NM"])].append((m["ROUTE"], float(m["LAT"]), float(m["LOT"])))
        except (TypeError, ValueError):
            continue
    lines = []
    for L in LINES:
        for p in L["paths"]:
            codes = [c for c in p["order"] if (L["num"], c) in by_code]
            if len(codes) < 2:
                continue
            recs = [by_code[(L["num"], c)] for c in codes]
            pts, prev = [], None
            for r in recs:
                base = norm(r["STATION_NM"])
                pt = _coords([base] + ALIASES.get(base, []), L["routes"], cand, prev)
                if pt is not None:
                    prev = pt
                pts.append(pt)
            pts = _fill_gaps(pts)
            lid = f"kr.Seoul.{L['key']}" + (f".{p['id']}" if p.get("id") else "")
            stations = []
            for r, (lat, lon) in zip(recs, pts):
                ko = r["STATION_NM"].strip()
                en = (r.get("STATION_NM_ENG") or "").strip() or ko
                ja = (r.get("STATION_NM_JPN") or "").strip()
                # [코드, 영문, 일문, 가나, 위도, 경도, 역 ID, 한글명, 환승 묶음 키]
                stations.append([r["FR_CODE"], en, ja, "", round(lat, 6), round(lon, 6),
                                 f"{lid}.{r['FR_CODE']}", ko, f"kr.{norm(ko)}"])
            en_of = {r["FR_CODE"]: s[1] for r, s in zip(recs, stations)}
            first, last = recs[0]["STATION_NM"], recs[-1]["STATION_NM"]
            first_en, last_en = stations[0][1], stations[-1][1]
            first_ja, last_ja = stations[0][2], stations[-1][2]
            if p.get("loop"):
                asc = {"id": f"{lid}:asc", "ko": "외선순환", "en": "Outer Circle", "ja": "外回り"}
                desc = {"id": f"{lid}:desc", "ko": "내선순환", "en": "Inner Circle", "ja": "内回り"}
            else:
                asc = {"id": f"{lid}:asc", "ko": last, "en": last_en, "ja": last_ja}
                desc = {"id": f"{lid}:desc", "ko": first, "en": first_en, "ja": first_ja}
            name = {"ko": L["ko"], "en": L["en"], "ja": L["ja"]}
            if p.get("id"):
                name = {"ko": f"{L['ko']} ({p['ko']})", "en": f"{L['en']} ({p['en']})", "ja": f"{L['ja']}（{p['ja']}）"}
            line = {"id": lid, "operator": OPERATOR, "code": L["code"], "name": name, "color": L["color"],
                    "stations": stations, "asc": asc, "desc": desc, "loop": bool(p.get("loop")), "source": "seoul"}
            extra = [[en_of[a], en_of[b]] for a, b in p.get("extra", []) if a in en_of and b in en_of]
            if extra:
                line["extraEdges"] = extra
            lines.append(line)
    return {"regionId": "seoul", "transferWalkSec": 180, "lines": lines, "extraTransfers": []}


def main():
    if not os.path.exists(os.path.join(RAW, "SearchSTNBySubwayLineInfo.json")):
        print("[seoul] 원본 없음 — tools/seoul/fetch_raw.py 를 먼저 실행하세요", file=sys.stderr)
        return 1
    seed = build()
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(seed, f, ensure_ascii=False, indent=1)
        f.write("\n")
    n = sum(len(L["stations"]) for L in seed["lines"])
    print(f"[seoul] seed: lines={len(seed['lines'])} stations={n} → {os.path.relpath(OUT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
