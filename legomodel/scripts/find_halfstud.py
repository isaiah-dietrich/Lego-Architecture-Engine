#!/usr/bin/env python3
"""
find_halfstud.py — Finds LDraw bricks with off-grid X/Y/Z coordinates.

In LDraw:
  - Stud pitch: 20 LDU → valid X/Z centers are multiples of 10
  - Plate height: 8 LDU → valid Y values are multiples of 8

Usage:
    python3 scripts/find_halfstud.py output/model.ldr
    python3 scripts/find_halfstud.py output/model.ldr --slopes-only
    python3 scripts/find_halfstud.py output/model.ldr --dump-slopes
"""

import sys
import os

SLOPE_PARTS = {"3037", "3039", "3040b", "3298", "4286", "85984"}

def parse_ldr(path, slopes_only, dump_slopes):
    issues = []
    with open(path) as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.strip()
            if not line.startswith("1 "):
                continue
            parts = line.split()
            if len(parts) < 14:
                continue
            # 1 <color> <x> <y> <z> a b c d e f g h i <part>
            try:
                x = float(parts[2])
                y = float(parts[3])
                z = float(parts[4])
            except ValueError:
                continue
            part_file = parts[13]
            part_id = part_file.replace(".dat", "").replace(".DAT", "")

            if slopes_only and part_id not in SLOPE_PARTS:
                continue

            if dump_slopes and part_id in SLOPE_PARTS:
                print(f"Line {lineno:5d}: {part_id:<10} x={x:8.1f}  y={y:8.1f}  z={z:8.1f}")
                continue

            x_bad = abs(x % 10) > 1e-6 and abs(x % 10 - 10) > 1e-6
            z_bad = abs(z % 10) > 1e-6 and abs(z % 10 - 10) > 1e-6
            y_bad = abs(y % 8) > 1e-6 and abs(y % 8 - 8) > 1e-6

            if x_bad or z_bad or y_bad:
                bad_axes = " ".join(a for a, b in [("X", x_bad), ("Y", y_bad), ("Z", z_bad)] if b)
                issues.append((lineno, x, y, z, part_id, bad_axes))

    return issues

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {os.path.basename(sys.argv[0])} <file.ldr> [--slopes-only] [--dump-slopes]")
        sys.exit(1)

    path = sys.argv[1]
    slopes_only = "--slopes-only" in sys.argv
    dump_slopes = "--dump-slopes" in sys.argv

    if not os.path.exists(path):
        print(f"File not found: {path}")
        sys.exit(1)

    issues = parse_ldr(path, slopes_only, dump_slopes)

    if dump_slopes:
        return

    if not issues:
        print("No off-grid placements found.")
        return

    print(f"{'Line':<6} {'Part':<10} {'X':>8} {'Y':>8} {'Z':>8}  Bad axes")
    print("-" * 55)
    for lineno, x, y, z, part_id, bad_axes in issues:
        print(f"{lineno:<6} {part_id:<10} {x:>8.1f} {y:>8.1f} {z:>8.1f}  {bad_axes}")

    print(f"\nTotal off-grid: {len(issues)}")

if __name__ == "__main__":
    main()
