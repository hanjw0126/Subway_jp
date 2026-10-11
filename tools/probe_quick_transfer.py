"""빠른 환승(환승하기 좋은 칸·문) 데이터 시험 → docs/probe/QUICK_TRANSFER_PROBE.md

- 한국: 국토교통부_철도역 빠른 환승 정보 (공공데이터포털 15151816, CSV) — 인증키 없이 파일 내려받기
        → 열 이름·운영기관·노선 분포·예시, 원본은 tools/seoul/raw/quick_transfer.csv (UTF-8)
- 일본: ODPT odpt:StationFacility 의 승강장 칸별 정보(platformInformation) 중 환승 정보(transferInformation)
        일반 조회 / 데이터 덤프(.json) / 챌린지 API 를 차례로 시험 → 사업자별 제공 여부·예시
"""
import collections
import csv
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REPORT = os.path.join(ROOT, "docs", "probe", "QUICK_TRANSFER_PROBE.md")
KR_OUT = os.path.join(HERE, "seoul", "raw", "quick_transfer.csv")
UA = "Mozilla/5.0 (X11; Linux x86_64) WorldWideMetro/1.0"
ODPT = "https://api.odpt.org/api/v4/"
ODPT_CHALLENGE = "https://api-challenge.odpt.org/api/v4/"
JP_OPERATORS = ["TokyoMetro", "Toei", "YokohamaMunicipal"]

log = []
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def note(s=""):
    print(s)
    log.append(s)


def _req(url, referer=None, timeout=300):
    h = {"User-Agent": UA}
    if referer:
        h["Referer"] = referer
    r = opener.open(urllib.request.Request(url, headers=h), timeout=timeout)
    return r.status, dict(r.headers), r.read()


def _decode(b):
    for enc in ("utf-8-sig", "cp949", "euc-kr"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            continue
    return b.decode("utf-8", "replace")


def korea():
    note("## 한국 — 국토교통부_철도역 빠른 환승 정보 (15151816)")
    page = "https://www.data.go.kr/data/15151816/fileData.do"
    try:
        st, _, html = _req(page, page)
        atch = sorted(set(re.findall(r"FILE_\d{6,}", html.decode("utf-8", "replace"))))
        note(f"- 상세 페이지 HTTP {st}, 첨부파일 ID {atch[:3]}")
        raw = None
        for a in atch:
            st, hd, b = _req(f"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId={a}&fileDetailSn=1", page)
            name = hd.get("Content-Disposition", "")
            try:
                name = name.encode("latin-1").decode("utf-8")
            except (UnicodeEncodeError, UnicodeDecodeError):
                pass
            note(f"- 파일 {a}: HTTP {st}, {len(b)} bytes, {name[:120]}")
            if len(b) > 1000:
                raw = b
                break
        if raw is None:
            note("- 결과: 받지 못함")
            return
        text = _decode(raw)
        rows = list(csv.reader(io.StringIO(text)))
        head = rows[0]
        note(f"- 행 {len(rows) - 1}, 열 {head}")
        note("- 앞 15행:")
        for r in rows[1:16]:
            note(f"  - {r}")
        ih = {h: i for i, h in enumerate(head)}
        for col in head:
            vals = collections.Counter(r[ih[col]] for r in rows[1:] if len(r) > ih[col])
            if 1 < len(vals) <= 60:
                note(f"- {col}: {dict(vals.most_common(60))}")
        os.makedirs(os.path.dirname(KR_OUT), exist_ok=True)
        with open(KR_OUT, "w", encoding="utf-8") as f:
            f.write(text.replace("\r\n", "\n"))
        note(f"- 저장: tools/seoul/raw/quick_transfer.csv ({len(text)} chars)")
    except Exception as e:  # noqa: BLE001
        note(f"- 실패: {e}")


def _try_json(label, url):
    try:
        st, _, b = _req(url)
        data = json.loads(b)
        note(f"- {label}: HTTP {st}, {len(data) if isinstance(data, list) else type(data).__name__}건")
        return data if isinstance(data, list) else None
    except urllib.error.HTTPError as e:
        body = e.read()[:200].decode("utf-8", "replace")
        note(f"- {label}: HTTP {e.code} {body!r}")
    except Exception as e:  # noqa: BLE001
        note(f"- {label}: 실패 {e}")
    return None


def _summarize(op, data):
    rows = [f for f in data if str(f.get("odpt:operator", "")).endswith(op) or op in str(f.get("owl:sameAs", ""))]
    plat = [p for f in rows for p in (f.get("odpt:platformInformation") or [])]
    with_tr = [p for p in plat if p.get("odpt:transferInformation")]
    note(f"  - {op}: 시설 {len(rows)}건, 승강장 칸 정보 {len(plat)}건, 환승 정보 있는 칸 {len(with_tr)}건")
    if plat:
        keys = collections.Counter(k for p in plat for k in p)
        note(f"    - 칸 정보 키: {dict(keys)}")
    for p in with_tr[:3]:
        note("    - 예: " + json.dumps(p, ensure_ascii=False)[:600])


def japan(key, c2026):
    note("## 일본 — ODPT odpt:StationFacility (승강장 칸별 환승 정보)")
    if key:
        for op in JP_OPERATORS:
            q = urllib.parse.urlencode({"odpt:operator": f"odpt.Operator:{op}", "acl:consumerKey": key})
            data = _try_json(f"일반 조회 {op}", f"{ODPT}odpt:StationFacility?{q}")
            if data:
                _summarize(op, data)
        dump = _try_json("데이터 덤프 odpt:StationFacility.json", f"{ODPT}odpt:StationFacility.json?" +
                         urllib.parse.urlencode({"acl:consumerKey": key}))
        if dump:
            ops = collections.Counter(str(f.get("odpt:operator", "")) for f in dump)
            note(f"  - 덤프 사업자: {dict(ops)}")
            for op in JP_OPERATORS:
                _summarize(op, dump)
    else:
        note("- ODPT 키 없음")
    if c2026:
        for op in JP_OPERATORS:
            q = urllib.parse.urlencode({"odpt:operator": f"odpt.Operator:{op}", "acl:consumerKey": c2026})
            data = _try_json(f"챌린지 API {op}", f"{ODPT_CHALLENGE}odpt:StationFacility?{q}")
            if data:
                _summarize(op, data)
    else:
        note("- 챌린지 키 없음")


def main():
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    note("# 빠른 환승 데이터 시험")
    note()
    korea()
    note()
    japan(os.environ.get("ODPT_CONSUMER_KEY", "").strip(), os.environ.get("ODPT_C2026_KEY", "").strip())
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(log) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
