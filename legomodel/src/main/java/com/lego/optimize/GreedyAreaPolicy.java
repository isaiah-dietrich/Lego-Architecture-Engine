package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Legacy greedy placement: always picks the largest-area brick that fits.
 *
 * <p>This is the original placement strategy. It minimizes piece count but
 * may overhang into regions where not all voxels are filled, reducing
 * surface accuracy.</p>
 */
public final class GreedyAreaPolicy implements PlacementPolicy {

    @Override
    public String name() {
        return "greedy-area";
    }

    @Override
    public Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                              int x, int y, int z, List<BrickSpec> allowedSpecs) {
        for (BrickSpec spec : allowedSpecs) {
            if (canPlace(surface, covered, x, y, z, spec.studX(), spec.studY())) {
                return new Brick(x, y, z, spec.studX(), spec.studY(), spec.heightUnits(), spec.partId());
            }
        }

        throw new IllegalStateException(
            "Cannot place any brick at (" + x + "," + y + "," + z + "). " +
            "Allowed dimensions must include 1x1 as fallback."
        );
    }

    private static boolean canPlace(VoxelGrid surface, boolean[][][] covered,
                                     int x, int y, int z, int studX, int studY) {
        for (int dx = 0; dx < studX; dx++) {
            for (int dz = 0; dz < studY; dz++) {
                int cx = x + dx;
                int cz = z + dz;
                if (cx >= surface.width() || cz >= surface.depth()) {
                    return false;
                }
                if (!surface.isFilled(cx, y, cz) || covered[cx][y][cz]) {
                    return false;
                }
            }
        }
        return true;
    }
}
