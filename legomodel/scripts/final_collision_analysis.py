#!/usr/bin/env python3
"""Final analysis: check for TRUE overlap using strict bounds, and look for
 flat bricks whose geometry GENUINELY intersects slope wedge geometry."""
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
        # Derive voxel position
        vy = round((-y / 8) - h)
        bricks.append({
            'x': x, 'y': y, 'z': z, 'rot': rot, 'part': part, 'h': h,
            'xmin': x - hx, 'xmax': x + hx,
            'ymin': y, 'ymax': y + h*8,
            'zmin': z - hz, 'zmax': z + hz,
            'is_slope': part in slope_parts,
            'vy': vy  # voxel y
        })

slopes = [b for b in bricks if b['is_slope']]
flats = [b for b in bricks if not b['is_slope']]

print(f"Slopes: {len(slopes)}, Flats: {len(flats)}")

# Check with STRICT bounds: flat brick center STRICTLY inside slope bbox
strict_inside = 0
for s in slopes:
    sb = s
    for f in flats:
        if (sb['xmin'] < f['x'] < sb['xmax'] and
            sb['ymin'] < f['y'] < sb['ymax'] and
            sb['zmin'] < f['z'] < sb['zmax']):
            strict_inside += 1
            if strict_inside <= 5:
                print(f"\n  STRICT INSIDE #{strict_inside}:")
                print(f"    Slope {s['part']} @ LDR({s['x']},{s['y']},{s['z']}) rot={s['rot']} vy={s['vy']}")
                print(f"    box: X[{s['xmin']:.0f},{s['xmax']:.0f}] Y[{s['ymin']:.0f},{s['ymax']:.0f}] Z[{s['zmin']:.0f},{s['zmax']:.0f}]")
                print(f"    Flat {f['part']} @ LDR({f['x']},{f['y']},{f['z']}) vy={f['vy']}")
                print(f"    box: X[{f['xmin']:.0f},{f['xmax']:.0f}] Y[{f['ymin']:.0f},{f['ymax']:.0f}] Z[{f['zmin']:.0f},{f['zmax']:.0f}]")

print(f"\nFlat brick centers STRICTLY inside slope bbox: {strict_inside}")

# Now look at voxel-space relationships:
# Find slopes and flats at the same voxel (x,z) but the flat's voxel y
# is within the slope's voxel y range (y..y+h-1)
# This would indicate overlapping placement in voxel space
# 
# Convert LDR positions to approximate voxel positions
def ldr_to_voxel_ranges(b):
    """Return approximate voxel x,y,z ranges from LDR brick data."""
    # X: [xmin/20, xmax/20)
    vxmin = round(b['xmin'] / 20)
    vxmax = round(b['xmax'] / 20)
    # Z: [zmin/20, zmax/20)
    vzmin = round(b['zmin'] / 20)
    vzmax = round(b['zmax'] / 20)
    # Y: vy to vy+h
    vymin = b['vy']
    vymax = b['vy'] + b['h']
    return vxmin, vxmax, vymin, vymax, vzmin, vzmax

voxel_overlaps = 0
for s in slopes:
    svx0, svx1, svy0, svy1, svz0, svz1 = ldr_to_voxel_ranges(s)
    for f in flats:
        fvx0, fvx1, fvy0, fvy1, fvz0, fvz1 = ldr_to_voxel_ranges(f)
        # Check voxel-space overlap
        if (svx0 < fvx1 and svx1 > fvx0 and
            svy0 < fvy1 and svy1 > fvy0 and
            svz0 < fvz1 and svz1 > fvz0):
            voxel_overlaps += 1
            if voxel_overlaps <= 5:
                print(f"\n  VOXEL OVERLAP #{voxel_overlaps}:")
                print(f"    Slope {s['part']} voxel X[{svx0},{svx1}) Y[{svy0},{svy1}) Z[{svz0},{svz1})")
                print(f"    Flat  {f['part']} voxel X[{fvx0},{fvx1}) Y[{fvy0},{fvy1}) Z[{fvz0},{fvz1})")

print(f"\nVoxel-space overlaps (slope vs flat): {voxel_overlaps}")

# Summary: what does the staircase look like around slopes?
# For each slope, show the flat bricks that are directly below (vy-1) 
# at overlapping XZ, and the flat bricks directly above (vy+h)
below_count = 0
above_count = 0
for s in slopes:
    svx0, svx1, svy0, svy1, svz0, svz1 = ldr_to_voxel_ranges(s)
    for f in flats:
        fvx0, fvx1, fvy0, fvy1, fvz0, fvz1 = ldr_to_voxel_ranges(f)
        # XZ must overlap
        if not (svx0 < fvx1 and svx1 > fvx0 and svz0 < fvz1 and svz1 > fvz0):
            continue
        # Below: flat brick's top (fvy1) == slope's bottom (svy0)
        if fvy1 == svy0:
            below_count += 1
        # Above: flat brick's bottom (fvy0) == slope's top (svy1)
        if fvy0 == svy1:
            above_count += 1

print(f"\nFlat bricks directly BELOW slopes (same XZ, adjacent Y): {below_count}")
print(f"Flat bricks directly ABOVE slopes (same XZ, adjacent Y): {above_count}")
