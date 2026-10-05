"""tools/regions.json 해석: mergeInto 가 있는 지역(예: 다마)은 대상 지역(도쿄)에 합쳐서 하나의 노선도로 만든다"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def load_regions():
    with open(os.path.join(HERE, "regions.json"), encoding="utf-8") as f:
        return json.load(f)


def app_regions(regions=None):
    """앱에 들어가는 지역 목록 (병합된 지역 제외, operators 합침, seeds = 합칠 seed 파일 목록)"""
    regions = regions if regions is not None else load_regions()
    out = []
    for r in regions:
        if r.get("mergeInto"):
            continue
        subs = [x for x in regions if x.get("mergeInto") == r["id"]]
        ops = list(dict.fromkeys(r["operators"] + [o for x in subs for o in x["operators"]]))
        out.append({**r, "operators": ops, "seeds": [r["seed"]] + [x["seed"] for x in subs]})
    return out


def load_region_seed(region):
    """app_regions() 항목 → 합쳐진 seed"""
    merged = None
    for name in region.get("seeds") or [region["seed"]]:
        with open(os.path.join(HERE, "seed", name), encoding="utf-8") as f:
            s = json.load(f)
        if merged is None:
            merged = s
            merged.setdefault("extraTransfers", [])
        else:
            merged["lines"] += s["lines"]
            merged["extraTransfers"] += s.get("extraTransfers", [])
    merged["regionId"] = region["id"]
    return merged
