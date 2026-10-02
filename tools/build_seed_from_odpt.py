#!/usr/bin/env python3
"""ODPT odpt:Railway / odpt:Station → tools/seed/<region>.json

regions.json 에서 "generator": "odpt" 인 지역만 생성한다.
사용법:  ODPT_CONSUMER_KEY=... python tools/build_seed_from_odpt.py [region ...] [--cache raw.json]
이후:    python tools/build_all.py && python tools/validate_assets.py
"""
import argparse
import json
import math
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from kana_to_hangul import load_overrides, resolve_ko  # noqa: E402
from romaji_to_kana import romaji_to_kana  # noqa: E402

API = "https:" + "//api.odpt.org/api/v4/"

# 노선 코드 · 한글명 · 기본 색 (ODPT 에 색이 있으면 ODPT 값 우선)
LINE_META = {
    "TokyoMetro.Ginza": ("G", "긴자선", "#FF9500"),
    "TokyoMetro.Marunouchi": ("M", "마루노우치선", "#F62E36"),
    "TokyoMetro.MarunouchiBranch": ("Mb", "마루노우치선 지선", "#F62E36"),
    "TokyoMetro.Hibiya": ("H", "히비야선", "#9CAEB7"),
    "TokyoMetro.Tozai": ("T", "도자이선", "#009BBF"),
    "TokyoMetro.Chiyoda": ("C", "지요다선", "#00BB85"),
    "TokyoMetro.Yurakucho": ("Y", "유라쿠초선", "#C1A470"),
    "TokyoMetro.Hanzomon": ("Z", "한조몬선", "#8F76D6"),
    "TokyoMetro.Namboku": ("N", "난보쿠선", "#00AC9B"),
    "TokyoMetro.Fukutoshin": ("F", "후쿠토신선", "#9C5E31"),
    "Toei.Asakusa": ("A", "도에이 아사쿠사선", "#E85298"),
    "Toei.Mita": ("I", "도에이 미타선", "#0079C2"),
    "Toei.Shinjuku": ("S", "도에이 신주쿠선", "#6CBB5A"),
    "Toei.Oedo": ("E", "도에이 오에도선", "#B6007A"),
    "Toei.Arakawa": ("SA", "도덴 아라카와선", "#EE86A7"),
    "Toei.NipporiToneri": ("NT", "닛포리·도네리 라이너", "#C1272D"),
    "MIR.TsukubaExpress": ("TX", "쓰쿠바 익스프레스", "#E60012"),
    "TWR.Rinkai": ("R", "린카이선", "#00418E"),
    "TamaMonorail.TamaMonorail": ("TT", "다마 모노레일", "#E97119"),
    "YokohamaMunicipal.Blue": ("B", "요코하마 블루라인", "#0070C0"),
    "YokohamaMunicipal.Green": ("G", "요코하마 그린라인", "#009944"),
}
LOOP_DIR = {"OuterLoop": ("外回り", "외선순환", "Outer loop"), "InnerLoop": ("内回り", "내선순환", "Inner loop")}


def fetch(kind, key, **params):
    params["acl:consumerKey"] = key
    url = API + kind + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=120) as r:
        return json.load(r)


def camel_words(s):
    return re.sub(r"(?<=[a-z])(?=[A-Z])", " ", s)


def meta_for(rid):
    k = rid.split(":", 1)[1]
    if k in LINE_META:
        return LINE_META[k]
    for mk, v in LINE_META.items():
        if mk.lower() in k.lower():
            return v
    return None


def interp(rows):
    known = [i for i, r in enumerate(rows) if r[4] is not None]
    for i, r in enumerate(rows):
        if r[4] is None and known:
            lo = max([k for k in known if k < i], default=None)
            hi = min([k for k in known if k > i], default=None)
            a, b = rows[lo if lo is not None else hi], rows[hi if hi is not None else lo]
            r[4], r[5] = (a[4] + b[4]) / 2, (a[5] + b[5]) / 2
            print(f"[warn] 좌표 없음 → 보간: {r[1]}")


