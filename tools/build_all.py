"""모든 지역: seed → network.json → layout.json → (선택) docs/images/map_<region>.png"""
import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "regions")


def run(*args):
    subprocess.run([sys.executable, *args], check=True, cwd=ROOT)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-preview", action="store_true")
    ap.add_argument("--font")
    a = ap.parse_args()
    with open(os.path.join(HERE, "regions.json"), encoding="utf-8") as f:
        regions = json.load(f)
    os.makedirs(ASSETS, exist_ok=True)
    app_regions = [{k: r[k] for k in ("id", "name", "realtime", "operators", "note")} for r in regions]
    with open(os.path.join(ASSETS, "regions.json"), "w", encoding="utf-8") as f:
        json.dump(app_regions, f, ensure_ascii=False, indent=2)
        f.write("\n")
    for r in regions:
        rd = os.path.join(ASSETS, r["id"])
        run(os.path.join(HERE, "build_network_from_seed.py"), os.path.join(HERE, "seed", r["seed"]))
        run(os.path.join(HERE, "layout_schematic.py"), os.path.join(rd, "network.json"))
        if not a.no_preview:
            extra = ["--font", a.font] if a.font else []
            run(os.path.join(HERE, "render_preview.py"), rd, "--out", os.path.join(ROOT, "docs", "images", f"map_{r['id']}.png"), *extra)


if __name__ == "__main__":
    main()
