"""공공데이터포털 파일 데이터 내려받기 (인증키 없이) — 서울 지하철 시간표·환승시간

  - 15098251 서울교통공사_서울 도시철도 열차운행시각표 (CSV, 이용허락범위 제한 없음)
      → $RUNNER_TEMP/timetable.csv (32MB, 저장소에는 넣지 않고 build_timetable.py 가 변환)
  - 15044419 서울교통공사_환승역거리 소요시간 정보 (CSV, 공공누리 제1유형)
      → tools/seoul/raw/transfer_times.csv (UTF-8)

내려받기 경로: 데이터 상세 페이지 → 첨부파일 ID(FILE_…) → /cmm/cmm/fileDownload.do
보고서: tools/seoul/raw/TIMETABLE_PROBE.md (파일명·크기·열 이름·행 수)
"""
import csv
import io
import os
import re
import sys
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
REPORT = os.path.join(RAW, "TIMETABLE_PROBE.md")
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"
TIMETABLE_PK = "15098251"
TRANSFER_PK = "15044419"

log = []
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def note(s):
    print(s)
    log.append(s)


def _req(url, referer, timeout=300):
    r = opener.open(urllib.request.Request(url, headers={"User-Agent": UA, "Referer": referer}), timeout=timeout)
    return r.status, dict(r.headers), r.read()


def download(pk):
    """공공데이터포털 파일 데이터 → (bytes, 파일명). 실패하면 (None, "")"""
    page = f"https://www.data.go.kr/data/{pk}/fileData.do"
    st, _, html = _req(page, page)
    atch = sorted(set(re.findall(r"FILE_\d{6,}", html.decode("utf-8", "replace"))))
    note(f"- {pk}: 상세 페이지 HTTP {st}, 첨부파일 ID {atch[:3]}")
    for a in atch:
        try:
            st, hd, b = _req(f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn=1", page)
            name = hd.get("Content-Disposition", "")
            try:
                name = name.encode("latin-1").decode("utf-8")
            except (UnicodeEncodeError, UnicodeDecodeError):
                pass
            m = re.search(r'filename="?([^";]+)', name)
            fname = m.group(1) if m else a
            note(f"- {pk}: {fname} — HTTP {st}, {len(b)} bytes")
            if len(b) > 1000:
                return b, fname
        except Exception as e:  # noqa: BLE001
            note(f"- {pk}: {a} 실패 — {e}")
    return None, ""


def decode(b):
    for enc in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            continue
    return b.decode("utf-8", "replace")


def main():
    os.makedirs(RAW, exist_ok=True)
    note("# 서울 지하철 시간표·환승시간 내려받기")
    note("")
    ok = True
    raw, fname = download(TIMETABLE_PK)
    if raw is None:
        ok = False
    else:
        text = decode(raw)
        out = os.path.join(os.environ.get("RUNNER_TEMP", "/tmp"), "timetable.csv")
        with open(out, "w", encoding="utf-8") as f:
            f.write(text)
        rows = list(csv.reader(io.StringIO(text)))
        note(f"- 열차운행시각표: {len(rows) - 1}행, 열 {rows[0] if rows else []} → {out}")
        with open(os.path.join(RAW, "timetable_source.txt"), "w", encoding="utf-8") as f:
            f.write(fname + "\n")
    raw, fname = download(TRANSFER_PK)
    if raw is None:
        ok = False
    else:
        text = decode(raw)
        with open(os.path.join(RAW, "transfer_times.csv"), "w", encoding="utf-8") as f:
            f.write("# " + fname + " (서울교통공사, 공공누리 제1유형)\n")
            f.write(text.replace("\r\n", "\n"))
        note(f"- 환승시간: {len(text.splitlines()) - 1}행 → tools/seoul/raw/transfer_times.csv")
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
