"""tools/raw/<region>/*_Railway.json, *_Station.json → tools/seed/<region>.json
생성된 seed 로 build_all.py 를 돌리면 ODPT 공식 역 ID·좌표·가나 표기로 network/layout 이 만들어집니다.
노선 색상/방면 한글명은 기존 seed 값을 유지합니다.
"""
import argparse
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("region")
    a = ap.parse_args()
    raw = os.path.join(HERE, "raw", a.region)
    seed_path = os.path.join(HERE, "seed", f"{a.region}.json")
    old = json.load(open(seed_path, encoding="utf-8")) if os.path.exists(seed_path) else {"lines": []}
    old_lines = {l["id"]: l for l in old["lines"]}
    stations = {}
    for p in glob.glob(os.path.join(raw, "*_Station.json")):
        for s in json.load(open(p, encoding="utf-8")):
            stations[s["owl:sameAs"]] = s
    lines = []
    for p in glob.glob(os.path.join(raw, "*_Railway.json")):
        for r in json.load(open(p, encoding="utf-8")):
            rid = r["owl:sameAs"]
            prev = old_lines.get(rid, {})
            order = sorted(r.get("odpt:stationOrder", []), key=lambda o: o["odpt:index"])
            sts = []
            for o in order:
                s = stations.get(o["odpt:station"])
                if not s or s.get("geo:lat") is None:
                    continue
                title = s.get("odpt:stationTitle", {})
                sts.append([s.get("odpt:stationCode", ""), s["owl:sameAs"].split(".")[-1], s.get("dc:title", ""),
                            title.get("ja-Hrkt", ""), s["geo:lat"], s["geo:long"]])
            if len(sts) < 2:
                continue
            title = r.get("odpt:railwayTitle", {})
            lines.append({
                "id": rid, "operator": r["odpt:operator"], "code": r.get("odpt:lineCode", prev.get("code", "")),
                "color": r.get("odpt:color", prev.get("color", "#888888")),
                "name": prev.get("name", {"ja": r.get("dc:title", ""), "ko": title.get("ko", ""), "en": title.get("en", "")}),
                "asc": prev.get("asc", {"id": r.get("odpt:ascendingRailDirection", "asc"), "ja": "", "ko": sts[-1][2] + " 방면", "en": ""}),
                "desc": prev.get("desc", {"id": r.get("odpt:descendingRailDirection", "desc"), "ja": "", "ko": sts[0][2] + " 방면", "en": ""}),
                "stations": sts})
    seed = {"regionId": a.region, "transferWalkSec": old.get("transferWalkSec", 180), "lines": lines,
            "extraTransfers": old.get("extraTransfers", [])}
    with open(seed_path, "w", encoding="utf-8") as f:
        json.dump(seed, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"seed 갱신: {seed_path} (lines={len(lines)})")


if __name__ == "__main__":
    main()
