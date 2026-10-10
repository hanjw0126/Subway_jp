"""서울 열린데이터광장 일반 API(SEOUL_NORMAL_SECRET) → tools/seoul/raw/<서비스>.json (원본 그대로)

  SEOUL_NORMAL_SECRET=... python tools/seoul/fetch_raw.py

데이터는 공공누리 제1유형(출처 표시)이다. 키는 파일·로그 어디에도 남기지 않는다.
서비스 하나가 실패해도 나머지는 저장하고, 실패한 서비스는 기존 파일을 그대로 둔다.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
BASE = "http://openapi.seoul.go.kr:8088/{key}/json/{svc}/{start}/{end}/"
PAGE = 1000

# 서비스 → 설명
SERVICES = {
    "SearchSTNBySubwayLineInfo": "서울시 노선별 지하철역 정보 (역코드·외부코드·영문/일문/중문 역명)",
    "subwayStationMaster": "서울시 역사마스터 정보 (역사 ID·노선·위도·경도)",
}


def _get(key, svc, start, end):
    url = BASE.format(key=urllib.parse.quote(key, safe=""), svc=svc, start=start, end=end)
    req = urllib.request.Request(url, headers={"User-Agent": "WorldWideMetro/1.0"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            msg = str(e).replace(key, "***")
            print(f"[seoul] {svc} {start}-{end} 실패({attempt + 1}): {msg}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    return None


def fetch_service(key, svc):
    rows, total, start = [], None, 1
    while total is None or start <= total:
        data = _get(key, svc, start, start + PAGE - 1)
        if data is None:
            return None
        body = data.get(svc)
        if not body:
            print(f"[seoul] {svc}: 응답 오류 {json.dumps(data, ensure_ascii=False)[:300]}", file=sys.stderr)
            return None
        total = int(body.get("list_total_count") or 0)
        page = body.get("row") or []
        rows += page
        if not page:
            break
        start += PAGE
    return rows


def main():
    key = os.environ.get("SEOUL_NORMAL_SECRET", "").strip()
    if not key:
        print("[seoul] SEOUL_NORMAL_SECRET 이 없습니다", file=sys.stderr)
        return 1
    os.makedirs(RAW, exist_ok=True)
    ok = 0
    for svc, desc in SERVICES.items():
        rows = fetch_service(key, svc)
        if rows is None:
            continue
        out = os.path.join(RAW, f"{svc}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump({"service": svc, "description": desc,
                       "source": "서울 열린데이터광장 (공공누리 제1유형)", "rows": rows},
                      f, ensure_ascii=False, indent=1, sort_keys=True)
            f.write("\n")
        print(f"[seoul] {svc}: {len(rows)}행 → {os.path.relpath(out)}")
        ok += 1
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
