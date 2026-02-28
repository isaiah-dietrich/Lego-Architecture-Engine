package com.lego.optimize;

import java.util.ArrayList;
import java.util.List;

import com.lego.model.Brick;
import com.lego.voxel.VoxelGrid;

/**
 * Deterministic greedy brick placer.
 *
 * Converts a surface voxel grid into a list of LEGO bricks.
 * Currently supports:
 * - 1x2 bricks (both orientations)
 * - 1x1 bricks (fallback)
 *
 * Algorithm:
 * - Process layer-by-layer (z ascending)
 * - Within each layer, scan y ascending, then x ascending
 * - At each unfilled position, try to place the largest brick that fits
 * - Priority: 1x2 horizontal, 1x2 vertical, 1x1
 */
public final class BrickPlacer {

    private BrickPlacer() {
        // Utility class, prevent instantiation
    }

    /**
     * Generates a list of bricks from a surface voxel grid.
     *
     * @param surface the surface voxel grid
     * @return list of bricks covering all filled voxels, deterministic order
     * @throws IllegalArgumentException if surface is null
     */
    public static List<Brick> placeBricks(VoxelGrid surface) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }

        List<Brick> bricks = new ArrayList<>();
        boolean[][][] covered = new boolean[surface.width()][surface.height()][surface.depth()];

        // Process layer by layer (z ascending)
        for (int z = 0; z < surface.depth(); z++) {
            // Scan in deterministic order: y ascending, x ascending
            for (int y = 0; y < surface.height(); y++) {
                for (int x = 0; x < surface.width(); x++) {
                    if (surface.isFilled(x, y, z) && !covered[x][y][z]) {
                        Brick brick = placeBrickAt(surface, covered, x, y, z);
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
     *
     * Priority:
     * 1. 1x2 horizontal (along x axis)
     * 2. 1x2 vertical (along y axis)
     * 3. 1x1 fallback
     */
    private static Brick placeBrickAt(VoxelGrid surface, boolean[][][] covered, int x, int y, int z) {
        // Try 1x2 horizontal (along x axis)
        if (canPlaceBrick(surface, covered, x, y, z, 2, 1)) {
            return new Brick(x, y, z, 2, 1, 1);
        }

        // Try 1x2 vertical (along y axis)
        if (canPlaceBrick(surface, covered, x, y, z, 1, 2)) {
            return new Brick(x, y, z, 1, 2, 1);
        }

        // Fallback to 1x1
        return new Brick(x, y, z, 1, 1, 1);
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
