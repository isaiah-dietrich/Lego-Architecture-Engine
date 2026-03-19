#!/usr/bin/env python3
"""Staircase artifact analysis: find flat bricks in front of slopes that would
be visible through the slope's angled face. These appear to be 'inside' the slope."""
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

def facing_from_rot(rot):
    if rot == IDN: return "NORTH"
    if rot == Y180: return "SOUTH"
    if rot == Y270: return "EAST"
    if rot == Y90: return "WEST"
    return "UNKNOWN"

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
        # Derive voxel position
        vy = round((-y / 8) - h)
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part, 'h': h,
            'xmin': x - hx, 'xmax': x + hx,
            'ymin': y, 'ymax': y + h*8,
            'zmin': z - hz, 'zmax': z + hz,
            'is_slope': part in slope_parts,
            'vy': vy,
            'vx_min': round((x - hx) / 20),
            'vx_max': round((x + hx) / 20),
            'vz_min': round((z - hz) / 20),
            'vz_max': round((z + hz) / 20),
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]

print(f"Slopes: {len(slopes)}, Flats: {len(flats)}")

# The staircase artifact: flat bricks in front of the slope face that are
# at higher voxel Y levels (the staircase steps above the slope).
# These bricks are visible through the slope's angled face.
#
# For a NORTH-facing slope at voxel (vx, vy, vz) with studY depth:
#   - At voxel level vy+k: look for flat bricks at z < vz (in front of slope)
#   - These bricks' LDraw Y range overlaps with the slope's upper portion

artifact_count = 0
artifact_examples = []

for s in slopes:
    face = facing_from_rot(s['rot'])
    
    for f in flats:
        # Check X overlap (must share X range)
        if s['xmin'] >= f['xmax'] or s['xmax'] <= f['xmin']:
            continue
        # Check Y overlap (must share height range)
        if s['ymin'] >= f['ymax'] or s['ymax'] <= f['ymin']:
            continue
        
        # Now check: is the flat brick in the slope-face direction?
        in_face_direction = False
        if face == "NORTH" and f['zmax'] <= s['zmin'] + 0.1:  # flat is at z < slope_zmin (in front)
            in_face_direction = True
        elif face == "SOUTH" and f['zmin'] >= s['zmax'] - 0.1:  # flat is at z > slope_zmax (behind)
            in_face_direction = True
        elif face == "EAST" and f['xmin'] >= s['xmax'] - 0.1:  # flat is at x > slope_xmax
            in_face_direction = True
        elif face == "WEST" and f['xmax'] <= s['xmin'] + 0.1:  # flat is at x < slope_xmin
            in_face_direction = True
            
        if in_face_direction and f['vy'] > s['vy']:  # flat is at higher voxel Y (staircase step above)
            artifact_count += 1
            if len(artifact_examples) < 10:
                artifact_examples.append((s, f, face))

print(f"\nStaircase artifacts (flats in front of slope face at higher Y): {artifact_count}")

# Also check: flat bricks BEHIND the slope base (opposite of slope face)
# that share height range. These would be at lower voxel Y.
behind_count = 0
for s in slopes:
    face = facing_from_rot(s['rot'])
    for f in flats:
        if s['xmin'] >= f['xmax'] or s['xmax'] <= f['xmin']:
            continue
        if s['ymin'] >= f['ymax'] or s['ymax'] <= f['ymin']:
            continue
        
        behind = False
        if face == "NORTH" and f['zmin'] >= s['zmax'] - 0.1:
            behind = True
        elif face == "SOUTH" and f['zmax'] <= s['zmin'] + 0.1:
            behind = True
        elif face == "EAST" and f['xmax'] <= s['xmin'] + 0.1:
            behind = True
        elif face == "WEST" and f['xmin'] >= s['xmax'] - 0.1:
            behind = True
            
        if behind and f['vy'] < s['vy']:
            behind_count += 1

print(f"Flats behind slope base at lower Y (also potentially visible): {behind_count}")

# Print examples
for i, (s, f, face) in enumerate(artifact_examples):
    print(f"\n  Example #{i}: Slope {s['part']} facing {face}")
    print(f"    Slope voxel: vy={s['vy']}, vz=[{s['vz_min']},{s['vz_max']}), vx=[{s['vx_min']},{s['vx_max']})")
    print(f"    Flat  {f['part']} vy={f['vy']}, vz=[{f['vz_min']},{f['vz_max']}), vx=[{f['vx_min']},{f['vx_max']})")
    print(f"    Y ranges: slope[{s['ymin']:.0f},{s['ymax']:.0f}] flat[{f['ymin']:.0f},{f['ymax']:.0f}]")
    print(f"    Z ranges: slope[{s['zmin']:.0f},{s['zmax']:.0f}] flat[{f['zmin']:.0f},{f['zmax']:.0f}]")
