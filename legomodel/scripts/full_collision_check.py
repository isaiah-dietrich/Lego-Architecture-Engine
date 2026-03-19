#!/usr/bin/env python3
"""Complete collision detection — finds ALL overlapping brick pairs in LDR output."""
import sys
from collections import defaultdict

ldr_path = sys.argv[1] if len(sys.argv) > 1 else "lab_sloped2.ldr"

bricks = []
with open(ldr_path) as f:
    for line_num, line in enumerate(f, 1):
        if not line.startswith("1 "):
            continue
        parts = line.strip().split()
        x = float(parts[2])
        y = float(parts[3])
        z = float(parts[4])
        rot = " ".join(parts[5:14])
        part = parts[14]
        bricks.append((x, y, z, rot, part, line_num))

# Extract all unique part IDs
unique_parts = set(b[4] for b in bricks)
print(f"Unique parts: {sorted(unique_parts)}")

# Part dims from catalog: (catalog_stud_x, catalog_stud_y, height_plates)
# Catalog stud_x -> Z in LDraw identity, catalog stud_y -> X in LDraw identity
part_dims = {
    "3005.dat": (1,1,3), "3004.dat": (1,2,3), "3003.dat": (2,2,3),
    "3001.dat": (2,4,3), "3002.dat": (2,3,3),
    "3024.dat": (1,1,1), "3023.dat": (1,2,1), "3022.dat": (2,2,1),
    "3020.dat": (2,4,1), "3021.dat": (2,3,1),
    "3010.dat": (1,4,3), "3622.dat": (1,3,3), "3710.dat": (1,4,1),
    "3037.dat": (2,4,3), "3039.dat": (2,2,3), "3040b.dat": (2,1,3),
    "3298.dat": (3,2,3), "4286.dat": (3,1,3), "85984.dat": (1,2,2),
}

missing = unique_parts - set(part_dims.keys())
if missing:
    print(f"WARNING: Missing part dims for: {missing}")

slope_parts = {"3037.dat","3039.dat","3040b.dat","3298.dat","4286.dat","85984.dat"}

IDN = "1 0 0 0 1 0 0 0 1"
Y90 = "0 0 1 0 1 0 -1 0 0"
Y180 = "-1 0 0 0 1 0 0 0 -1"
Y270 = "0 0 -1 0 1 0 1 0 0"
rot_name = {IDN: "IDN", Y180: "Y180", Y90: "Y90", Y270: "Y270"}

boxes = []
skipped = 0
for (x, y, z, rot, part, ln) in bricks:
    dims = part_dims.get(part)
    if dims is None:
        skipped += 1
        continue
    sx, sy, h = dims
    # LDraw identity: catalog stud_y → X, catalog stud_x → Z
    hx_local = sy * 10.0
    hz_local = sx * 10.0
    hy = h * 8.0

    if rot in (IDN, Y180):
        hx, hz = hx_local, hz_local
    elif rot in (Y90, Y270):
        hx, hz = hz_local, hx_local
    else:
        hx, hz = max(hx_local, hz_local), max(hx_local, hz_local)

    boxes.append((x - hx, x + hx, y, y + hy, z - hz, z + hz,
                  part, x, y, z, rot, ln))

print(f"Total bricks: {len(bricks)}, boxes: {len(boxes)}, skipped: {skipped}")

# Spatial grid
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
            if (a[0] < b[1] and a[1] > b[0] and
                a[2] < b[3] and a[3] > b[2] and
                a[4] < b[5] and a[5] > b[4]):
                collision_set.add((i, j))

collisions = sorted(collision_set)
print(f"\nTotal collisions: {len(collisions)}")

ss = sf = ff = 0
for i, j in collisions:
    a_s = boxes[i][6] in slope_parts
    b_s = boxes[j][6] in slope_parts
    if a_s and b_s: ss += 1
    elif a_s or b_s: sf += 1
    else: ff += 1
print(f"  slope-slope: {ss}, slope-flat: {sf}, flat-flat: {ff}")

for idx, (i, j) in enumerate(collisions[:30]):
    a, b = boxes[i], boxes[j]
    ox = min(a[1],b[1]) - max(a[0],b[0])
    oy = min(a[3],b[3]) - max(a[2],b[2])
    oz = min(a[5],b[5]) - max(a[4],b[4])
    print(f"\n  #{idx}: {a[6]} (line {a[11]}) rot={rot_name.get(a[10],'?')} @ LDR({a[7]},{a[8]},{a[9]})")
    print(f"    vs {b[6]} (line {b[11]}) rot={rot_name.get(b[10],'?')} @ LDR({b[7]},{b[8]},{b[9]})")
    print(f"    A box: X[{a[0]:.0f},{a[1]:.0f}] Y[{a[2]:.0f},{a[3]:.0f}] Z[{a[4]:.0f},{a[5]:.0f}]")
    print(f"    B box: X[{b[0]:.0f},{b[1]:.0f}] Y[{b[2]:.0f},{b[3]:.0f}] Z[{b[4]:.0f},{b[5]:.0f}]")
    print(f"    Overlap: X={ox:.0f} Y={oy:.0f} Z={oz:.0f} LDU")
