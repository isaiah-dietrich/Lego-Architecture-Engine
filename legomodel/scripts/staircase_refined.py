#!/usr/bin/env python3
"""Refined staircase artifact: only count flat bricks within 2 studs (40 LDU) of slope face."""
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

bricks = []
with open(ldr_path) as f:
    for line in f:
        if not line.startswith("1 "): continue
        parts = line.strip().split()
        x, y, z = float(parts[2]), float(parts[3]), float(parts[4])
        rot = " ".join(parts[5:14])
        part = parts[14]
        dims = part_dims.get(part)
        if not dims: continue
        sx, sy, h = dims
        hx_local = sy * 10.0
        hz_local = sx * 10.0
        if rot in (IDN, Y180):
            hx, hz = hx_local, hz_local
        elif rot in (Y90, Y270):
            hx, hz = hz_local, hx_local
        else:
            hx, hz = max(hx_local, hz_local), max(hx_local, hz_local)
        vy = round((-y / 8) - h)
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part, 'h': h,
            'xmin': x - hx, 'xmax': x + hx,
            'ymin': y, 'ymax': y + h*8,
            'zmin': z - hz, 'zmax': z + hz,
            'is_slope': part in slope_parts, 'vy': vy
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]
MAX_DIST = 40  # 2 studs = 40 LDU

artifact_front = 0  # flat in front of slope face (staircase steps UP)
artifact_behind = 0  # flat behind slope back (staircase steps DOWN)
examples = []

for s in slopes:
    face = s['rot']
    for f in flats:
        # Must share X range
        if s['xmin'] >= f['xmax'] or s['xmax'] <= f['xmin']:
            continue
        # Must share Y range
        if s['ymin'] >= f['ymax'] or s['ymax'] <= f['ymin']:
            continue
        
        # Check distance and direction based on facing
        if face == IDN:  # NORTH, slope face → -Z
            # Front of slope face: flat at z < slope_zmin, within MAX_DIST
            if f['zmax'] <= s['zmin'] + 0.1 and s['zmin'] - f['zmin'] <= MAX_DIST:
                artifact_front += 1
                if len(examples) < 5: examples.append((s, f, "NORTH-front"))
            # Behind slope back: flat at z > slope_zmax, within MAX_DIST
            if f['zmin'] >= s['zmax'] - 0.1 and f['zmax'] - s['zmax'] <= MAX_DIST:
                artifact_behind += 1
        elif face == Y180:  # SOUTH, slope face → +Z
            if f['zmin'] >= s['zmax'] - 0.1 and f['zmax'] - s['zmax'] <= MAX_DIST:
                artifact_front += 1
                if len(examples) < 5: examples.append((s, f, "SOUTH-front"))
            if f['zmax'] <= s['zmin'] + 0.1 and s['zmin'] - f['zmin'] <= MAX_DIST:
                artifact_behind += 1
        elif face == Y270:  # EAST, slope face → +X
            if f['xmin'] >= s['xmax'] - 0.1 and f['xmax'] - s['xmax'] <= MAX_DIST:
                artifact_front += 1
                if len(examples) < 5: examples.append((s, f, "EAST-front"))
            if f['xmax'] <= s['xmin'] + 0.1 and s['xmin'] - f['xmin'] <= MAX_DIST:
                artifact_behind += 1
        elif face == Y90:  # WEST, slope face → -X
            if f['xmax'] <= s['xmin'] + 0.1 and s['xmin'] - f['xmin'] <= MAX_DIST:
                artifact_front += 1
                if len(examples) < 5: examples.append((s, f, "WEST-front"))
            if f['xmin'] >= s['xmax'] - 0.1 and f['xmax'] - s['xmax'] <= MAX_DIST:
                artifact_behind += 1

print(f"Staircase artifacts within {MAX_DIST} LDU:")
print(f"  Flats in front of slope face (visible through slope): {artifact_front}")
print(f"  Flats behind slope back (adjacent at base): {artifact_behind}")
print(f"  Total: {artifact_front + artifact_behind}")

for i, (s, f, desc) in enumerate(examples):
    print(f"\n  #{i} ({desc}): Slope {s['part']}")
    print(f"    Slope: Y[{s['ymin']:.0f},{s['ymax']:.0f}] Z[{s['zmin']:.0f},{s['zmax']:.0f}] X[{s['xmin']:.0f},{s['xmax']:.0f}] vy={s['vy']}")
    print(f"    Flat:  {f['part']} Y[{f['ymin']:.0f},{f['ymax']:.0f}] Z[{f['zmin']:.0f},{f['zmax']:.0f}] X[{f['xmin']:.0f},{f['xmax']:.0f}] vy={f['vy']}")
    y_overlap = min(s['ymax'], f['ymax']) - max(s['ymin'], f['ymin'])
    print(f"    Y overlap: {y_overlap:.0f} LDU, height mismatch: slope h={s['h']} flat h={f['h']}")
