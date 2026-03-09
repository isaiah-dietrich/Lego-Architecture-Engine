#!/usr/bin/env python3
"""Show why slightly darkened golden pixels match wrong LEGO palette entries."""
import math

def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def linear_to_lab(r, g, b):
    x = 0.4124564*r + 0.3575761*g + 0.1804375*b
    y = 0.2126729*r + 0.7151522*g + 0.0721750*b
    z = 0.0193339*r + 0.1191920*g + 0.9503041*b
    x /= 0.95047
    z /= 1.08883
    def f(t):
        return t ** (1/3) if t > 0.008856 else (903.3*t + 16) / 116
    return (116*f(y) - 16, 500*(f(x) - f(y)), 200*(f(y) - f(z)))

def srgb_to_lab(r, g, b):
    return linear_to_lab(
        srgb_to_linear(r / 255.0),
        srgb_to_linear(g / 255.0),
        srgb_to_linear(b / 255.0),
    )

def de76(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))

# Golden fur at different darkening levels
print("=== What darkening does to golden pixels in L*a*b* ===")
shadow_labs = []
for pct, label in [(100, "full brightness"), (70, "slight shadow"), (50, "moderate shadow"), (30, "deep shadow")]:
    sr, sg, sb = int(200 * pct / 100), int(160 * pct / 100), int(80 * pct / 100)
    lab = srgb_to_lab(sr, sg, sb)
    shadow_labs.append(lab)
    print(f"  sRGB({sr:3d},{sg:3d},{sb:3d}) {label:20s}  L*={lab[0]:5.1f}  a*={lab[1]:5.1f}  b*={lab[2]:5.1f}")

# Palette entries
palette = [
    ("84  Medium Nougat",     "AA7D55", True),
    ("178 Flat Dark Gold",    "B48455", True),
    ("28  Dark Tan",          "958A73", True),
    ("86  Light Brown",       "7C503A", True),
    ("6   Brown",             "583927", False),
    ("320 Dark Red",          "720E0F", False),
    ("8   Dark Bluish Gray",  "6B5A5A", False),
    ("326 Olive Green",       "9B9A5A", False),
    ("70  Reddish Brown",     "582A12", False),
    ("26  Magenta",           "923978", False),
    ("373 Sand Purple",       "845E84", False),
]

print()
print("=== dE76 from each shadow level to palette entries ===")
print(f"  {'Palette entry':25s}  {'L*':>5s} {'a*':>5s} {'b*':>5s}   full   70%   50%   30%")
print("  " + "-" * 80)
for name, hex_val, correct in palette:
    r = int(hex_val[0:2], 16)
    g = int(hex_val[2:4], 16)
    b = int(hex_val[4:6], 16)
    lab = srgb_to_lab(r, g, b)
    dists = [de76(lab, s) for s in shadow_labs]
    marker = " <-- CORRECT" if correct else ""
    print(f"  {name:25s}  {lab[0]:5.1f} {lab[1]:5.1f} {lab[2]:5.1f}  {dists[0]:5.1f} {dists[1]:5.1f} {dists[2]:5.1f} {dists[3]:5.1f}{marker}")

# Show which palette entry WINS at each shadow level
print()
print("=== Winner at each shadow level (nearest by dE76) ===")
for i, (pct, label) in enumerate([(100, "full"), (70, "70%"), (50, "50%"), (30, "30%")]):
    best_name = None
    best_dist = float("inf")
    for name, hex_val, _ in palette:
        r = int(hex_val[0:2], 16)
        g = int(hex_val[2:4], 16)
        b = int(hex_val[4:6], 16)
        lab = srgb_to_lab(r, g, b)
        d = de76(lab, shadow_labs[i])
        if d < best_dist:
            best_dist = d
            best_name = name
    print(f"  {label:5s} brightness → {best_name} (dE76={best_dist:.1f})")
