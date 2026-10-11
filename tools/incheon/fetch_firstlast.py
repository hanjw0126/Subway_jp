"""인천교통공사 인천1·2호선 첫차·막차 열차운행시각표 (공공데이터포털 파일 데이터) 내려받기

파일 데이터라 인증키 없이 받는다. 1호선은 15051200(휴일)·15051201(평일), 2호선은 그 근처 번호를
차례로 확인해 제목으로 찾는다. 결과:
  tools/incheon/raw/<호선>_<WEEKDAY|HOLIDAY>.csv (UTF-8), tools/incheon/raw/SOURCES.md (데이터 번호·파일명·열)
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
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"
CANDIDATES = list(range(15051195, 15051215))

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
log = []


def note(s=""):
    print(s)
    log.append(s)


def _req(url, referer, timeout=120):
    r = opener.open(urllib.request.Request(url, headers={"User-Agent": UA, "Referer": referer}), timeout=timeout)
    return r.status, dict(r.headers), r.read()


def _decode(b):
    for enc in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            continue
    return b.decode("utf-8", "replace")


def classify(title):
    """제목 → (호선, 요일) 또는 None"""
    if "인천교통공사" not in title or "첫차" not in title:
        return None
    line = "IC1" if "인천1호선" in title else "IC2" if "인천2호선" in title else None
    day = "HOLIDAY" if "휴일" in title else "WEEKDAY" if "평일" in title else None
    return (line, day) if line and day else None


def main():
    os.makedirs(RAW, exist_ok=True)
    note("# 인천교통공사 첫차·막차 열차운행시각표 (공공데이터포털)")
    note()
    found = {}
    for pk in CANDIDATES:
        page = f"https://www.data.go.kr/data/{pk}/fileData.do"
        try:
            st, _, html = _req(page, page)
        except Exception as e:  # noqa: BLE001
            note(f"- {pk}: 실패 {e}")
            continue
        text = html.decode("utf-8", "replace")
        m = re.search(r"<title>([^<]+)</title>", text)
        title = (m.group(1) if m else "").strip()
        kind = classify(title)
        if not kind:
            continue
        atch = sorted(set(re.findall(r"FILE_\d{6,}", text)))
        note(f"- {pk}: {title} → {kind}, 첨부파일 {atch[:2]}")
        for a in atch:
            try:
                st, hd, b = _req(f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn=1", page)
            except Exception as e:  # noqa: BLE001
                note(f"  - {a} 실패 {e}")
                continue
            if len(b) < 200:
                continue
            csv_text = _decode(b).replace("\r\n", "\n")
            rows = list(csv.reader(io.StringIO(csv_text)))
            name = f"{kind[0]}_{kind[1]}.csv"
            with open(os.path.join(RAW, name), "w", encoding="utf-8") as f:
                f.write(f"# {title} (공공데이터포털 {pk})\n")
                f.write(csv_text)
            note(f"  - 저장 {name}: {len(rows) - 1}행, 열 {rows[0] if rows else []}")
            for r in rows[1:4]:
                note(f"    - {r}")
            found[kind] = pk
            break
    note()
    missing = [(l, d) for l in ("IC1", "IC2") for d in ("WEEKDAY", "HOLIDAY") if (l, d) not in found]
    note(f"- 찾음: {sorted(found)}")
    note(f"- 못 찾음: {missing}")
    with open(os.path.join(RAW, "SOURCES.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