def build_region(region, raw, overrides, known_kana, old_seed):
    stations = {s["owl:sameAs"]: s for op in region["operators"] for s in raw[op]["Station"]}
    lines = []
    for op in region["operators"]:
        for rw in sorted(raw[op]["Railway"], key=lambda x: x["owl:sameAs"]):
            rid = rw["owl:sameAs"]
            path = rid.split(":", 1)[1]
            order = [o["odpt:station"] for o in sorted(rw.get("odpt:stationOrder", []), key=lambda o: o["odpt:index"])]
            if len(order) < 2:
                continue
            # 6자 노선(오에도선) 처럼 같은 역이 두 번 나오면 마지막 것만 남기고 끊긴 연결은 extraEdges 로 보존
            extra, keep = [], []
            for i, sid in enumerate(order):
                if sid in order[i + 1:]:
                    for nb in (order[i - 1] if i > 0 else None, order[i + 1] if i + 1 < len(order) else None):
                        if nb and nb != sid:
                            extra.append((sid, nb))
                else:
                    keep.append(sid)
            kept_adj = {frozenset(p) for p in zip(keep, keep[1:])}
            extra = [e for e in dict.fromkeys(extra) if frozenset(e) not in kept_adj]
            rows = []
            for sid in keep:
                s = stations.get(sid, {})
                title = s.get("odpt:stationTitle") or {}
                ja = s.get("dc:title") or title.get("ja") or sid.split(".")[-1]
                en_id = sid.split(".")[-1]
                kana = known_kana.get(ja) or title.get("ja-Hrkt") or romaji_to_kana(title.get("en") or camel_words(en_id))
                row = [s.get("odpt:stationCode") or "", en_id, ja, kana, s.get("geo:lat"), s.get("geo:long")]
                if sid != f"odpt.Station:{path}.{en_id}":
                    row.append(sid)
                rows.append(row)
            interp(rows)
            m = meta_for(rid)
            title = rw.get("odpt:railwayTitle") or {}
            code = (m[0] if m else None) or rw.get("odpt:lineCode") or path.split(".")[-1][:2]
            ko_name = (m[1] if m else None) or title.get("ko") or rw.get("dc:title")
            color = rw.get("odpt:color") or (m[2] if m else "#888888")

            def term(r, did):
                suf = (did or "").split(".")[-1].split(":")[-1]
                if suf in LOOP_DIR:
                    ja_, ko_, en_ = LOOP_DIR[suf]
                    return {"id": did, "ja": ja_, "ko": ko_, "en": en_}
                ko_ = resolve_ko(r[2], r[3], overrides)
                return {"id": did, "ja": r[2] + "方面", "ko": ko_ + " 방면", "en": "for " + camel_words(r[1])}
            asc_id = rw.get("odpt:ascendingRailDirection")
            desc_id = rw.get("odpt:descendingRailDirection")
            if not asc_id or not desc_id:
                print(f"[warn] {rid}: 방면 ID 없음 asc={asc_id} desc={desc_id}")
            L = {"id": rid, "operator": rw.get("odpt:operator", "odpt.Operator:" + op), "code": code, "color": color,
                 "name": {"ja": rw.get("dc:title") or title.get("ja", ""), "ko": ko_name, "en": title.get("en", "")},
                 "asc": term(rows[-1], asc_id or ""), "desc": term(rows[0], desc_id or ""), "stations": rows}
            if extra:
                L["extraEdges"] = [[a.split(".")[-1], b.split(".")[-1]] for a, b in extra]
            lines.append(L)
    return {"regionId": region["id"], "transferWalkSec": 180, "autoTransferRadiusM": 300,
            "lines": lines, "extraTransfers": (old_seed or {}).get("extraTransfers", [])}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("regions", nargs="*")
    ap.add_argument("--cache", help="원시 응답 JSON 저장/재사용 경로")
    a = ap.parse_args()
    regions = json.load(open(os.path.join(HERE, "regions.json"), encoding="utf-8"))
    targets = [r for r in regions if r.get("generator") == "odpt" and (not a.regions or r["id"] in a.regions)]
    raw = {}
    if a.cache and os.path.exists(a.cache):
        raw = json.load(open(a.cache, encoding="utf-8"))
    key = os.environ.get("ODPT_CONSUMER_KEY", "")
    for r in targets:
        for op_id in r["operators"]:
            op = op_id.split(":", 1)[1]
            if op in raw:
                continue
            if not key:
                sys.exit("ODPT_CONSUMER_KEY 환경변수가 필요합니다")
            raw[op] = {"Railway": fetch("odpt:Railway", key, **{"odpt:operator": op_id}),
                       "Station": fetch("odpt:Station", key, **{"odpt:operator": op_id})}
    if a.cache:
        json.dump(raw, open(a.cache, "w", encoding="utf-8"), ensure_ascii=False)
    overrides = load_overrides()
    known_kana = {}
    for fn in os.listdir(os.path.join(HERE, "seed")):
        if fn.endswith(".json"):
            for L in json.load(open(os.path.join(HERE, "seed", fn), encoding="utf-8")).get("lines", []):
                for st in L["stations"]:
                    if st[3] and st[3] != romaji_to_kana(st[1]):
                        known_kana.setdefault(st[2], st[3])
    for r in targets:
        r2 = dict(r, operators=[o.split(":", 1)[1] for o in r["operators"]])
        p = os.path.join(HERE, "seed", r["seed"])
        old = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None
        seed = build_region(r2, raw, overrides, known_kana, old)
        with open(p, "w", encoding="utf-8") as f:
            json.dump(seed, f, ensure_ascii=False, indent=1)
            f.write("\n")
        n = sum(len(L["stations"]) for L in seed["lines"])
        print(f"[seed] {r['id']}: lines={len(seed['lines'])} stations={n}")
        for L in seed["lines"]:
            for st in L["stations"]:
                ko = resolve_ko(st[2], st[3], overrides)
                if any("\u3040" <= c <= "\u30ff" or "\u4e00" <= c <= "\u9fff" for c in ko):
                    print(f"[warn] 한글 변환 실패 → ko_overrides.csv 에 추가 필요: {st[2]} ({L['id']})")


if __name__ == "__main__":
    main()
