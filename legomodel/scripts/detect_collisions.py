#!/usr/bin/env python3
"""Detect bounding-box collisions in an LDR file using spatial grid."""
import sys
from collections import defaultdict

def main():
    ldr_path = sys.argv[1] if len(sys.argv) > 1 else "lab_sloped.ldr"

    bricks = []
    with open(ldr_path) as f:
        for line in f:
            if not line.startswith("1 "):
                continue
            parts = line.strip().split()
            x_ldu = float(parts[2])
            y_ldu = float(parts[3])
            z_ldu = float(parts[4])
            part = parts[14]
            rot = " ".join(parts[5:14])
            bricks.append((x_ldu, y_ldu, z_ldu, rot, part))

    part_dims = {
        "3005.dat": (1,1,3), "3004.dat": (1,2,3), "3003.dat": (2,2,3), "3001.dat": (2,4,3),
        "3024.dat": (1,1,1), "3023.dat": (1,2,1), "3022.dat": (2,2,1), "3020.dat": (2,4,1),
        "3037.dat": (2,4,3), "3039.dat": (2,2,3), "3040b.dat": (2,1,3),
        "3298.dat": (3,2,3), "4286.dat": (3,1,3), "85984.dat": (1,2,2),
    }

    slope_parts = {"3037.dat","3039.dat","3040b.dat","3298.dat","4286.dat","85984.dat"}
    n_slope = sum(1 for b in bricks if b[4] in slope_parts)
    print(f"Total bricks: {len(bricks)}, slopes: {n_slope}, flat: {len(bricks)-n_slope}")

    IDN = "1 0 0 0 1 0 0 0 1"
    Y180 = "-1 0 0 0 1 0 0 0 -1"
    Y90 = "0 0 1 0 1 0 -1 0 0"
    Y270 = "0 0 -1 0 1 0 1 0 0"

    boxes = []
    for (x, y, z, rot, part) in bricks:
        dims = part_dims.get(part)
        if dims is None:
            continue
        sx, sy, h = dims  # sx=catalog stud_x, sy=catalog stud_y
        # LDraw identity: catalog stud_y -> local X, catalog stud_x -> local Z
        hx_local = sy * 10.0  # half-extent along LDraw X in identity
        hz_local = sx * 10.0  # half-extent along LDraw Z in identity
        hy = h * 8.0

        if rot == IDN or rot == Y180:
            hx, hz = hx_local, hz_local
        elif rot == Y90 or rot == Y270:
            hx, hz = hz_local, hx_local
        else:
            hx, hz = max(hx_local, hz_local), max(hx_local, hz_local)

        boxes.append((x - hx, x + hx, y, y + hy, z - hz, z + hz, part, x, y, z))

    print(f"Boxes computed: {len(boxes)}")

    # Spatial grid: bucket by (cell_x, cell_z) with cell size 100 LDU
    CELL = 100.0
    grid = defaultdict(list)
    for idx, b in enumerate(boxes):
        cx0 = int(b[0] // CELL)
        cx1 = int(b[1] // CELL)
        cz0 = int(b[4] // CELL)
        cz1 = int(b[5] // CELL)
        for cx in range(cx0, cx1 + 1):
            for cz in range(cz0, cz1 + 1):
                grid[(cx, cz)].append(idx)

    collision_set = set()
    for cell_indices in grid.values():
        for ii in range(len(cell_indices)):
            for jj in range(ii + 1, len(cell_indices)):
                i, j = cell_indices[ii], cell_indices[jj]
                if i > j:
                    i, j = j, i
                if (i, j) in collision_set:
                    continue
                a = boxes[i]
                b = boxes[j]
                if (a[0] < b[1] and a[1] > b[0] and
                    a[2] < b[3] and a[3] > b[2] and
                    a[4] < b[5] and a[5] > b[4]):
                    collision_set.add((i, j))

    collision_pairs = sorted(collision_set)
    print(f"Total collision pairs: {len(collision_pairs)}")

    slope_slope = slope_flat = flat_flat = 0
    for i, j in collision_pairs:
        a_slope = boxes[i][6] in slope_parts
        b_slope = boxes[j][6] in slope_parts
        if a_slope and b_slope:
            slope_slope += 1
        elif a_slope or b_slope:
            slope_flat += 1
        else:
            flat_flat += 1
    print(f"  slope-slope: {slope_slope}, slope-flat: {slope_flat}, flat-flat: {flat_flat}")

    # Count collisions involving slope at y vs brick at y+1 or y+2
    y_gap_hist = defaultdict(int)
    for i, j in collision_pairs:
        a = boxes[i]
        b = boxes[j]
        # Which is higher (more negative Y = higher in LDraw)
        if a[2] < b[2]:  # a is higher
            gap = b[2] - a[2]
        else:
            gap = a[2] - b[2]
        y_gap_hist[round(gap)] += 1
    print("Y-gap distribution (LDU between tops):")
    for gap in sorted(y_gap_hist.keys()):
        print(f"  gap={gap} LDU ({gap/8:.1f} plates): {y_gap_hist[gap]} pairs")

    for idx, (i, j) in enumerate(collision_pairs[:20]):
        a = boxes[i]
        b = boxes[j]
        print(f"  #{idx}: {a[6]}@({a[7]},{a[8]},{a[9]}) vs {b[6]}@({b[7]},{b[8]},{b[9]})")
        print(f"    A: x[{a[0]:.0f},{a[1]:.0f}] y[{a[2]:.0f},{a[3]:.0f}] z[{a[4]:.0f},{a[5]:.0f}]")
        print(f"    B: x[{b[0]:.0f},{b[1]:.0f}] y[{b[2]:.0f},{b[3]:.0f}] z[{b[4]:.0f},{b[5]:.0f}]")
        y_overlap = min(a[3], b[3]) - max(a[2], b[2])
        print(f"    Y-overlap: {y_overlap:.0f} LDU ({y_overlap/8:.1f} plates)")

if __name__ == "__main__":
    main()
