"""인천교통공사 인천1·2호선 첫차·막차 열차운행시각표 내려받기 → tools/incheon/raw/<IC1|IC2>_<WEEKDAY|HOLIDAY>.csv

1) 공공데이터포털 파일 데이터 (인증키 없이): 검색 결과와 알려진 번호(1호선 15051200·15051201)에서 제목으로 찾는다.
2) 공공데이터포털에 없는 호선(현재 인천2호선)은 인천교통공사 누리집 「열차운행시각표 > N호선 첫차/막차」 표를
   같은 열 구성(역명, 첫차(상선), 첫차(하선), 막차(상선), 막차(하선))으로 바꿔 저장한다.
기록: tools/incheon/raw/SOURCES.md (출처·데이터 번호·열·앞부분)
"""
import csv
import html as htmllib
import io
import os
import re
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"
KNOWN = [15051200, 15051201]
QUERIES = ["인천교통공사 첫차 막차", "인천2호선 첫차 막차", "인천1호선 첫차 막차", "인천교통공사 열차운행시각표"]
SITE = {"IC1": "https://www.ictr.or.kr/main/railway/guidance/timetable1_se.jsp",
        "IC2": "https://www.ictr.or.kr/main/railway/guidance/timetable2_se.jsp"}
HEAD = ["역명", "첫차(상선)", "첫차(하선)", "막차(상선)", "막차(하선)"]

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


def search_pks():
    """공공데이터포털 검색 결과 페이지에서 파일 데이터 번호를 모은다"""
    pks = set()
    for q in QUERIES:
        url = "https://www.data.go.kr/tcs/dss/selectDataSetList.do?" + urllib.parse.urlencode(
            {"dType": "FILE", "keyword": q, "perPage": 40, "currentPage": 1})
        try:
            _, _, page = _req(url, "https://www.data.go.kr/")
            found = set(int(x) for x in re.findall(r"/data/(\d{8})/fileData\.do", page.decode("utf-8", "replace")))
            note(f"- 검색 '{q}': {sorted(found)[:30]}")
            pks |= found
        except Exception as e:  # noqa: BLE001
            note(f"- 검색 '{q}' 실패: {e}")
    return pks


def from_portal(found):
    for pk in sorted(set(KNOWN) | search_pks()):
        page = f"https://www.data.go.kr/data/{pk}/fileData.do"
        try:
            _, _, raw = _req(page, page)
        except Exception:  # noqa: BLE001
            continue
        text = raw.decode("utf-8", "replace")
        m = re.search(r"<title>([^<]+)</title>", text)
        title = (m.group(1) if m else "").strip()
        kind = classify(title)
        if not kind or kind in found:
            continue
        atch = sorted(set(re.findall(r"FILE_\d{6,}", text)))
        note(f"- {pk}: {title} → {kind}, 첨부파일 {atch[:2]}")
        for a in atch:
            try:
                _, _, b = _req(f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn=1", page)
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
            found[kind] = f"공공데이터포털 {pk}"
            break


def _cells(tr):
    return [re.sub(r"\s+", " ", htmllib.unescape(re.sub(r"<[^>]+>", " ", c))).strip()
            for c in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", tr, re.S)]


def _time(s):
    m = re.match(r"^(\d{1,2})\s*:\s*(\d{2})(?:\s*:\s*(\d{2}))?$", s.strip())
    return f"{int(m.group(1)):02d}:{m.group(2)}:{m.group(3) or '00'}" if m else ""


def _table_rows(part):
    """표 행 → {역명: (평일 첫차, 평일 막차, 휴일 첫차, 휴일 막차)}"""
    out = {}
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", part, re.S):
        c = _cells(tr)
        if len(c) >= 5 and _time(c[1]):
            out[c[0]] = tuple(_time(x) for x in c[1:5])
    return out


def from_site(key, found):
    """인천교통공사 누리집 첫차/막차 표 (상선 표, 하선 표) → 평일·휴일 CSV"""
    url = SITE[key]
    try:
        _, _, raw = _req(url, url)
    except Exception as e:  # noqa: BLE001
        note(f"- {key} 누리집 실패: {e}")
        return
    page = raw.decode("utf-8", "replace")
    i = page.find("(하선)")
    j = page.find("(상선)")
    if i < 0 or j < 0:
        note(f"- {key} 누리집: 상선/하선 표를 찾지 못함")
        return
    up = _table_rows(page[j:i] if j < i else page[j:])
    down = _table_rows(page[i:] if j < i else page[i:j])
    names = list(up) or list(down)
    for k in down:
        if k not in names:
            names.append(k)
    for day, off in (("WEEKDAY", 0), ("HOLIDAY", 2)):
        if (key, day) in found:
            continue
        name = f"{key}_{day}.csv"
        with open(os.path.join(RAW, name), "w", encoding="utf-8") as f:
            f.write(f"# 인천교통공사 누리집 {key[-1]}호선 첫차/막차 ({url})\n")
            w = csv.writer(f, lineterminator="\n")
            w.writerow(HEAD)
            for n in names:
                u, d = up.get(n, ("",) * 4), down.get(n, ("",) * 4)
                w.writerow([n, u[off], d[off], u[off + 1], d[off + 1]])
        note(f"- {key} {day}: 누리집 표 → {name} (역 {len(names)}곳, 상선 {len(up)}·하선 {len(down)})")
        for n in names[:3]:
            note(f"    - {n}: 상선 {up.get(n)} / 하선 {down.get(n)}")
        found[(key, day)] = url


def main():
    os.makedirs(RAW, exist_ok=True)
    note("# 인천교통공사 첫차·막차 열차운행시각표")
    note()
    found = {}
    from_portal(found)
    for key in ("IC1", "IC2"):
        if any((key, d) not in found for d in ("WEEKDAY", "HOLIDAY")):
            from_site(key, found)
    note()
    for k, v in sorted(found.items()):
        note(f"- {k}: {v}")
    missing = [(l, d) for l in ("IC1", "IC2") for d in ("WEEKDAY", "HOLIDAY") if (l, d) not in found]
    note(f"- 못 찾음: {missing}")
    with open(os.path.join(RAW, "SOURCES.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
