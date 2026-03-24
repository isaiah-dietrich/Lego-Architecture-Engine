package com.lego.optimize;

import java.util.ArrayList;
import java.util.List;

import com.lego.model.Facing;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.PartMask.VoxelOffset;

/**
 * v1 procedural mask provider derived from part dimensions and slope metadata.
 */
public final class ProceduralPartMaskProvider implements PartMaskProvider {

    @Override
    public PartMask getMask(BrickSpec spec, Facing facing, int studX, int studY) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (studX <= 0 || studY <= 0 || spec.heightUnits() <= 0) {
            throw new IllegalArgumentException("Invalid oriented dimensions for mask generation");
        }
        if (!spec.isSlope()) {
            return PartMask.cuboid(studX, studY, spec.heightUnits());
        }

        // Procedural wedge approximation:
        // - Occupancy retracts along facing direction with height.
        // - Coverage follows occupancy for strict no-gap/no-outside constraints.
        List<VoxelOffset> solid = new ArrayList<>(studX * studY * spec.heightUnits());
        int runLength = (facing == Facing.NORTH || facing == Facing.SOUTH) ? studY : studX;
        int h = spec.heightUnits();
        for (int dy = 0; dy < h; dy++) {
            int cutoff = (int) Math.floor((double) dy * runLength / Math.max(1, h));
            for (int dx = 0; dx < studX; dx++) {
                for (int dz = 0; dz < studY; dz++) {
                    int t = facingDistanceIndex(facing, dx, dz, studX, studY);
                    if (t >= cutoff) {
                        solid.add(new VoxelOffset(dx, dy, dz));
                    }
                }
            }
        }
        if (solid.isEmpty()) {
            // Guaranteed fallback for pathological dimensions.
            return PartMask.cuboid(studX, studY, 1);
        }
        return new PartMask(solid, solid);
    }

    private static int facingDistanceIndex(Facing facing, int dx, int dz, int studX, int studY) {
        return switch (facing) {
            case NORTH -> dz;                 // front at z=0
            case SOUTH -> (studY - 1 - dz);  // front at z=max
            case EAST -> (studX - 1 - dx);   // front at x=max
            case WEST -> dx;                 // front at x=0
            case NONE -> 0;
        };
    }
}
