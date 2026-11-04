#!/usr/bin/env python3
"""Generate on-brand uncoalesced mosaic marks as SVG.

Reads the shared spec from tokens.json (same folder) so the rules live in
one place. Stdlib only.

Usage:
  python generate_mark.py --seed 7 --out mark.svg
  python generate_mark.py --seed 7 --mode product --accent sandyBrown --out fluxboard.svg
  python generate_mark.py --mode glyph --accent sandyBrown --glyph parentheses --out parentheses.svg
  python generate_mark.py --mode vector --accent palmLeaf --mark peridotHex --out peridot.svg
  python generate_mark.py --demo
"""
import argparse
import json
import math
import random
from pathlib import Path

TOKENS_PATH = Path(__file__).with_name("tokens.json")


def load_tokens():
    return json.loads(TOKENS_PATH.read_text())


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb):
    return "#" + "".join(f"{max(0, min(255, round(c))):02X}" for c in rgb)


def mix(hex_a, hex_b, t):
    a, b = hex_to_rgb(hex_a), hex_to_rgb(hex_b)
    return rgb_to_hex(tuple(a[i] + (b[i] - a[i]) * t for i in range(3)))


def hue_ramp(tokens, accent):
    """Shade/base/tint of one accent hue, interpolated toward the brand's own
    graphite/porcelain (not generic black/white). Shared by product-mode
    scatter marks and vector marks alike — one texture rule, one place."""
    c = tokens["color"]
    base = c[accent]["hex"]
    return {
        "shade": mix(base, c["graphite"]["hex"], 0.35),
        "base": base,
        "tint": mix(base, c["porcelain"]["hex"], 0.35),
    }


def build_palette(tokens, mode, accent):
    """Return list of (hex, weight) the generator picks colors from."""
    c = tokens["color"]
    if mode == "parent":
        return [
            (c["palmLeaf"]["hex"], c["palmLeaf"]["weight"]),
            (c["sandyBrown"]["hex"], c["sandyBrown"]["weight"]),
            (c["watermelon"]["hex"], c["watermelon"]["weight"]),
            (c["moss"]["hex"], c["moss"]["weight"]),
        ]
    # product mode: shade / accent / tint ramp of a single core hue
    ramp = hue_ramp(tokens, accent)
    return [(ramp["shade"], 0.25), (ramp["base"], 0.5), (ramp["tint"], 0.25)]


def weighted_choice(rng, items):
    colors, weights = zip(*items)
    return rng.choices(colors, weights=weights, k=1)[0]


def generate_cells(tokens, seed, mode, accent, glyph=None):
    rng = random.Random(seed)
    palette = build_palette(tokens, "parent" if mode == "parent" else "product", accent)
    circle_p = tokens["shape"]["mix"]["circle"]

    if mode == "glyph":
        # fixed positions from tokens.json, only shape+color are randomized per seed
        glyph_spec = tokens["glyphs"][glyph]
        cells = []
        for r, c in glyph_spec["cells"]:
            shape = "circle" if rng.random() < circle_p else "square"
            cells.append({"row": r, "col": c, "shape": shape, "color": weighted_choice(rng, palette)})
        # optional fixed, non-randomized center cell in its own named color —
        # a deliberate exception to the parent grid's "center always empty" rule
        center = glyph_spec.get("center")
        if center:
            cells.append({
                "row": center["row"], "col": center["col"],
                "shape": center.get("shape", "circle"),
                "color": tokens["color"][center["color"]]["hex"],
            })
        return cells

    rows, cols = tokens["grid"]["rows"], tokens["grid"]["columns"]
    cr, cc = tokens["grid"]["centerCell"]
    cells = []
    for r in range(rows):
        for c in range(cols):
            if (r, c) == (cr, cc):
                continue  # center is always empty — the concept, not a bug
            dist = abs(r - cr) + abs(c - cc)
            fill_p = max(0.04, min(0.95, 0.95 - dist * 0.115))
            if rng.random() > fill_p:
                continue
            shape = "circle" if rng.random() < circle_p else "square"
            color = weighted_choice(rng, palette)
            cells.append({"row": r, "col": c, "shape": shape, "color": color})
    return cells


