#!/usr/bin/env python3
"""Analyze slope placement patterns: find slopes and their nearby flat bricks
that share the same LDraw Y range (visual overlap despite no bbox collision)."""
import sys
from collections import defaultdict

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

bricks = []
with open(ldr_path) as f:
    for line in f:
        if not line.startswith("1 "):
            continue
        parts = line.strip().split()
        x, y, z = float(parts[2]), float(parts[3]), float(parts[4])
        rot = " ".join(parts[5:14])
        part = parts[14]
        color = int(parts[1])
        dims = part_dims.get(part)
        if dims is None:
            continue
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
        is_slope = part in slope_parts
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part, 'color': color,
            'xmin': x - hx, 'xmax': x + hx,
            'ymin': y, 'ymax': y + hy,
            'zmin': z - hz, 'zmax': z + hz,
            'is_slope': is_slope, 'h': h
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]

print(f"Total: {len(bricks)}, Slopes: {len(slopes)}, Flats: {len(flats)}")

# Find slope-flat pairs that share Y range and are adjacent in XZ
# (XZ bounding boxes touch or nearly touch but don't overlap)
nearby_pairs = []
for s in slopes:
    for f in flats:
        # Check Y range overlap (strict)
        if s['ymin'] >= f['ymax'] or s['ymax'] <= f['ymin']:
            continue
        # Check if XZ is adjacent (gap <= 1 LDU) or overlapping
        x_gap = max(0, max(s['xmin'] - f['xmax'], f['xmin'] - s['xmax']))
        z_gap = max(0, max(s['zmin'] - f['zmax'], f['zmin'] - s['zmax']))
        if x_gap <= 1 and z_gap <= 1:
            y_overlap = min(s['ymax'], f['ymax']) - max(s['ymin'], f['ymin'])
            nearby_pairs.append((s, f, y_overlap, x_gap, z_gap))

print(f"\nSlope-flat pairs sharing Y range & adjacent/touching in XZ: {len(nearby_pairs)}")

# Show sample nearby pairs
nearby_pairs.sort(key=lambda p: -p[2])  # sort by Y overlap
for idx, (s, f, yov, xg, zg) in enumerate(nearby_pairs[:20]):
    print(f"\n  #{idx}: Slope {s['part']} color={s['color']} @ ({s['x']},{s['y']},{s['z']})")
    print(f"    box: X[{s['xmin']:.0f},{s['xmax']:.0f}] Y[{s['ymin']:.0f},{s['ymax']:.0f}] Z[{s['zmin']:.0f},{s['zmax']:.0f}]")
    print(f"    Flat  {f['part']} color={f['color']} @ ({f['x']},{f['y']},{f['z']})")
    print(f"    box: X[{f['xmin']:.0f},{f['xmax']:.0f}] Y[{f['ymin']:.0f},{f['ymax']:.0f}] Z[{f['zmin']:.0f},{f['zmax']:.0f}]")
    print(f"    Y overlap: {yov:.0f} LDU, X gap: {xg:.0f}, Z gap: {zg:.0f}")

# Check slope color distribution
slope_colors = defaultdict(int)
flat_colors = defaultdict(int)
for s in slopes:
    slope_colors[s['color']] += 1
for f in flats:
    flat_colors[f['color']] += 1
print(f"\nSlope color distribution: {dict(sorted(slope_colors.items(), key=lambda x: -x[1]))}")
# Find slopes with color 16 (default/uncolored)
c16_slopes = sum(1 for s in slopes if s['color'] == 16)
print(f"Slopes with default color (16): {c16_slopes}")

# Show voxel Y analysis: for each slope, what voxel Y is it at?
# voxel_y = -(ldr_y / 8) - h (from: ldr_y = -(vy * 8 + h * 8) → vy = -(ldr_y + h*8)/8 = -ldr_y/8 - h)
vy_distribution = defaultdict(int)
for s in slopes:
    vy = round(-s['y'] / 8 - s['h'])
    vy_distribution[vy] += 1
print(f"\nSlope voxel Y distribution: {dict(sorted(vy_distribution.items()))}")

# Check: are there flat bricks at the same LDR position as slopes?
slope_positions = set()
for s in slopes:
    slope_positions.add((s['x'], s['y'], s['z']))
overlapping_pos = 0
for f in flats:
    if (f['x'], f['y'], f['z']) in slope_positions:
        overlapping_pos += 1
print(f"\nFlats at exact same LDR center as slopes: {overlapping_pos}")

# Check: flat bricks whose center is inside a slope bounding box
flats_inside_slopes = []
for f in flats:
    for s in slopes:
        if (s['xmin'] < f['x'] < s['xmax'] and
            s['ymin'] < f['y'] < s['ymax'] and
            s['zmin'] < f['z'] < s['zmax']):
            flats_inside_slopes.append((s, f))
            break
print(f"Flat brick centers inside a slope bounding box: {len(flats_inside_slopes)}")
for s, f in flats_inside_slopes[:10]:
    print(f"  Slope {s['part']} box X[{s['xmin']:.0f},{s['xmax']:.0f}] Y[{s['ymin']:.0f},{s['ymax']:.0f}] Z[{s['zmin']:.0f},{s['zmax']:.0f}]")
    print(f"  Flat  {f['part']} center ({f['x']},{f['y']},{f['z']})")
