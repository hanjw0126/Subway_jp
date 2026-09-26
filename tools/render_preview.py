"""layout.json → PNG 미리보기 (PR 검토용). 한글 폰트 필요: --font, KO_FONT 환경변수 또는 tools/fonts/*.ttf"""
import argparse
import glob
import json
import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import font_manager  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def find_font(explicit=None):
    cands = [explicit, os.environ.get("KO_FONT")] + glob.glob(os.path.join(HERE, "fonts", "*.ttf")) + [
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "C:/Windows/Fonts/malgun.ttf", "/System/Library/Fonts/AppleSDGothicNeo.ttc"]
    for c in cands:
        if c and os.path.exists(c):
            return c
    return None


def render(layout, network, out, font=None):
    fp = find_font(font)
    prop = font_manager.FontProperties(fname=fp) if fp else None
    if not fp:
        print("[warn] 한글 폰트를 찾지 못해 글자가 깨질 수 있습니다 (--font 지정)")
    u = layout["unit"]
    W, H = layout["width"], layout["height"]
    fig = plt.figure(figsize=(W / u * 0.9, H / u * 0.9), dpi=110)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, W)
    ax.set_ylim(H, 0)
    ax.set_aspect("equal")
    ax.axis("off")
    lw_world = u * 0.11
    pt = fig.dpi / 72
    ppw = fig.get_size_inches()[0] * fig.dpi / W
    lw_pt = lw_world * ppw / pt
    fs = u * 0.2 * ppw / pt
    for l in layout["lines"]:
        for s in l["segments"]:
            pts = s["points"]
            o = s["offset"] * lw_world * 1.05
            for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
                d = math.hypot(x2 - x1, y2 - y1) or 1
                nx, ny = -(y2 - y1) / d * o, (x2 - x1) / d * o
                ax.plot([x1 + nx, x2 + nx], [y1 + ny, y2 + ny], color=l["color"], lw=lw_pt, solid_capstyle="round", zorder=2)
    line_color = {l["id"]: l["color"] for l in network["lines"]}
    st_line = {s["id"]: s["lineId"] for s in network["stations"]}
    for n in layout["nodes"]:
        x, y = n["x"], n["y"]
        if n["interchange"]:
            ax.add_patch(plt.Circle((x, y), u * 0.17, facecolor="white", edgecolor="#222", lw=lw_pt * 0.35, zorder=3))
        else:
            ax.add_patch(plt.Circle((x, y), u * 0.11, facecolor="white", edgecolor=line_color[st_line[n["stationIds"][0]]], lw=lw_pt * 0.4, zorder=3))
        dx, dy = n["labelDx"], n["labelDy"]
        gap = u * 0.22
        ha = "left" if dx > 0 else ("right" if dx < 0 else "center")
        va = "top" if dy > 0 else ("bottom" if dy < 0 else "center")
        ax.text(x + dx * gap, y + dy * gap, n["labelKo"], fontproperties=prop, fontsize=fs, ha=ha, va=va, zorder=4,
                color="#212529", bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.85))
    x0 = u * 0.5
    for i, l in enumerate(network["lines"]):
        ax.plot([x0, x0 + u * 0.6], [u * (0.6 + i * 0.45)] * 2, color=l["color"], lw=lw_pt, solid_capstyle="round")
        ax.text(x0 + u * 0.8, u * (0.6 + i * 0.45), f'{l["code"]}  {l["name"]["ko"]}', fontproperties=prop, fontsize=fs, va="center")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    fig.savefig(out, facecolor="#F8F9FA")
    plt.close(fig)
    print(f"[preview] {os.path.basename(out)}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("region_dir")
    ap.add_argument("--out", required=True)
    ap.add_argument("--font")
    a = ap.parse_args()
    with open(os.path.join(a.region_dir, "layout.json"), encoding="utf-8") as f:
        lay = json.load(f)
    with open(os.path.join(a.region_dir, "network.json"), encoding="utf-8") as f:
        net = json.load(f)
    render(lay, net, a.out, a.font)
