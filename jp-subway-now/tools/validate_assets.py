"""app/src/main/assets/regions 무결성 검사"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")


def check_region(rid):
    errs = []
    with open(os.path.join(ASSETS, rid, "network.json"), encoding="utf-8") as f:
        net = json.load(f)
    with open(os.path.join(ASSETS, rid, "layout.json"), encoding="utf-8") as f:
        lay = json.load(f)
    sids = {s["id"] for s in net["stations"]}
    if len(sids) != len(net["stations"]):
        errs.append("중복 역 ID")
    for l in net["lines"]:
        for s in l["stations"]:
            if s not in sids:
                errs.append(f"{l['id']}: 없는 역 {s}")
        for k in ("asc", "desc"):
            if not l["directions"][k]["name"]["ko"]:
                errs.append(f"{l['id']}: 방면 한글명 없음")
    for s in net["stations"]:
        ko = s["name"]["ko"]
        if not ko:
            errs.append(f"{s['id']}: 한글명 없음")
        if any("\u3040" <= c <= "\u30ff" or "\u4e00" <= c <= "\u9fff" for c in ko):
            errs.append(f"{s['id']}: 한글명에 가나/한자가 남아 있음 ({ko})")
    for t in net["transfers"]:
        if t["from"] not in sids or t["to"] not in sids:
            errs.append(f"환승 참조 오류 {t}")
    groups = {s["group"] for s in net["stations"]}
    lg = {n["group"] for n in lay["nodes"]}
    if groups != lg:
        errs.append(f"layout 노드 불일치: {sorted(groups ^ lg)[:5]}")
    if {sid for n in lay["nodes"] for sid in n["stationIds"]} != sids:
        errs.append("layout 이 모든 역을 포함하지 않음")
    if {l["lineId"] for l in lay["lines"]} != {l["id"] for l in net["lines"]}:
        errs.append("layout 노선 불일치")
    return errs, net


def main():
    with open(os.path.join(ASSETS, "regions.json"), encoding="utf-8") as f:
        regions = json.load(f)
    bad = 0
    for r in regions:
        errs, net = check_region(r["id"])
        print(f"[{r['id']}] lines={len(net['lines'])} stations={len(net['stations'])} -> {'OK' if not errs else 'FAIL'}")
        for e in errs:
            print("   -", e)
        bad += len(errs)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
