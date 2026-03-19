#!/usr/bin/env python3
"""Analyze LDR for visual overlap causes — slopes whose LDraw geometry overlaps neighbors."""
import sys
from collections import defaultdict

ldr_path = sys.argv[1] if len(sys.argv) > 1 else "lab_sloped2.ldr"

bricks = []
with open(ldr_path) as f:
    for line in f:
        if not line.startswith("1 "):
            continue
        parts = line.strip().split()
        x = float(parts[2])
        y = float(parts[3])
        z = float(parts[4])
        rot = " ".join(parts[5:14])
        part = parts[14]
        bricks.append((x, y, z, rot, part))

slope_parts = {"3037.dat","3039.dat","3040b.dat","3298.dat","4286.dat","85984.dat"}

IDN = "1 0 0 0 1 0 0 0 1"
Y90 = "0 0 1 0 1 0 -1 0 0"
Y180 = "-1 0 0 0 1 0 0 0 -1"
Y270 = "0 0 -1 0 1 0 1 0 0"
rot_to_facing = {IDN: "NORTH", Y180: "SOUTH", Y270: "EAST", Y90: "WEST"}

# Part dims: (catalog_stud_x, catalog_stud_y, height_plates)
part_dims = {
    "3005.dat": (1,1,3), "3004.dat": (1,2,3), "3003.dat": (2,2,3), "3001.dat": (2,4,3),
    "3024.dat": (1,1,1), "3023.dat": (1,2,1), "3022.dat": (2,2,1), "3020.dat": (2,4,1),
    "3710.dat": (1,4,1), "3010.dat": (1,4,3), "3622.dat": (1,3,3), "3021.dat": (2,3,1),
    "3002.dat": (2,3,3),
    "3037.dat": (2,4,3), "3039.dat": (2,2,3), "3040b.dat": (2,1,3),
    "3298.dat": (3,2,3), "4286.dat": (3,1,3), "85984.dat": (1,2,2),
}

# Compute LDraw bounding boxes using TRUE geometry extents
# LDraw identity: catalog stud_y -> local X, catalog stud_x -> local Z
def compute_box(x, y, z, rot, part):
    dims = part_dims.get(part)
    if dims is None:
        return None
    sx, sy, h = dims
    hx_local = sy * 10.0  # half-extent X (identity)
    hz_local = sx * 10.0  # half-extent Z (identity)
    hy = h * 8.0

    if rot in (IDN, Y180):
        hx, hz = hx_local, hz_local
    elif rot in (Y90, Y270):
        hx, hz = hz_local, hx_local
    else:
        hx, hz = max(hx_local, hz_local), max(hx_local, hz_local)
    return (x - hx, x + hx, y, y + hy, z - hz, z + hz)

# Check for duplicate positions
pos_map = defaultdict(list)
for b in bricks:
    key = (b[0], b[1], b[2])
    pos_map[key].append(b)
dupes = {k: v for k, v in pos_map.items() if len(v) > 1}
print(f"Positions with >1 brick: {len(dupes)}")
for (x,y,z), bs in list(dupes.items())[:10]:
    print(f"  ({x},{y},{z}): {[(b[4], rot_to_facing.get(b[3], '?')) for b in bs]}")

# Slope summary
print(f"\nTotal slopes: {sum(1 for b in bricks if b[4] in slope_parts)}")
print(f"Total flat: {sum(1 for b in bricks if b[4] not in slope_parts)}")

# Check adjacent slope-flat pairs where the slope's angled face might visually
# penetrate the neighbor. In LDraw, slope geometry slopes from full height on 
# one side to zero on the other. The bounding box is correct, but the diagonal
# face cuts through the box.
# The real collision question: does a slope's geometry extend BEYOND its bounding box?
# Answer: NO — slopes are within their bounding box. But adjacent bricks that 
# touch the slope's bounding box edge might visually appear to intersect the 
# sloped face.

# Check if markCovered fix is actually in the jar by looking at the build
print("\nSlope Y-level distribution:")
slope_y = defaultdict(int)
for b in bricks:
    if b[4] in slope_parts:
        slope_y[b[1]] += 1
for y in sorted(slope_y.keys()):
    print(f"  LDraw Y={y}: {slope_y[y]} slopes")

# Now check the actual collision using the REAL geometry-aware method
# Slope parts have wedge shape - the true collision detection should account
# for the actual triangular cross-section.
# For now, let's check if there are bricks whose bounding boxes overlap
# (using a stricter check than the previous script)
print("\nStrict bounding-box check (with < instead of <=)...")
boxes = []
for b in bricks:
    box = compute_box(*b)
    if box:
        boxes.append((*box, b[4], b[0], b[1], b[2], b[3]))

CELL = 100.0
grid = defaultdict(list)
for idx, box in enumerate(boxes):
    cx0 = int(box[0] // CELL)
    cx1 = int(box[1] // CELL)
    cz0 = int(box[4] // CELL)
    cz1 = int(box[5] // CELL)
    for cx in range(cx0, cx1 + 1):
        for cz in range(cz0, cz1 + 1):
            grid[(cx, cz)].append(idx)

collision_set = set()
for cell_indices in grid.values():
    for ii in range(len(cell_indices)):
        for jj in range(ii + 1, len(cell_indices)):
            i, j = cell_indices[ii], cell_indices[jj]
            if i > j: i, j = j, i
            if (i, j) in collision_set: continue
            a, b = boxes[i], boxes[j]
            # Strict overlap: interior intersection only (not touching edges)
            if (a[0] < b[1] and a[1] > b[0] and
                a[2] < b[3] and a[3] > b[2] and
                a[4] < b[5] and a[5] > b[4]):
                collision_set.add((i, j))

print(f"Strict collisions: {len(collision_set)}")

# Now let's check if slopes placed at same voxel-Y but different LDraw-Y
# could cause visual issues due to height difference
# slope at vy=0, h=3: LDraw y = -(0*8+24) = -24, bottom at 0
# flat  at vy=0, h=1: LDraw y = -(0*8+8) = -8, bottom at 0 
# They share the same BOTTOM (y=0) but different tops.
# Are there situations where a slope and flat share the same bottom?
print("\nChecking for bricks sharing same bottom Y...")
bottom_map = defaultdict(list)
for b in bricks:
    box = compute_box(*b)
    if box:
        bottom_y = box[3]  # y + hy
        bottom_map[(b[0], bottom_y, b[2])].append((b[4], rot_to_facing.get(b[3], "?")))
shared_bottom = {k: v for k, v in bottom_map.items() if len(v) > 1}
print(f"Positions sharing same (x, bottom_y, z): {len(shared_bottom)}")
for (x,by,z), bs in list(shared_bottom.items())[:10]:
    print(f"  ({x}, bottom={by}, {z}): {bs}")
