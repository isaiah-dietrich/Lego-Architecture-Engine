package com.lego.voxel;

import java.util.Objects;

import com.lego.model.Vector3;

/**
 * Extracts the surface (shell) voxels from a solid voxel grid.
 */
public final class SurfaceExtractor {

    /** Non-instantiable utility class. */
    private SurfaceExtractor() {
        // Utility class, prevent instantiation
    }

    /**
     * Creates a hollow shell by keeping only voxels that have at least one
     * empty (or out-of-bounds) 6-direction neighbor.
     *
     * @param solidGrid input solid voxel grid (must be non-null)
     * @return new voxel grid containing only surface voxels
     */
    public static VoxelGrid extractSurface(VoxelGrid solidGrid) {
        Objects.requireNonNull(solidGrid, "VoxelGrid cannot be null");

        int width = solidGrid.width();
        int height = solidGrid.height();
        int depth = solidGrid.depth();

        VoxelGrid surface = new VoxelGrid(width, height, depth);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    if (!solidGrid.isFilled(x, y, z)) {
                        continue;
                    }

                    if (hasEmptyNeighbor(solidGrid, x, y, z)) {
                        surface.setFilled(x, y, z, true);
                    }
                }
            }
        }

        // Propagate normals from source grid to surface grid
        if (solidGrid.hasNormals()) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        if (surface.isFilled(x, y, z)) {
                            Vector3 n = solidGrid.getNormal(x, y, z);
                            if (n.length() > 1e-6) {
                                surface.accumulateNormal(x, y, z, n);
                            }
                        }
                    }
                }
            }
        }

        surface.normalizeNormals();
        return surface;
    }

    /** Returns true if any of the six face-adjacent neighbors is empty or out of bounds. */
    private static boolean hasEmptyNeighbor(VoxelGrid grid, int x, int y, int z) {
        return !grid.isFilledPosX(x, y, z) ||
               !grid.isFilledNegX(x, y, z) ||
               !grid.isFilledPosY(x, y, z) ||
               !grid.isFilledNegY(x, y, z) ||
               !grid.isFilledPosZ(x, y, z) ||
               !grid.isFilledNegZ(x, y, z);
    }
}
