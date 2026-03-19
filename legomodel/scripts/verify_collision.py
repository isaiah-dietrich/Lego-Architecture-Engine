#!/usr/bin/env python3
"""Verify: are flat bricks actually inside slope bounding boxes? Cross-check collision vs wedge."""
import sys

ldr_path = sys.argv[1] if len(sys.argv) > 1 else "lab_sloped2.ldr"

part_dims = {
    "3005.dat": (1,1,3), "3004.dat": (1,2,3), "3003.dat": (2,2,3),
    "3001.dat": (2,4,3), "3002.dat": (2,3,3),
    "3024.dat": (1,1,1), "3023.dat": (1,2,1), "3022.dat": (2,2,1),
    "3020.dat": (2,4,1), "3021.dat": (2,3,1),
    "3010.dat": (1,4,3), "3622.dat": (1,3,3), "3710.dat": (1,4,1),
    "3037.dat": (2,4,3), "3039.dat": (2,2,3), "3040b.dat": (2,1,3),
    "3298.dat": (3,2,3), "4286.dat": (3,1,3), "85984.dat": (1,2,2),
}
slope_parts = {"3037.dat","3039.dat","3040b.dat","3298.dat","4286.dat","85984.dat"}

IDN = "1 0 0 0 1 0 0 0 1"
Y90 = "0 0 1 0 1 0 -1 0 0"
Y180 = "-1 0 0 0 1 0 0 0 -1"
Y270 = "0 0 -1 0 1 0 1 0 0"

def get_box(x, y, z, rot, part):
    dims = part_dims.get(part)
    if not dims: return None
    sx, sy, h = dims
    hx_local = sy * 10.0
    hz_local = sx * 10.0
    hy = h * 8.0
    if rot in (IDN, Y180):
        hx, hz = hx_local, hz_local
    elif rot in (Y90, Y270):
        hx, hz = hz_local, hx_local
    else:
        hx, hz = max(hx_local, hz_local), max(hx_local, hz_local)
    return (x - hx, x + hx, y, y + hy, z - hz, z + hz)

bricks = []
with open(ldr_path) as f:
    for line in f:
        if not line.startswith("1 "): continue
        parts = line.strip().split()
        x, y, z = float(parts[2]), float(parts[3]), float(parts[4])
        rot = " ".join(parts[5:14])
        part = parts[14]
        box = get_box(x, y, z, rot, part)
        if not box: continue
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part,
            'box': box, 'is_slope': part in slope_parts
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]

# For each slope, find flat bricks whose CENTER is within the slope's bounding box
center_inside = []
for s in slopes:
    sb = s['box']
    for f in flats:
        fx, fy, fz = f['x'], f['y'], f['z']
        if sb[0] <= fx <= sb[1] and sb[2] <= fy <= sb[3] and sb[4] <= fz <= sb[5]:
            center_inside.append((s, f))

print(f"Flat brick centers inside slope BOUNDING BOX (all 3 dims): {len(center_inside)}")

# Now verify: do these pairs also have overlapping bounding boxes?
bbox_overlap = 0
for s, f in center_inside:
    a, b = s['box'], f['box']
    if (a[0] < b[1] and a[1] > b[0] and
        a[2] < b[3] and a[3] > b[2] and
        a[4] < b[5] and a[5] > b[4]):
        bbox_overlap += 1

print(f"Of those, with bounding box OVERLAP (strict </>): {bbox_overlap}")

# Print first 10 center-inside pairs
for i, (s, f) in enumerate(center_inside[:10]):
    sb, fb = s['box'], f['box']
    print(f"\n  #{i}: Slope {s['part']} @ ({s['x']},{s['y']},{s['z']}) rot={s['rot']}")
    print(f"    Slope box: X[{sb[0]:.0f},{sb[1]:.0f}] Y[{sb[2]:.0f},{sb[3]:.0f}] Z[{sb[4]:.0f},{sb[5]:.0f}]")
    print(f"    Flat  {f['part']} @ ({f['x']},{f['y']},{f['z']})")
    print(f"    Flat  box: X[{fb[0]:.0f},{fb[1]:.0f}] Y[{fb[2]:.0f},{fb[3]:.0f}] Z[{fb[4]:.0f},{fb[5]:.0f}]")
    # Check individual axis overlap
    xov = a[0] < b[1] and a[1] > b[0]
    yov = a[2] < b[3] and a[3] > b[2]
    zov = a[4] < b[5] and a[5] > b[4]
    print(f"    X overlap: {sb[0]}<{fb[1]} and {sb[1]}>{fb[0]} = {sb[0]<fb[1] and sb[1]>fb[0]}")
    print(f"    Y overlap: {sb[2]}<{fb[3]} and {sb[3]}>{fb[2]} = {sb[2]<fb[3] and sb[3]>fb[2]}")
    print(f"    Z overlap: {sb[4]}<{fb[5]} and {sb[5]}>{fb[4]} = {sb[4]<fb[5] and sb[5]>fb[4]}")
