"""layout.json → PNG 미리보기. 앱 노선도와 같은 규칙(회사별 선 디자인·환승역 분할 링·종점 아이콘·강)으로 그린다.
한글 폰트: --font, KO_FONT 환경변수 또는 tools/fonts/*.ttf
--max-px: 긴 변의 픽셀 상한 (큰 노선도는 dpi 를 낮춰 맞춘다)"""
import argparse
import colorsys
import glob
import json
import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib import font_manager  # noqa: E402
from matplotlib.patches import Circle, FancyBboxPatch, Wedge  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
INK = "#212529"
RIVER = "#CFE6FA"


def find_font(explicit=None):
    cands = [explicit, os.environ.get("KO_FONT")] + glob.glob(os.path.join(HERE, "fonts", "*.ttf")) + [
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc",
        "C:/Windows/Fonts/malgun.ttf", "/System/Library/Fonts/AppleSDGothicNeo.ttc"]
    for c in cands:
        if c and os.path.exists(c):
            return c
    return None


def darker(hex_color, k=0.62):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    hh, ll, ss = colorsys.rgb_to_hls(r, g, b)
    r, g, b = colorsys.hls_to_rgb(hh, ll * k, ss)
    return "#%02x%02x%02x" % (int(r * 255), int(g * 255), int(b * 255))


def luminance(hex_color):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _styled(ax, xs, ys, color, category, lw, alpha=1.0):
    kw = dict(solid_capstyle="round", alpha=alpha)
    if category == "toei":
        ax.plot(xs, ys, color=color, lw=lw, zorder=2, **kw)
        ax.plot(xs, ys, color="white", lw=lw * 0.22, zorder=2.1, **kw)
    elif category == "jr":
        ax.plot(xs, ys, color=color, lw=lw * 1.1, zorder=2, solid_capstyle="butt", alpha=alpha)
        ax.plot(xs, ys, color="white", lw=lw * 0.36, zorder=2.1, dashes=(2.2, 1.6), dash_capstyle="butt", alpha=alpha)
    elif category == "private":
        ax.plot(xs, ys, color=darker(color), lw=lw * 1.3, zorder=2, **kw)
        ax.plot(xs, ys, color=color, lw=lw * 0.72, zorder=2.1, **kw)
    elif category == "monorail":
        ax.plot(xs, ys, color=color, lw=lw * 0.9, zorder=2, **kw)
        ax.plot(xs, ys, color="white", lw=lw * 0.42, zorder=2.1, **kw)
    elif category == "tram":
        ax.plot(xs, ys, color=color, lw=lw * 0.6, zorder=2, **kw)
    else:
        ax.plot(xs, ys, color=color, lw=lw, zorder=2, **kw)


