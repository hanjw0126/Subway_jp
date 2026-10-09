"""한국어 위키백과(위키데이터) 기준 일본 역명.

  python tools/ko_wiki.py   → 위키데이터에서 일본 역 항목의 일본어·한국어 이름과 좌표를 받아
                              tools/seed/ko_wiki.json 에 저장 (네트워크 필요, CI 전용)
  lookup(index, ja, lat, lon) → 같은 일본어 이름이면서 2 km 이내인 가장 가까운 항목의 한국어 표기

위키데이터는 CC0 라 자유롭게 쓸 수 있다. 한국어 표기는 대체로 국립국어원 외래어 표기법을 따른다.
파일은 한 줄에 한 역 [일본어, 위도, 경도, 한국어] 형식이라 변경 사항을 diff 로 확인할 수 있다.
"""
import json
import math
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "seed", "ko_wiki.json")
ENDPOINT = "https://query.wikidata.org/sparql"
USER_AGENT = "WorldWideMetro/1.0 (https://github.com/hanjw0126/Subway_jp)"
MAX_DIST_M = 2000
MIN_ROWS = 500  # 이보다 적게 받으면 실패로 보고 기존 파일을 유지한다

_SELECT = """
SELECT ?ja ?ko ?coord WHERE {{
  ?item wdt:P17 wd:Q17 .
  {type_clause}
  ?item wdt:P625 ?coord ;
        rdfs:label ?ja ;
        rdfs:label ?ko .
  FILTER(LANG(?ja) = "ja")
  FILTER(LANG(?ko) = "ko")
}}
"""
# 기본: 철도역(Q55488)의 하위 분류 전부. 시간 초과면 대표 분류만(철도역·지하철역·노면전차 정류장)
QUERIES = [
    _SELECT.format(type_clause="?item wdt:P31/wdt:P279* wd:Q55488 ."),
    _SELECT.format(type_clause="VALUES ?type { wd:Q55488 wd:Q928830 wd:Q2175765 } ?item wdt:P31 ?type ."),
]


def norm_ja(s):
    """新宿駅 / 新宿駅 (東京都) → 新宿"""
    s = re.sub(r"[（(].*?[）)]", "", s or "").strip()
    return s[:-1] if len(s) > 1 and s.endswith("駅") else s


def norm_ko(s):
    """신주쿠역 / 신주쿠역 (도쿄도) → 신주쿠"""
    s = re.sub(r"\(.*?\)", "", s or "").strip()
    if len(s) > 1 and s.endswith("역"):
        s = s[:-1]
    return s.strip()


def same_ko(a, b):
    """띄어쓰기·가운뎃점 차이는 같은 표기로 본다"""
    def k(x):
        return re.sub(r"[\s·・\-]", "", x or "")
    return k(a) == k(b)


def _dist_m(la1, lo1, la2, lo2):
    la1, lo1, la2, lo2 = map(math.radians, (la1, lo1, la2, lo2))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def load_index(path=DATA):
    """일본어 이름 → [(위도, 경도, 한국어), ...]. 파일이 없으면 빈 사전"""
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        rows = json.load(f)
    idx = {}
    for ja, lat, lon, ko in rows:
        idx.setdefault(ja, []).append((lat, lon, ko))
    return idx


def lookup(index, ja, lat, lon):
    """같은 일본어 이름이면서 MAX_DIST_M 이내인 가장 가까운 항목의 한국어 표기 (없으면 None)"""
    best = None
    for la, lo, ko in index.get(norm_ja(ja), ()):
        d = _dist_m(lat, lon, la, lo)
        if d <= MAX_DIST_M and (best is None or d < best[0]):
            best = (d, ko)
    return best[1] if best else None


def _query(sparql):
    url = ENDPOINT + "?" + urllib.parse.urlencode({"query": sparql, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.load(r)["results"]["bindings"]


def fetch():
    rows = None
    for q in QUERIES:
        try:
            rows = _query(q)
            if len(rows) >= MIN_ROWS:
                break
            print(f"[ko_wiki] 결과가 너무 적음 ({len(rows)}) → 다음 쿼리", file=sys.stderr)
        except Exception as e:  # noqa: BLE001 — 시간 초과 등은 다음 쿼리로
            print(f"[ko_wiki] 쿼리 실패: {e}", file=sys.stderr)
            rows = None
    if not rows or len(rows) < MIN_ROWS:
        print("[ko_wiki] 위키데이터에서 충분한 결과를 받지 못해 기존 파일을 유지합니다", file=sys.stderr)
        return 1
    out = set()
    for b in rows:
        m = re.match(r"Point\(([-\d.eE]+) ([-\d.eE]+)\)", b["coord"]["value"])
        if not m:
            continue
        lon, lat = float(m.group(1)), float(m.group(2))
        ja, ko = norm_ja(b["ja"]["value"]), norm_ko(b["ko"]["value"])
        if ja and ko:
            out.add((ja, round(lat, 4), round(lon, 4), ko))
    lines = [json.dumps(list(r), ensure_ascii=False) for r in sorted(out)]
    with open(DATA, "w", encoding="utf-8") as f:
        f.write("[\n" + ",\n".join(lines) + "\n]\n")
    print(f"[ko_wiki] {len(lines)} 개 역 저장 → {os.path.relpath(DATA)}")
    return 0


if __name__ == "__main__":
    sys.exit(fetch())
