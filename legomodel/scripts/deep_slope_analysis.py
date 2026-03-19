#!/usr/bin/env python3
"""Deep analysis: check height distribution, slope neighborhoods, and staircase patterns."""
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
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part, 'color': color,
            'h': h,
            'xmin': x - hx, 'xmax': x + hx,
            'ymin': y, 'ymax': y + h*8,
            'zmin': z - hz, 'zmax': z + hz,
            'is_slope': part in slope_parts
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]

# Height distribution
h_dist = defaultdict(int)
for b in flats:
    h_dist[b['h']] += 1
print(f"Flat brick height distribution: {dict(sorted(h_dist.items()))}")
print(f"  h=1 (plates): {h_dist.get(1,0)}")
print(f"  h=3 (bricks): {h_dist.get(3,0)}")

# Part type distribution  
part_dist = defaultdict(int)
for b in bricks:
    part_dist[b['part']] += 1
print(f"\nPart distribution:")
for k, v in sorted(part_dist.items(), key=lambda x: -x[1]):
    is_s = "SLOPE" if k in slope_parts else "flat"
    print(f"  {k}: {v} ({is_s})")

# For each slope, find its IMMEDIATE neighbors (touching bounding boxes)
# and check height mismatch
height_mismatch_count = 0
same_height_count = 0
for s in slopes:
    for f in flats:
        # Check if touching in XZ and sharing Y range
        x_touch = (abs(s['xmin'] - f['xmax']) < 0.1 or abs(s['xmax'] - f['xmin']) < 0.1) and \
                  not (s['xmin'] >= f['xmax'] + 0.1 or f['xmin'] >= s['xmax'] + 0.1)
        z_touch = (abs(s['zmin'] - f['zmax']) < 0.1 or abs(s['zmax'] - f['zmin']) < 0.1) and \
                  not (s['zmin'] >= f['zmax'] + 0.1 or f['zmin'] >= s['zmax'] + 0.1)
        # XZ must touch (one axis touching, other overlapping or touching)
        x_overlap = s['xmin'] < f['xmax'] + 0.1 and s['xmax'] > f['xmin'] - 0.1
        z_overlap = s['zmin'] < f['zmax'] + 0.1 and s['zmax'] > f['zmin'] - 0.1
        if not ((x_touch and z_overlap) or (z_touch and x_overlap)):
            continue
        # Y range overlap
        if s['ymin'] >= f['ymax'] or s['ymax'] <= f['ymin']:
            continue
        if abs(s['ymin'] - f['ymin']) < 0.1 and abs(s['ymax'] - f['ymax']) < 0.1:
            same_height_count += 1
        else:
            height_mismatch_count += 1

print(f"\nSlope-flat touching pairs with same height range: {same_height_count}")
print(f"Slope-flat touching pairs with height MISMATCH: {height_mismatch_count}")

# Count how many flat bricks have their LDR center WITHIN the slope's
# WEDGE geometry (not just bounding box)
# For a NORTH-facing slope, the wedge goes from (top, z_min) to (bottom, z_max)
# At any Y level within the slope, the slope face is at:
#   z_face = z_min + (y - y_min) / (y_max - y_min) * (z_max - z_min)  
# (for NORTH: y_min is top, y_max is bottom. LDraw Y is negative-up)
# A point is inside the wedge if z < z_face

def get_facing(rot):
    if rot == IDN: return "NORTH"
    if rot == Y180: return "SOUTH"
    if rot == Y270: return "EAST"
    if rot == Y90: return "WEST"
    return "UNKNOWN"

inside_wedge = 0
for s in slopes:
    facing = get_facing(s['rot'])
    # Check if any flat brick center falls inside the slope's wedge
    for f in flats:
        fx, fy, fz = f['x'], f['y'], f['z']
        # Must be within bounding box first
        if not (s['xmin'] <= fx <= s['xmax'] and s['ymin'] <= fy <= s['ymax']):
            continue
        # Now check wedge geometry based on facing
        # Normalized Y position within slope (0=top, 1=bottom)
        if s['ymax'] - s['ymin'] < 0.1:
            continue
        t = (fy - s['ymin']) / (s['ymax'] - s['ymin'])  # 0=top, 1=bottom
        
        if facing == "NORTH":
            # Slope face goes from z_min (top) to z_max (bottom)
            # The face at normalized position t is at z_face = z_min + t * depth
            z_face = s['zmin'] + t * (s['zmax'] - s['zmin'])
            if s['zmin'] <= fz <= z_face:
                inside_wedge += 1
        elif facing == "SOUTH":
            z_face = s['zmax'] - t * (s['zmax'] - s['zmin'])
            if z_face <= fz <= s['zmax']:
                inside_wedge += 1
        elif facing == "EAST":
            x_face = s['xmax'] - t * (s['xmax'] - s['xmin'])
            if x_face <= fx <= s['xmax']:
                inside_wedge += 1
        elif facing == "WEST":
            x_face = s['xmin'] + t * (s['xmax'] - s['xmin'])
            if s['xmin'] <= fx <= x_face:
                inside_wedge += 1

print(f"\nFlat brick centers inside slope WEDGE geometry: {inside_wedge}")
