package com.lego.optimize;

import java.util.ArrayList;
import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Deterministic brick placer with pluggable placement policies.
 *
 * <p>Converts a surface voxel grid into a list of LEGO bricks using catalog-driven
 * dimensions. The placement policy controls which brick dimension is selected at
 * each position.</p>
 *
 * <p>Scan order (deterministic):</p>
 * <ul>
 *   <li>Layer-by-layer: y ascending (VoxelGrid Y = OBJ height axis)</li>
 *   <li>Within each layer: z ascending (depth), then x ascending (width)</li>
 * </ul>
 *
 * <p>Available policies:</p>
 * <ul>
 *   <li>{@link ScoringPlacementPolicy} (default) — accuracy-first with neighbor
 *       coverage scoring and area as tie-breaker</li>
 *   <li>{@link GreedyAreaPolicy} — legacy largest-area-first greedy selection</li>
 * </ul>
 *
 * Brick dimensions occupy the horizontal X-Z plane (one voxel tall in Y):
 * <ul>
 *   <li>studX spans VoxelGrid X (width)</li>
 *   <li>studY spans VoxelGrid Z (depth)</li>
 * </ul>
 */
public final class BrickPlacer {

    private static final PlacementPolicy DEFAULT_POLICY = new ScoringPlacementPolicy();

    private BrickPlacer() {
        // Utility class, prevent instantiation
    }

    /**
     * Generates a list of bricks from a surface voxel grid.
     * Loads allowed dimensions from the curated catalog.
     * Uses the default {@link ScoringPlacementPolicy}.
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
     * Generates a list of bricks from a surface voxel grid using provided brick specs.
     * Uses the default {@link ScoringPlacementPolicy}.
     *
     * @param surface the surface voxel grid
     * @param allowedSpecs allowed brick specs in priority order (largest first)
     * @return list of bricks covering all filled voxels, deterministic order
     * @throws IllegalArgumentException if surface is null or allowedSpecs is null/empty
     */
    public static List<Brick> placeBricks(VoxelGrid surface, List<BrickSpec> allowedSpecs) {
        return placeBricks(surface, allowedSpecs, DEFAULT_POLICY);
    }

    /**
     * Generates a list of bricks from a surface voxel grid using a specific placement policy.
     *
     * @param surface the surface voxel grid
     * @param allowedSpecs allowed brick specs in priority order (largest first)
     * @param policy placement policy for brick selection
     * @return list of bricks covering all filled voxels, deterministic order
     * @throws IllegalArgumentException if any argument is null, or allowedSpecs is empty
     */
    public static List<Brick> placeBricks(VoxelGrid surface, List<BrickSpec> allowedSpecs,
                                           PlacementPolicy policy) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (allowedSpecs == null || allowedSpecs.isEmpty()) {
            throw new IllegalArgumentException("allowedDimensions must not be null or empty");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }

        return placeBricksInternal(surface, allowedSpecs, policy);
    }

    /**
     * Core placement loop. Scans layer-by-layer, delegating brick selection to the policy.
     */
    private static List<Brick> placeBricksInternal(VoxelGrid surface, List<BrickSpec> allowedSpecs,
                                                    PlacementPolicy policy) {
        List<Brick> bricks = new ArrayList<>();
        boolean[][][] covered = new boolean[surface.width()][surface.height()][surface.depth()];

        // Process layer by layer (y ascending: VoxelGrid Y = OBJ height axis)
        for (int y = 0; y < surface.height(); y++) {
            for (int z = 0; z < surface.depth(); z++) {
                for (int x = 0; x < surface.width(); x++) {
                    if (surface.isFilled(x, y, z) && !covered[x][y][z]) {
                        Brick brick = policy.selectBrick(surface, covered, x, y, z, allowedSpecs);
                        bricks.add(brick);
                        markCovered(covered, brick);
                    }
                }
            }
        }

        return bricks;
    }

    /**
     * Marks all voxels occupied by a brick as covered.
     */
    static void markCovered(boolean[][][] covered, Brick brick) {
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    covered[x][y][z] = true;
                }
            }
        }
    }
}