def render(layout, network, out, font=None, dpi=90):
    fp = find_font(font)
    prop = font_manager.FontProperties(fname=fp) if fp else None
    bold = font_manager.FontProperties(fname=fp, weight="bold") if fp else None
    if not fp:
        print("[warn] 한글 폰트를 찾지 못해 글자가 깨질 수 있습니다 (--font 지정)")
    u = layout["unit"]
    W, H = layout["width"], layout["height"]
    fig = plt.figure(figsize=(W / u * 0.9, H / u * 0.9), dpi=dpi)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, W)
    ax.set_ylim(H, 0)
    ax.set_aspect("equal")
    ax.axis("off")
    lw_world = u * 0.11
    pt = fig.dpi / 72
    ppw = fig.get_size_inches()[0] * fig.dpi / W
    lw_pt = lw_world * ppw / pt
    fs = u * 0.19 * ppw / pt

    # 강 (맨 아래)
    for rv in layout.get("rivers") or []:
        pts = rv.get("points") or []
        if len(pts) >= 2:
            ax.plot([p[0] for p in pts], [p[1] for p in pts], color=RIVER, lw=rv.get("width", u * 0.5) * ppw / pt,
                    solid_capstyle="round", solid_joinstyle="round", zorder=1)

    for g in layout.get("ghosts") or []:
        pts = g.get("points") or []
        if len(pts) >= 2:
            _styled(ax, [p[0] for p in pts], [p[1] for p in pts], g.get("color", "#888888"), g.get("category", "metro"), lw_pt, alpha=0.3)

    for l in layout["lines"]:
        cat = l.get("category", "metro")
        for s in l["segments"]:
            pts = s["points"]
            o = s.get("offset", 0.0) * lw_world * 1.05
            xs, ys = [], []
            for i, (x, y) in enumerate(pts):
                a = pts[max(i - 1, 0)]
                b = pts[min(i + 1, len(pts) - 1)]
                d = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
                xs.append(x - (b[1] - a[1]) / d * o)
                ys.append(y + (b[0] - a[0]) / d * o)
            _styled(ax, xs, ys, l["color"], cat, lw_pt)

    # 역별 노선 색 (같은 색 노선은 하나로 센다)
    line_color = {l["id"]: l["color"] for l in network["lines"]}
    st_line = {s["id"]: s["lineId"] for s in network["stations"]}
    for n in layout["nodes"]:
        x, y = n["x"], n["y"]
        cols = []
        for sid in n["stationIds"]:
            c = line_color.get(st_line.get(sid))
            if c and c.upper() not in [k.upper() for k in cols]:
                cols.append(c)
        k = len(cols)
        if k <= 1:
            r = u * 0.11
            ax.add_patch(Circle((x, y), r, facecolor="white", edgecolor=(cols[0] if cols else "#444"), lw=lw_pt * 0.45, zorder=3))
        else:
            r = u * (0.17 if k == 2 else 0.2 if k == 3 else 0.24)
            ring = r * 0.36
            ax.add_patch(Circle((x, y), r + u * 0.025, facecolor=("#9AA0A6" if k == 2 else "#343A40"), edgecolor="none", zorder=3))
            for i, c in enumerate(cols):
                a0 = 90 - 360 * (i + 1) / k
                ax.add_patch(Wedge((x, y), r, a0, a0 + 360 / k, width=ring, facecolor=c, edgecolor="none", zorder=3.1))
            ax.add_patch(Circle((x, y), r - ring, facecolor="white", edgecolor="none", zorder=3.2))
            if k >= 4:
                ax.text(x, y, str(k), fontproperties=bold, fontsize=fs * 0.85, ha="center", va="center", color=INK, zorder=3.3)
        dx, dy = n["labelDx"], n["labelDy"]
        gap = u * (0.24 if k <= 1 else 0.32)
        ha = "left" if dx > 0 else ("right" if dx < 0 else "center")
        va = "top" if dy > 0 else ("bottom" if dy < 0 else "center")
        ax.text(x + dx * gap, y + dy * gap, n["labelKo"], fontproperties=prop, fontsize=fs, ha=ha, va=va, zorder=4,
                color=INK, bbox=dict(boxstyle="round,pad=0.12", fc="white", ec="none", alpha=0.85))

    # 종점 노선 아이콘: JR = 둥근 사각형, 그 외 = 원
    side = u * 0.42
    for l in layout["lines"]:
        for t in l.get("terminals") or []:
            cx, cy = t[0], t[1]
            txt_col = INK if luminance(l["color"]) > 0.62 else "white"
            if l.get("category") == "jr":
                rim = side * 0.1
                ax.add_patch(FancyBboxPatch((cx - side / 2 - rim, cy - side / 2 - rim), side + 2 * rim, side + 2 * rim,
                                            boxstyle=f"round,pad=0,rounding_size={side * 0.22}", fc="white", ec="none", zorder=5))
                ax.add_patch(FancyBboxPatch((cx - side / 2, cy - side / 2), side, side,
                                            boxstyle=f"round,pad=0,rounding_size={side * 0.16}", fc=l["color"], ec="none", zorder=5.1))
            else:
                ax.add_patch(Circle((cx, cy), side / 2 * 1.2, facecolor="white", edgecolor="none", zorder=5))
                ax.add_patch(Circle((cx, cy), side / 2, facecolor=l["color"], edgecolor="none", zorder=5.1))
            code = l.get("code", "")
            ax.text(cx, cy, code, fontproperties=bold, fontsize=fs * (0.9 if len(code) <= 1 else 0.7 if len(code) == 2 else 0.55),
                    ha="center", va="center", color=txt_col, zorder=5.2)

    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    fig.savefig(out, facecolor="#F8F9FA")
    plt.close(fig)
    print(f"[preview] {os.path.basename(out)}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("region_dir")
    ap.add_argument("--out", required=True)
    ap.add_argument("--font")
    ap.add_argument("--dpi", type=int, default=90)
    ap.add_argument("--max-px", type=int, default=0)
    a = ap.parse_args()
    with open(os.path.join(a.region_dir, "layout.json"), encoding="utf-8") as f:
        lay = json.load(f)
    with open(os.path.join(a.region_dir, "network.json"), encoding="utf-8") as f:
        net = json.load(f)
    dpi = a.dpi
    if a.max_px:
        inches = max(lay["width"], lay["height"]) / lay["unit"] * 0.9
        dpi = max(10, min(dpi, int(a.max_px / inches)))
    render(lay, net, a.out, a.font, dpi)
