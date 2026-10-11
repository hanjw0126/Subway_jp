"""공공데이터포털 파일 데이터 내려받기 (인증키 없이) + 서울 지하철 데이터 시험

  - 15098251 서울교통공사_서울 도시철도 열차운행시각표 (CSV)
  - 15044419 서울교통공사_환승역거리 소요시간 정보 (CSV)

내려받기 경로: 데이터 상세 페이지 → 첨부파일 ID(FILE_…) → /cmm/cmm/fileDownload.do
결과: tools/seoul/raw/TIMETABLE_PROBE.md (크기·열 이름·앞부분·값 분포), 받은 파일은 $RUNNER_TEMP/<PK>.csv
"""
import collections
import csv
import io
import os
import re
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
REPORT = os.path.join(HERE, "raw", "TIMETABLE_PROBE.md")
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"
DATASETS = {
    "15098251": "서울교통공사_서울 도시철도 열차운행시각표",
    "15044419": "서울교통공사_환승역거리 소요시간 정보",
}

log = []
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def note(s):
    print(s)
    log.append(s)


def _req(url, referer, timeout=300):
    r = opener.open(urllib.request.Request(url, headers={"User-Agent": UA, "Referer": referer}), timeout=timeout)
    return r.status, dict(r.headers), r.read()


def download(pk):
    """공공데이터포털 파일 데이터 → bytes (실패하면 None)"""
    page = f"https://www.data.go.kr/data/{pk}/fileData.do"
    st, _, html = _req(page, page)
    atch = sorted(set(re.findall(r"FILE_\d{6,}", html.decode("utf-8", "replace"))))
    note(f"- 상세 페이지 HTTP {st}, 첨부파일 ID {atch[:3]}")
    for a in atch:
        try:
            st, hd, b = _req(f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn=1", page)
            name = hd.get("Content-Disposition", "")
            try:
                name = name.encode("latin-1").decode("utf-8")
            except (UnicodeEncodeError, UnicodeDecodeError):
                pass
            note(f"- 파일 {a} → HTTP {st}, {len(b)} bytes, {name[:120]}")
            if len(b) > 1000:
                return b
        except Exception as e:  # noqa: BLE001
            note(f"- 파일 {a} 실패: {e}")
    return None


def decode(b):
    for enc in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return b.decode(enc), enc
        except UnicodeDecodeError:
            continue
    return b.decode("utf-8", "replace"), "utf-8?"


def summarize(raw, max_rows=8):
    text, enc = decode(raw)
    rows = list(csv.reader(io.StringIO(text)))
    head = rows[0] if rows else []
    note(f"- 인코딩 {enc}, 행 {len(rows) - 1}, 열 {head}")
    note(f"- 앞 {max_rows}행:")
    for r in rows[1:max_rows + 1]:
        note(f"  - {r}")
    idx = {h: i for i, h in enumerate(head)}
    for col in head:
        vals = collections.Counter(r[idx[col]] for r in rows[1:] if len(r) > idx[col])
        if 1 < len(vals) <= 40:
            note(f"- {col}: {dict(vals.most_common(40))}")
    return head, rows[1:]


def main():
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    note("# 공공데이터포털 파일 내려받기 시험 (서울 지하철)")
    for pk, title in DATASETS.items():
        note("")
        note(f"## {pk} {title}")
        try:
            raw = download(pk)
        except Exception as e:  # noqa: BLE001
            note(f"- 실패: {e}")
            raw = None
        if raw is None:
            note("- 결과: 받지 못함")
            continue
        with open(os.path.join(os.environ.get("RUNNER_TEMP", "/tmp"), f"{pk}.csv"), "wb") as f:
            f.write(raw)
        head, rows = summarize(raw, max_rows=8 if pk == "15098251" else 40)
        if pk == "15098251":
            # 역사코드별 역명·호선 (앱 역 목록과 연결할 수 있는지 확인용)
            ih = {h: i for i, h in enumerate(head)}
            st = sorted({(r[ih["호선"]], r[ih["역사코드"]], r[ih["역사명"]]) for r in rows if len(r) == len(head)})
            note(f"- 역 {len(st)}곳 (호선, 역사코드, 역사명) 앞 30: {st[:30]}")
            per_line = collections.Counter(s[0] for s in st)
            note(f"- 호선별 역 수: {dict(sorted(per_line.items()))}")
            trains = collections.Counter((r[ih["호선"]], r[ih["주중주말"]], r[ih["열차코드"]]) for r in rows if len(r) == len(head))
            note(f"- (호선, 요일, 열차코드) 묶음 {len(trains)}개, 정차 수 최대 {max(trains.values())}")
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
