package com.lego.optimize;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-space occupancy and coverage masks for a part orientation.
 */
public record PartMask(
    List<VoxelOffset> solidOccupancyMask,
    List<VoxelOffset> topCoverageMask
) {
    public PartMask {
        if (solidOccupancyMask == null || solidOccupancyMask.isEmpty()) {
            throw new IllegalArgumentException("solidOccupancyMask must not be null/empty");
        }
        if (topCoverageMask == null || topCoverageMask.isEmpty()) {
            throw new IllegalArgumentException("topCoverageMask must not be null/empty");
        }
        solidOccupancyMask = List.copyOf(solidOccupancyMask);
        topCoverageMask = List.copyOf(topCoverageMask);
    }

    /**
     * Builds a rectangular cuboid mask in local coordinates.
     */
    public static PartMask cuboid(int studX, int studY, int heightUnits) {
        if (studX <= 0 || studY <= 0 || heightUnits <= 0) {
            throw new IllegalArgumentException("Mask dimensions must be positive");
        }
        List<VoxelOffset> voxels = new ArrayList<>(studX * studY * heightUnits);
        for (int dy = 0; dy < heightUnits; dy++) {
            for (int dx = 0; dx < studX; dx++) {
                for (int dz = 0; dz < studY; dz++) {
                    voxels.add(new VoxelOffset(dx, dy, dz));
                }
            }
        }
        return new PartMask(voxels, voxels);
    }

    /**
     * Local voxel offset from an anchor position.
     */
    public record VoxelOffset(int dx, int dy, int dz) {}
}
