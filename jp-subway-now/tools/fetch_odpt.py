"""ODPT 원본 데이터(Railway / Station / TrainTimetable)를 tools/raw/<region>/ 에 저장합니다.
원본은 재배포하지 않으므로 tools/raw 는 .gitignore 대상입니다.

사용:  ODPT_CONSUMER_KEY=xxxx python tools/fetch_odpt.py tokyo [--timetable]
"""
import argparse
import json
import os
import sys

import requests as http

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.environ.get("ODPT_BASE_URL", "https://api.odpt.org/api/v4/")


def fetch(kind, key, **params):
    params["acl:consumerKey"] = key
    r = http.get(BASE + kind, params=params, timeout=60)
    r.raise_for_status()
    return r.json()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("region")
    ap.add_argument("--timetable", action="store_true", help="TrainTimetable 도 저장 (용량 큼)")
    a = ap.parse_args()
    key = os.environ.get("ODPT_CONSUMER_KEY")
    if not key:
        sys.exit("ODPT_CONSUMER_KEY 환경변수를 설정하세요")
    with open(os.path.join(HERE, "regions.json"), encoding="utf-8") as f:
        region = next(r for r in json.load(f) if r["id"] == a.region)
    out = os.path.join(HERE, "raw", a.region)
    os.makedirs(out, exist_ok=True)
    kinds = ["odpt:Railway", "odpt:Station"] + (["odpt:TrainTimetable"] if a.timetable else [])
    for op in region["operators"]:
        name = op.split(":")[1]
        for kind in kinds:
            data = fetch(kind, key, **{"odpt:operator": op})
            with open(os.path.join(out, f"{name}_{kind.split(':')[1]}.json"), "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False)
            print(f"{op} {kind}: {len(data)}")


if __name__ == "__main__":
    main()