def render_svg(tokens, cells, transparent=False):
    g = tokens["grid"]
    s = tokens["shape"]
    size, canvas = g["cellSize"], g["canvas"]
    bg = tokens["color"]["graphite"]["hex"]

    parts = [f'<svg viewBox="0 0 {canvas} {canvas}" xmlns="http://www.w3.org/2000/svg">']
    if not transparent:
        parts.append(f'<rect width="{canvas}" height="{canvas}" fill="{bg}"/>')
    for cell in cells:
        cx = cell["col"] * size + size / 2
        cy = cell["row"] * size + size / 2
        if cell["shape"] == "circle":
            r = size * s["circleDiameterRatio"] / 2
            parts.append(f'<circle cx="{cx}" cy="{cy}" r="{r:.1f}" fill="{cell["color"]}"/>')
        else:
            side = size * s["squareSideRatio"]
            rad = side * s["squareCornerRadiusRatio"]
            x, y = cx - side / 2, cy - side / 2
            parts.append(
                f'<rect x="{x:.1f}" y="{y:.1f}" width="{side:.1f}" height="{side:.1f}" '
                f'rx="{rad:.1f}" fill="{cell["color"]}"/>'
            )
    parts.append("</svg>")
    return "\n".join(parts)


def hex_facet_elements(tokens, name, accent, cx, cy, scale=1.0):
    """SVG element strings for one vectorMarks entry (wedge fan + center dot),
    positioned at (cx, cy). No <svg> wrapper — callers embed this directly,
    whether as a standalone icon or inline inside a wordmark."""
    spec = tokens["vectorMarks"][name]
    ramp = hue_ramp(tokens, accent)
    n = spec["vertexCount"]
    r = spec["radiusRatio"] * tokens["grid"]["canvas"] * scale
    rot = math.radians(spec["rotationDeg"])
    pts = [(cx + r * math.cos(rot + 2 * math.pi * i / n),
            cy + r * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]

    parts = []
    for i in range(n):
        color = ramp[spec["toneSequence"][i]]
        p1, p2 = pts[i], pts[(i + 1) % n]
        parts.append(
            f'<polygon points="{cx:.1f},{cy:.1f} {p1[0]:.1f},{p1[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}" fill="{color}"/>'
        )
    dot_r = spec["centerDotRatio"] * tokens["grid"]["canvas"] * scale
    dot_color = tokens["color"][spec["centerColor"]]["hex"]
    parts.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{dot_r:.1f}" fill="{dot_color}"/>')
    return parts


def render_vector_svg(tokens, name, accent, transparent=False):
    canvas = tokens["grid"]["canvas"]
    bg = tokens["color"]["graphite"]["hex"]
    parts = [f'<svg viewBox="0 0 {canvas} {canvas}" xmlns="http://www.w3.org/2000/svg">']
    if not transparent:
        parts.append(f'<rect width="{canvas}" height="{canvas}" fill="{bg}"/>')
    parts += hex_facet_elements(tokens, name, accent, canvas / 2, canvas / 2)
    parts.append("</svg>")
    return "\n".join(parts)


def demo():
    """Self-check: run the generator against the rules it claims to follow."""
    tokens = load_tokens()
    cr, cc = tokens["grid"]["centerCell"]
    palette_hexes = {v["hex"] for v in tokens["color"].values()}

    for seed in range(20):
        cells = generate_cells(tokens, seed, "parent", None)
        assert not any(c["row"] == cr and c["col"] == cc for c in cells), \
            f"seed {seed}: center cell was filled"
        assert all(c["shape"] in ("circle", "square") for c in cells)
        assert all(c["color"] in palette_hexes for c in cells), \
            f"seed {seed}: color outside declared palette"
        fill_rate = len(cells) / (tokens["grid"]["rows"] * tokens["grid"]["columns"])
        assert 0.25 <= fill_rate <= 0.65, f"seed {seed}: fill rate {fill_rate:.2f} out of range"

    product_cells = generate_cells(tokens, 1, "product", "sandyBrown")
    assert not any(c["row"] == cr and c["col"] == cc for c in product_cells)

    glyph_cells = generate_cells(tokens, 2, "glyph", "watermelon", glyph="parentheses")
    expected_positions = {tuple(p) for p in tokens["glyphs"]["parentheses"]["cells"]}
    assert {(c["row"], c["col"]) for c in glyph_cells} == expected_positions, \
        "glyph cells don't match tokens.json positions"
    assert not any(c["row"] == cr and c["col"] == cc for c in glyph_cells)

    elements = hex_facet_elements(tokens, "peridotHex", "palmLeaf", 450, 450)
    polygons = [e for e in elements if e.startswith("<polygon")]
    circles = [e for e in elements if e.startswith("<circle")]
    assert len(polygons) == tokens["vectorMarks"]["peridotHex"]["vertexCount"] == 6, \
        "peridotHex should be a 6-wedge fan"
    assert len(circles) == 1, "peridotHex should have exactly one center dot"
    ramp_hexes = set(hue_ramp(tokens, "palmLeaf").values())
    assert all(any(f'fill="{h}"' in p for h in ramp_hexes) for p in polygons), \
        "peridotHex wedges should only use the palmLeaf shade/base/tint ramp"
    assert f'fill="{tokens["color"]["moss"]["hex"]}"' in circles[0], \
        "peridotHex center dot should be forced to moss"

    print("demo: all self-checks passed (20 parent seeds + 1 product seed + 1 glyph seed + 1 vector mark)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--mode", choices=["parent", "product", "glyph", "vector"], default="parent")
    ap.add_argument("--accent", choices=["palmLeaf", "sandyBrown", "watermelon", "moss"],
                     help="required when --mode product, glyph, or vector")
    ap.add_argument("--glyph", help="required when --mode glyph, e.g. parentheses")
    ap.add_argument("--mark", help="required when --mode vector, e.g. peridotHex")
    ap.add_argument("--out", default="mark.svg")
    ap.add_argument("--transparent", action="store_true", help="omit the graphite background rect")
    ap.add_argument("--demo", action="store_true", help="run self-checks and exit")
    args = ap.parse_args()

    if args.demo:
        demo()
        return

    if args.mode in ("product", "glyph", "vector") and not args.accent:
        ap.error(f"--mode {args.mode} requires --accent")
    if args.mode == "glyph" and not args.glyph:
        ap.error("--mode glyph requires --glyph")
    if args.mode == "vector" and not args.mark:
        ap.error("--mode vector requires --mark")

    tokens = load_tokens()
    if args.mode == "glyph" and args.glyph not in tokens["glyphs"]:
        ap.error(f"unknown glyph {args.glyph!r}; known: {list(tokens['glyphs'])}")
    if args.mode == "vector" and args.mark not in tokens["vectorMarks"]:
        ap.error(f"unknown vector mark {args.mark!r}; known: {list(tokens['vectorMarks'])}")

    if args.mode == "vector":
        svg = render_vector_svg(tokens, args.mark, args.accent, transparent=args.transparent)
        Path(args.out).write_text(svg)
        print(f"wrote {args.out} (vector mark={args.mark}, accent={args.accent})")
        return

    cells = generate_cells(tokens, args.seed, args.mode, args.accent, glyph=args.glyph)
    svg = render_svg(tokens, cells, transparent=args.transparent)
    Path(args.out).write_text(svg)
    print(f"wrote {args.out} ({len(cells)} shapes, seed={args.seed}, mode={args.mode})")


if __name__ == "__main__":
    main()
