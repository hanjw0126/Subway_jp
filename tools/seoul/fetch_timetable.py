"""서울교통공사_서울 도시철도 열차운행시각표 (공공데이터포털 15098251, CSV) 내려받기 시험·수집

파일 데이터라 인증키 없이 포털의 파일 내려받기 경로로 받는다.
  1) 데이터 상세 페이지에서 uddi(상세 ID)를 찾는다
  2) 내려받기 준비 요청 → atchFileId 를 받는다
  3) 파일을 받는다 (없으면 DATA_GO_KR_KEY 로 자동변환 API 시도)
결과: tools/seoul/raw/TIMETABLE_PROBE.md (경로·크기·열 이름·앞부분·호선별 행 수),
      성공하면 $RUNNER_TEMP/timetable.csv (저장소에는 변환 결과만 넣는다)
"""
import collections
import csv
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
REPORT = os.path.join(HERE, "raw", "TIMETABLE_PROBE.md")
PK = "15098251"
PAGE = f"https://www.data.go.kr/data/{PK}/fileData.do"
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"

log = []


def note(s):
    print(s)
    log.append(s)


opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def req(url, data=None, headers=None, timeout=120):
    h = {"User-Agent": UA, "Referer": PAGE}
    h.update(headers or {})
    body = urllib.parse.urlencode(data).encode() if isinstance(data, dict) else data
    r = opener.open(urllib.request.Request(url, data=body, headers=h), timeout=timeout)
    return r.status, dict(r.headers), r.read()


def decode(b):
    for enc in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return b.decode(enc), enc
        except UnicodeDecodeError:
            continue
    return b.decode("utf-8", "replace"), "utf-8?"


def try_file_download():
    st, _, html = req(PAGE)
    text = html.decode("utf-8", "replace")
    note(f"- 상세 페이지 HTTP {st}, {len(html)} bytes")
    uddis = sorted(set(re.findall(r"uddi:[0-9a-fA-F-]{20,}", text)))
    note(f"- uddi 후보: {uddis[:5]}")
    funcs = sorted(set(re.findall(r"fn_fileDataDown\([^)]*\)", text)))[:5]
    note(f"- 내려받기 함수 호출: {funcs}")
    atch = sorted(set(re.findall(r"FILE_\d{6,}", text)))
    note(f"- 페이지의 atchFileId 후보: {atch[:5]}")
    cands = []
    for u in uddis[:3]:
        for method in ("POST", "GET"):
            url = "https://www.data.go.kr/tcs/dss/selectFileDataDownload.do"
            params = {"publicDataPk": PK, "publicDataDetailPk": u, "fileDetailSn": "1", "recommendDataYn": ""}
            try:
                if method == "POST":
                    st, _, b = req(url, params, {"X-Requested-With": "XMLHttpRequest"})
                else:
                    st, _, b = req(url + "?" + urllib.parse.urlencode(params), headers={"X-Requested-With": "XMLHttpRequest"})
                s = b.decode("utf-8", "replace")
                note(f"- selectFileDataDownload {method} {u[:20]}… → HTTP {st}: {s[:300]!r}")
                for m in re.finditer(r'"atchFileId"\s*:\s*"([^"]+)"', s):
                    sn = re.search(r'"fileDetailSn"\s*:\s*"?(\d+)', s)
                    cands.append((m.group(1), sn.group(1) if sn else "1"))
            except Exception as e:  # noqa: BLE001
                note(f"- selectFileDataDownload {method} 실패: {e}")
    cands += [(a, "1") for a in atch]
    for a, sn in cands:
        url = f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn={sn}"
        try:
            st, hd, b = req(url, timeout=300)
            note(f"- 파일 받기 {a}/{sn} → HTTP {st}, {len(b)} bytes, {hd.get('Content-Type')}, {hd.get('Content-Disposition', '')[:120]}")
            if len(b) > 100_000:
                return b
        except Exception as e:  # noqa: BLE001
            note(f"- 파일 받기 {a} 실패: {e}")
    return None


def try_odcloud(key):
    """공공데이터포털 파일 자동변환 API (인증키 필요)"""
    try:
        st, _, b = req(f"https://infuser.odcloud.kr/oas/docs?namespace={PK}/v1")
        doc = json.loads(b)
        paths = list(doc.get("paths", {}))
        note(f"- 자동변환 API 경로: {paths[:5]}")
    except Exception as e:  # noqa: BLE001
        note(f"- 자동변환 API 문서 실패: {e}")
        return None
    if not key:
        note("- DATA_GO_KR_KEY 없음 → 자동변환 API 호출은 건너뜀")
        return None
    rows, page = [], 1
    path = paths[-1]
    while True:
        q = urllib.parse.urlencode({"page": page, "perPage": 10000, "serviceKey": key, "returnType": "JSON"})
        st, _, b = req(f"https://api.odcloud.kr/api{path}?{q}", timeout=300)
        d = json.loads(b)
        data = d.get("data") or []
        rows += data
        note(f"- 자동변환 API page {page}: {len(data)}행 (total {d.get('totalCount')})")
        if not data or len(rows) >= int(d.get("totalCount") or 0):
            break
        page += 1
    if not rows:
        return None
    out = io.StringIO()
    w = csv.DictWriter(out, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)
    return out.getvalue().encode("utf-8")


def summarize(raw):
    text, enc = decode(raw)
    rows = list(csv.reader(io.StringIO(text)))
    head = rows[0] if rows else []
    note(f"- 인코딩 {enc}, 행 {len(rows) - 1}, 열 {head}")
    note("- 앞 5행:")
    for r in rows[1:6]:
        note(f"  - {r}")
    # 호선·요일·방향별 행 수 (열 이름을 모르니 후보로 찾는다)
    idx = {h: i for i, h in enumerate(head)}
    for col in head:
        vals = collections.Counter(r[idx[col]] for r in rows[1:] if len(r) > idx[col])
        if 1 < len(vals) <= 40:
            note(f"- {col}: {dict(vals.most_common(40))}")


def main():
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    note("# 서울 도시철도 열차운행시각표 내려받기 시험")
    note("")
    raw = None
    try:
        raw = try_file_download()
    except Exception as e:  # noqa: BLE001
        note(f"- 파일 내려받기 실패: {e}")
    if raw is None:
        raw = try_odcloud(os.environ.get("DATA_GO_KR_KEY", "").strip())
    if raw is not None:
        out = os.path.join(os.environ.get("RUNNER_TEMP", "/tmp"), "timetable.csv")
        with open(out, "wb") as f:
            f.write(raw)
        note(f"- 저장: {out} ({len(raw)} bytes)")
        summarize(raw)
    else:
        note("- 결과: 받지 못함")
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
