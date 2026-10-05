"""모든 지역: seed(병합 포함) → network.json → layout.json → 지역 간 환승 표시 → (선택) docs/images/map_<region>.png"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")
sys.path.insert(0, HERE)

from cross_region import add_ghosts  # noqa: E402
from seed_merge import app_regions, load_region_seed  # noqa: E402


def run(*args):
    subprocess.run([sys.executable, *args], check=True, cwd=ROOT)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-preview", action="store_true")
    ap.add_argument("--font")
    a = ap.parse_args()
    regions = app_regions()
    ids = [r["id"] for r in regions]
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, "regions.json"), "w", encoding="utf-8") as f:
        json.dump([{k: r[k] for k in ("id", "name", "realtime", "operators", "note")} for r in regions],
                  f, ensure_ascii=False, indent=2)
        f.write("\n")
    for d in sorted(os.listdir(ASSETS)):
        p = os.path.join(ASSETS, d)
        if os.path.isdir(p) and d not in ids:
            shutil.rmtree(p)
            print(f"[cleanup] 병합/삭제된 지역 폴더 제거: {d}")
    for r in regions:
        rd = os.path.join(ASSETS, r["id"])
        os.makedirs(rd, exist_ok=True)
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tf:
            json.dump(load_region_seed(r), tf, ensure_ascii=False)
            tmp = tf.name
        try:
            run(os.path.join(HERE, "build_network_from_seed.py"), tmp, "--out", os.path.join(rd, "network.json"))
        finally:
            os.unlink(tmp)
        run(os.path.join(HERE, "layout_schematic.py"), os.path.join(rd, "network.json"))
    add_ghosts(ASSETS, ids)
    if not a.no_preview:
        extra = ["--font", a.font] if a.font else []
        for r in regions:
            run(os.path.join(HERE, "render_preview.py"), os.path.join(ASSETS, r["id"]), "--out",
                os.path.join(ROOT, "docs", "images", f"map_{r['id']}.png"), *extra)


if __name__ == "__main__":
    main()
