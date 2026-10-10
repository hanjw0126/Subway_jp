"""OpenStreetMap(Overpass) 한강 중심선 → tools/seoul/raw/han_river.json

서울 구간의 한강은 동(미사)에서 서(김포)로 경도가 계속 줄어들며 흐른다. 그래서 경도 구간(BIN)별로
점의 위도를 평균하면 섬 주변의 갈래 물길이 섞여 있어도 한 줄의 중심선이 된다.
데이터: © OpenStreetMap contributors (ODbL). 실패하면 기존 파일을 유지한다.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "raw", "han_river.json")
ENDPOINTS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
QUERY = '[out:json][timeout:90];way["waterway"="river"]["name"="한강"](37.45,126.68,37.70,127.25);out geom;'
BIN = 0.004  # 경도 구간 (약 350 m)
MIN_POINTS = 30


def _fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    for url in ENDPOINTS:
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": "WorldWideMetro/1.0"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.load(r).get("elements", [])
        except Exception as e:  # noqa: BLE001
            print(f"[river] {url} 실패: {e}", file=sys.stderr)
    return []


def centerline(elements):
    bins = {}
    for el in elements:
        for g in el.get("geometry") or []:
            k = round(g["lon"] / BIN)
            bins.setdefault(k, []).append(g["lat"])
    # 동 → 서 (경도 내림차순)
    return [[round(sum(v) / len(v), 5), round(k * BIN, 5)] for k, v in sorted(bins.items(), reverse=True)]


def main():
    pts = centerline(_fetch())
    if len(pts) < MIN_POINTS:
        print(f"[river] 점이 너무 적음 ({len(pts)}) — 기존 파일 유지", file=sys.stderr)
        return 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"id": "han", "name": {"ko": "한강", "en": "Han River", "ja": "漢江"},
                   "source": "© OpenStreetMap contributors (ODbL)", "points": pts}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"[river] 한강 중심선 {len(pts)}점 → {os.path.relpath(OUT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
