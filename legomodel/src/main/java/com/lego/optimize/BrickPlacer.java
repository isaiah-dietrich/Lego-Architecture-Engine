package com.lego.optimize;

import java.util.ArrayList;
import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.Dimension;
import com.lego.voxel.VoxelGrid;

/**
 * Deterministic greedy brick placer.
 *
 * Converts a surface voxel grid into a list of LEGO bricks using catalog-driven dimensions.
 * 
 * Algorithm:
 * - Process layer-by-layer (z ascending)
 * - Within each layer, scan y ascending, then x ascending
 * - At each unfilled position, try to place the largest brick that fits
 * - Priority: determined by catalog (area desc, width desc, depth desc)
 * 
 * Dimensions are loaded from the curated catalog and filtered to:
 * - Active parts only (active=true)
 * - Full-height bricks only (heightUnitsRaw == "1")
 * - Standard "Bricks" category only (excludes slopes, plates, special parts)
 * - Allowed orientations only (1x2 vertical forbidden)
 */
public final class BrickPlacer {

    private BrickPlacer() {
        // Utility class, prevent instantiation
    }

    /**
     * Generates a list of bricks from a surface voxel grid.
     * Loads allowed dimensions from the curated catalog.
     *
     * @param surface the surface voxel grid
     * @return list of bricks covering all filled voxels, deterministic order
     * @throws IllegalArgumentException if surface is null
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid dimensions
     */
    public static List<Brick> placeBricks(VoxelGrid surface) {
        return placeBricks(surface, AllowedBrickDimensions.loadFromCatalog());
    }

    /**
     * Generates a list of bricks from a surface voxel grid using provided dimensions.
     * Test-friendly overload for dependency injection.
     * 
     * Uses consistent scan direction (left-to-right, front-to-back) for more predictable
     * and symmetric results than multi-orientation optimization.
     *
     * @param surface the surface voxel grid
     * @param allowedDimensions allowed brick dimensions in priority order (largest first)
     * @return list of bricks covering all filled voxels, deterministic order
     * @throws IllegalArgumentException if surface is null or allowedDimensions is null/empty
     */
    public static List<Brick> placeBricks(VoxelGrid surface, List<Dimension> allowedDimensions) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (allowedDimensions == null || allowedDimensions.isEmpty()) {
            throw new IllegalArgumentException("allowedDimensions must not be null or empty");
        }

        // Use single consistent orientation for predictability
        return placeBricksWithOrientation(surface, allowedDimensions, false, false);
    }

    /**
     * Generates bricks with a specific scan orientation.
     *
     * @param surface the surface voxel grid
     * @param allowedDimensions allowed brick dimensions
     * @param reverseX whether to scan x in descending order
     * @param reverseY whether to scan y in descending order
     * @return list of bricks
     */
    private static List<Brick> placeBricksWithOrientation(VoxelGrid surface, List<Dimension> allowedDimensions,
                                                           boolean reverseX, boolean reverseY) {
        List<Brick> bricks = new ArrayList<>();
        boolean[][][] covered = new boolean[surface.width()][surface.height()][surface.depth()];

        // Process layer by layer (z always ascending, bottom to top)
        for (int z = 0; z < surface.depth(); z++) {
            // Scan y in specified direction
            int yStart = reverseY ? surface.height() - 1 : 0;
            int yEnd = reverseY ? -1 : surface.height();
            int yStep = reverseY ? -1 : 1;

            for (int y = yStart; y != yEnd; y += yStep) {
                // Scan x in specified direction
                int xStart = reverseX ? surface.width() - 1 : 0;
                int xEnd = reverseX ? -1 : surface.width();
                int xStep = reverseX ? -1 : 1;

                for (int x = xStart; x != xEnd; x += xStep) {
                    if (surface.isFilled(x, y, z) && !covered[x][y][z]) {
                        Brick brick = placeBrickAt(surface, covered, x, y, z, allowedDimensions);
                        
                        // Enforce: never allow 1x2 vertical orientation
                        if (brick.studX() == 1 && brick.studY() == 2) {
                            throw new IllegalStateException(
                                "Invalid brick placement at (" + x + "," + y + "," + z + "): " +
                                "1x2 vertical bricks are not allowed"
                            );
                        }

                        bricks.add(brick);
                        markCovered(covered, brick);
                    }
                }
            }
        }

        return bricks;
    }

    /**
     * Places the largest brick that fits at the given position.
     * Tries dimensions in priority order (largest area first).
     */
    private static Brick placeBrickAt(VoxelGrid surface, boolean[][][] covered, 
                                       int x, int y, int z, List<Dimension> allowedDimensions) {
        // Try each dimension in priority order
        for (Dimension dim : allowedDimensions) {
            if (canPlaceBrick(surface, covered, x, y, z, dim.studX(), dim.studY())) {
                return new Brick(x, y, z, dim.studX(), dim.studY(), 1);
            }
        }

        // Should never reach here if allowedDimensions includes 1x1
        throw new IllegalStateException(
            "Cannot place any brick at (" + x + "," + y + "," + z + "). " +
            "Allowed dimensions must include 1x1 as fallback."
        );
    }

    /**
     * Checks if a brick of the given size can be placed at the position.
     *
     * A brick can be placed if:
     * - All required voxels are within bounds
     * - All required voxels are filled in the surface
     * - None of the required voxels are already covered
     */
    private static boolean canPlaceBrick(VoxelGrid surface, boolean[][][] covered,
                                          int x, int y, int z, int studX, int studY) {
        for (int dx = 0; dx < studX; dx++) {
            for (int dy = 0; dy < studY; dy++) {
                int cx = x + dx;
                int cy = y + dy;

                // Check bounds
                if (cx >= surface.width() || cy >= surface.height()) {
                    return false;
                }

                // Check if surface is filled and not yet covered
                if (!surface.isFilled(cx, cy, z) || covered[cx][cy][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Marks all voxels occupied by a brick as covered.
     */
    private static void markCovered(boolean[][][] covered, Brick brick) {
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    covered[x][y][z] = true;
                }
            }
        }
    }
}
