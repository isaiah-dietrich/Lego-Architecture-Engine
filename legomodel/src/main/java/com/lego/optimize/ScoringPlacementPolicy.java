package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.Dimension;
import com.lego.voxel.VoxelGrid;

/**
 * Accuracy-first scoring placement policy.
 *
 * <p>For each candidate brick dimension at a position, computes a score
 * combining surface accuracy and piece count efficiency:</p>
 *
 * <ol>
 *   <li><strong>Accuracy</strong> (primary): ratio of filled, uncovered voxels
 *       to total voxels in the candidate footprint. Higher is better.
 *       Only candidates with perfect accuracy (1.0) are placed — this
 *       matches the current requirement that every voxel under a brick
 *       must be filled.</li>
 *   <li><strong>Area</strong> (secondary): larger bricks win over smaller
 *       ones, reducing total piece count.</li>
 *   <li><strong>Neighbor coverage</strong> (tertiary): fraction of the brick's
 *       border voxels (one step outside the footprint) that are also filled.
 *       Higher values indicate the brick is interior to a surface region
 *       rather than at a jagged edge. This breaks ties between same-size
 *       candidates, preferring placements that leave clean rectangular
 *       remainders for subsequent bricks.</li>
 * </ol>
 *
 * <p><strong>Orientation exploration:</strong> unlike {@link GreedyAreaPolicy},
 * this policy tries both orientations of each non-square dimension (e.g.
 * 2×4 and 4×2). This expands the search space and finds placements that the
 * fixed-orientation greedy policy misses, typically producing fewer total
 * bricks on irregular surfaces.</p>
 */
public final class ScoringPlacementPolicy implements PlacementPolicy {

    @Override
    public String name() {
        return "scoring";
    }

    @Override
    public Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                              int x, int y, int z, List<Dimension> allowedDimensions) {
        int bestStudX = 0;
        int bestStudY = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Dimension dim : allowedDimensions) {
            // Try catalog orientation
            double score = scorePlacement(surface, covered, x, y, z,
                                          dim.studX(), dim.studY());
            if (score > bestScore) {
                bestScore = score;
                bestStudX = dim.studX();
                bestStudY = dim.studY();
            }

            // Try rotated orientation (skip square bricks — identical)
            if (dim.studX() != dim.studY()) {
                score = scorePlacement(surface, covered, x, y, z,
                                       dim.studY(), dim.studX());
                if (score > bestScore) {
                    bestScore = score;
                    bestStudX = dim.studY();
                    bestStudY = dim.studX();
                }
            }
        }

        if (bestScore == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException(
                "Cannot place any brick at (" + x + "," + y + "," + z + "). " +
                "Allowed dimensions must include 1x1 as fallback."
            );
        }

        return new Brick(x, y, z, bestStudX, bestStudY, 1);
    }

    /**
     * Computes a composite placement score.
     *
     * <p>Returns {@link Double#NEGATIVE_INFINITY} if the candidate cannot be
     * placed (any footprint voxel is out of bounds, empty, or already covered).</p>
     *
     * <p>Score components (in descending importance):</p>
     * <ul>
     *   <li>accuracy × 1_000_000_000 — must be 1.0 to be valid</li>
     *   <li>area × 1_000 — prefers larger bricks (fewer pieces)</li>
     *   <li>neighborCoverage — tie-break for same-size candidates</li>
     * </ul>
     */
    private static double scorePlacement(VoxelGrid surface, boolean[][][] covered,
                                          int x, int y, int z,
                                          int studX, int studY) {
        int area = studX * studY;
        int filledCount = 0;

        // Check every voxel in the candidate footprint
        for (int dx = 0; dx < studX; dx++) {
            for (int dz = 0; dz < studY; dz++) {
                int cx = x + dx;
                int cz = z + dz;
                if (cx >= surface.width() || cz >= surface.depth()) {
                    return Double.NEGATIVE_INFINITY;
                }
                if (!surface.isFilled(cx, y, cz) || covered[cx][y][cz]) {
                    return Double.NEGATIVE_INFINITY;
                }
                filledCount++;
            }
        }

        double accuracy = (double) filledCount / area;

        // Neighbor coverage: how many border-adjacent voxels are filled?
        double neighborCoverage = computeNeighborCoverage(surface, x, y, z, studX, studY);

        return accuracy * 1_000_000_000 + area * 1_000 + neighborCoverage;
    }

    /**
     * Computes the fraction of border-adjacent voxels that are filled.
     *
     * <p>Border voxels are one step outside the candidate footprint in the
     * X-Z plane (same Y layer). A high ratio means the brick is surrounded
     * by more surface, indicating an interior placement.</p>
     */
    private static double computeNeighborCoverage(VoxelGrid surface,
                                                    int x, int y, int z,
                                                    int studX, int studY) {
        int neighborCount = 0;
        int filledNeighbors = 0;

        // Left edge (x - 1)
        if (x > 0) {
            for (int dz = 0; dz < studY; dz++) {
                int cz = z + dz;
                if (cz < surface.depth()) {
                    neighborCount++;
                    if (surface.isFilled(x - 1, y, cz)) {
                        filledNeighbors++;
                    }
                }
            }
        }

        // Right edge (x + studX)
        if (x + studX < surface.width()) {
            for (int dz = 0; dz < studY; dz++) {
                int cz = z + dz;
                if (cz < surface.depth()) {
                    neighborCount++;
                    if (surface.isFilled(x + studX, y, cz)) {
                        filledNeighbors++;
                    }
                }
            }
        }

        // Front edge (z - 1)
        if (z > 0) {
            for (int dx = 0; dx < studX; dx++) {
                int cx = x + dx;
                if (cx < surface.width()) {
                    neighborCount++;
                    if (surface.isFilled(cx, y, z - 1)) {
                        filledNeighbors++;
                    }
                }
            }
        }

        // Back edge (z + studY)
        if (z + studY < surface.depth()) {
            for (int dx = 0; dx < studX; dx++) {
                int cx = x + dx;
                if (cx < surface.width()) {
                    neighborCount++;
                    if (surface.isFilled(cx, y, z + studY)) {
                        filledNeighbors++;
                    }
                }
            }
        }

        if (neighborCount == 0) {
            return 0.0;
        }
        return (double) filledNeighbors / neighborCount;
    }
}
