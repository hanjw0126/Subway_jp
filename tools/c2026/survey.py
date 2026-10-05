"""챌린지2026 API 제공 범위 조사 (데이터 본문은 출력하지 않고 개수·ID·필드명만 기록)"""
import json, os, sys, collections, urllib.request, urllib.parse, urllib.error
K = os.environ.get("ODPT_C2026_KEY", "").strip()
if not K:
    print("::error::ODPT_C2026_KEY 가 비어 있음"); sys.exit(1)
H = "ht" + "tps://"
CANDS = [H + "api-challenge.odpt.org/api/v4/", H + "api-challenge2026.odpt.org/api/v4/", H + "api.odpt.org/api/v4/"]
def get(base, t, **p):
    p["acl:consumerKey"] = K
    u = base + t + "?" + urllib.parse.urlencode(p)
    try:
        with urllib.request.urlopen(u, timeout=150) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception as e:
        return type(e).__name__, None
best = None
for b in CANDS:
    s, d = get(b, "odpt:Railway")
    n = len(d) if isinstance(d, list) else 0
    print(f"ENDPOINT {b.split('//')[1].split('/')[0]} status={s} railways={n}")
    if n and (best is None or n > best[1]):
        best = (b, n, d)
if not best:
    sys.exit(1)
base, _, rws = best
print("USE", base.split("//")[1].split("/")[0])
pub = {}
s, d = get(CANDS[2], "odpt:Railway")
pubset = {x["owl:sameAs"] for x in d} if isinstance(d, list) else set()
byop = collections.defaultdict(list)
for r in rws:
    byop[r.get("odpt:operator")].append(r)
summary = {}
for op, rs in sorted(byop.items()):
    s, st = get(base, "odpt:Station", **{"odpt:operator": op})
    st = st if isinstance(st, list) else []
    geo = {x["owl:sameAs"] for x in st if x.get("geo:lat") is not None}
    first = rs[0]["owl:sameAs"]
    res = {}
    for t in ["odpt:TrainTimetable", "odpt:StationTimetable", "odpt:Train", "odpt:TrainInformation"]:
        s2, d2 = get(base, t, **({"odpt:railway": first} if "Timetable" in t else {"odpt:operator": op}))
        res[t.split(":")[1]] = len(d2) if isinstance(d2, list) else s2
    rows = []
    for r in rs:
        order = r.get("odpt:stationOrder") or []
        miss = sum(1 for o in order if o.get("odpt:station") not in geo)
        rows.append((r["owl:sameAs"].split(":")[1], len(order), miss, bool(r.get("odpt:color")), r["owl:sameAs"] in pubset))
    print(f"\nOP {op.split(':')[1]} railways={len(rs)} stations={len(st)} geo={len(geo)} sample({first.split(':')[1]}) {res}")
    for row in rows:
        print("   ", row[0], "order", row[1], "noGeo", row[2], "color" if row[3] else "-", "PUBLIC" if row[4] else "C2026")
    summary[op] = {"railways": len(rs), "stations": len(st), "geo": len(geo), **res}
if st:
    print("\nStation fields:", sorted(st[0].keys()))
print("Railway fields:", sorted(rws[0].keys()))
