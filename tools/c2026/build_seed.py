#!/usr/bin/env python3
"""공공교통 오픈데이터 챌린지 2026 데이터 → tools/seed/c2026/<region>.json  (커밋 금지: .gitignore)

릴리즈 빌드 때만 실행한다 (ODPT_C2026_KEY 필요). 결과는 seed_merge.load_region_seed 가 지역 seed 에 합친다.
각 노선에 "source": "c2026" 가 붙어 network.json 까지 전달된다.
챌린지 종료 후: tools/c2026/ 삭제 + release.yml 의 "Challenge 2026 data" 단계 삭제 + worker 의 C2026_OPERATORS 삭제.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)
import build_seed_from_odpt as bso  # noqa: E402
from kana_to_hangul import load_overrides, resolve_ko  # noqa: E402
from romaji_to_kana import romaji_to_kana  # noqa: E402

CFG = json.load(open(os.path.join(HERE, "config.json"), encoding="utf-8"))
OUT = os.path.join(TOOLS, "seed", "c2026")


def fetch(kind, key, **params):
    params["acl:consumerKey"] = key
    url = CFG["endpoint"] + kind + "?" + urllib.parse.urlencode(params)
    for i in range(4):
        try:
            with urllib.request.urlopen(url, timeout=180) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            if i == 3:
                raise
            print(f"[retry] {kind} {params.get('odpt:operator')}: {type(e).__name__}")
            time.sleep(5 * (i + 1))


def _inside(s, bb):
    la, lo = s.get("geo:lat"), s.get("geo:long")
    if la is None or lo is None:
        return None
    return bb[0] <= la <= bb[2] and bb[1] <= lo <= bb[3]


def clip_order(order, stations, bb):
    """지역 범위 안에 있는 가장 긴 연속 구간만 남긴다 (좌표 없는 역은 앞 역 판정을 따름)"""
    flags = [_inside(stations.get(o["odpt:station"], {}), bb) for o in order]
    last = True
    for i, f in enumerate(flags):
        if f is None:
            flags[i] = last
        last = flags[i]
    best, cur = [], []
    for o, f in zip(order, flags):
        if f:
            cur.append(o)
        else:
            best = cur if len(cur) > len(best) else best
            cur = []
    return cur if len(cur) > len(best) else best


def _has_cjk(s):
    return any("\u3040" <= c <= "\u30ff" or "\u4e00" <= c <= "\u9fff" for c in s or "")


def ko_line_name(L, overrides):
    en = (L["name"].get("en") or "").replace(" Line", "").strip()
    ja = (L["name"].get("ja") or "").replace("線", "")
    if en:
        return resolve_ko(ja, romaji_to_kana(en), overrides) + "선"
    return L["name"].get("ko") or L["id"].split(".")[-1]


def public_seed_lines(seed_dir):
    """tools/seed/*.json 중 노선 seed({"lines": [...]}) 만 골라 노선을 돌려준다.
    같은 폴더의 다른 데이터(ko_wiki.json 같은 목록)나 다른 나라 seed(kr: 노선)는 건너뛴다."""
    for fn in sorted(os.listdir(seed_dir)):
        if not fn.endswith(".json"):
            continue
        with open(os.path.join(seed_dir, fn), encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            continue
        for L in data.get("lines", []):
            if str(L.get("id", "")).startswith("odpt."):
                yield L


def main():
    key = os.environ.get("ODPT_C2026_KEY", "")
    if not key:
        sys.exit("ODPT_C2026_KEY 환경변수가 필요합니다")
    meta = json.load(open(os.path.join(HERE, "line_meta.json"), encoding="utf-8"))
    bso.LINE_META.update({k: tuple(v) for k, v in meta.items()})
    overrides = load_overrides()
    known_kana, public_ids = {}, set()
    seed_dir = os.path.join(TOOLS, "seed")
    for L in public_seed_lines(seed_dir):
        public_ids.add(L["id"])
        for st in L["stations"]:
            if st[3]:
                known_kana.setdefault(st[2], st[3])
    os.makedirs(OUT, exist_ok=True)
    for reg in CFG["regions"]:
        raw = {}
        for op in reg["operators"]:
            rw = fetch("odpt:Railway", key, **{"odpt:operator": "odpt.Operator:" + op})
            st = fetch("odpt:Station", key, **{"odpt:operator": "odpt.Operator:" + op})
            stations = {s["owl:sameAs"]: s for s in st}
            kept = []
            for r in rw:
                rid = r.get("owl:sameAs", "")
                if rid in public_ids or rid in CFG.get("excludeLines", []):
                    continue
                order = sorted(r.get("odpt:stationOrder", []), key=lambda o: o["odpt:index"])
                cl = clip_order(order, stations, reg["bbox"])
                if len(cl) >= reg.get("minStations", 3):
                    kept.append({**r, "odpt:stationOrder": cl})
            raw[op] = {"Railway": kept, "Station": st}
            print(f"[c2026] {op}: 노선 {len(rw)}개 중 {len(kept)}개 사용")
        seed = bso.build_region({"id": reg["region"], "operators": reg["operators"]}, raw, overrides, known_kana, None)
        fails = 0
        for L in seed["lines"]:
            L["source"] = "c2026"
            if _has_cjk(L["name"].get("ko")):
                L["name"]["ko"] = ko_line_name(L, overrides)
            fails += sum(_has_cjk(resolve_ko(s[2], s[3], overrides)) for s in L["stations"])
        seed["extraTransfers"] = []
        with open(os.path.join(OUT, reg["region"] + ".json"), "w", encoding="utf-8") as f:
            json.dump(seed, f, ensure_ascii=False)
        n = sum(len(L["stations"]) for L in seed["lines"])
        print(f"[c2026] {reg['region']}: 노선 {len(seed['lines'])}개, 역 {n}개, 한글 변환 실패 {fails}개")


if __name__ == "__main__":
    main()
