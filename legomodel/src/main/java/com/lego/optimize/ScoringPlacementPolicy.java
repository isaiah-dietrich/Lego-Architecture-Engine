package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Quality-first scoring placement policy.
 *
 * <p>For each candidate brick dimension at a position, computes a score
 * that maximizes surface quality through orientation-aware placement:</p>
 *
 * <ol>
 *   <li><strong>Accuracy</strong> (primary): ratio of filled, uncovered voxels
 *       to total voxels in the candidate footprint. Higher is better.
 *       Only candidates with perfect accuracy (1.0) are placed — this
 *       matches the current requirement that every voxel under a brick
 *       must be filled.</li>
 *   <li><strong>Color uniformity</strong> (secondary, when color data available):
 *       measures how visually consistent the voxel colors are under the candidate
 *       footprint using CIELAB ΔE. A brick covering a single color region scores
 *       1.0; one straddling a sharp color boundary (e.g. eye detail) scores
 *       lower, allowing a smaller single-color brick to win.</li>
 *   <li><strong>Area</strong> (tertiary): larger bricks win over smaller
 *       ones, reducing total piece count and seam count for a more
 *       cohesive surface.</li>
 *   <li><strong>Neighbor coverage</strong> (quaternary): fraction of the brick's
 *       border voxels (one step outside the footprint) that are also filled.
 *       Among same-area candidates (especially rotated orientations), the
 *       one with higher coverage is preferred — it fits more snugly in the
 *       surrounding surface.</li>
 * </ol>
 *
 * <p><strong>Orientation exploration:</strong> unlike {@link GreedyAreaPolicy},
 * this policy tries both orientations of each non-square dimension (e.g.
 * 2×4 and 4×2). This is the primary quality feature — it finds better-fitting
 * rotations that the fixed-orientation greedy policy misses. Coverage then
 * selects the rotation that meshes best with the surrounding surface.</p>
 *
 * <p><strong>Color awareness:</strong> when constructed with a {@link PlacementFeatureGrid}
 * (built via {@code ColorFeatureGridFactory}), the policy will prefer
 * bricks that cover visually uniform regions. In areas with intense color
 * variation (eyes, patterns), smaller bricks are chosen to preserve detail.
 * When no feature grid is provided, color uniformity defaults to 1.0 and the
 * policy behaves identically to its color-unaware mode.</p>
 */
public final class ScoringPlacementPolicy implements PlacementPolicy {

    private final PlacementFeatureGrid featureGrid;

    /** Creates a scoring policy without color awareness. */
    public ScoringPlacementPolicy() {
        this((PlacementFeatureGrid) null);
    }

    /**
     * Creates a scoring policy with optional precomputed feature data.
     *
     * @param featureGrid precomputed placement features (from
     *                     {@code ColorFeatureGridFactory.create}),
     *                     or null for color-unaware mode
     */
    public ScoringPlacementPolicy(PlacementFeatureGrid featureGrid) {
        this.featureGrid = featureGrid;
    }

    @Override
    public String name() {
        return "scoring";
    }

    @Override
    public Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                              int x, int y, int z, List<BrickSpec> allowedSpecs) {
        // In high color-variance regions, force the smallest available brick
        if (featureGrid != null && featureGrid.isHighVariance(x, y, z)) {
            BrickSpec smallest = allowedSpecs.get(allowedSpecs.size() - 1);
            return new Brick(x, y, z, smallest.studX(), smallest.studY(),
                             smallest.heightUnits(), smallest.partId());
        }

        int bestStudX = 0;
        int bestStudY = 0;
        int bestHeightUnits = 0;
        String bestPartId = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (BrickSpec spec : allowedSpecs) {
            // Try catalog orientation
            double score = scorePlacement(surface, covered, featureGrid, x, y, z,
                                          spec.studX(), spec.studY(), spec.heightUnits());
            if (score > bestScore) {
                bestScore = score;
                bestStudX = spec.studX();
                bestStudY = spec.studY();
                bestHeightUnits = spec.heightUnits();
                bestPartId = spec.partId();
            }

            // Try rotated orientation (skip square bricks — identical)
            if (spec.studX() != spec.studY()) {
                score = scorePlacement(surface, covered, featureGrid, x, y, z,
                                       spec.studY(), spec.studX(), spec.heightUnits());
                if (score > bestScore) {
                    bestScore = score;
                    bestStudX = spec.studY();
                    bestStudY = spec.studX();
                    bestHeightUnits = spec.heightUnits();
                    bestPartId = spec.partId();
                }
            }
        }

        if (bestScore == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException(
                "Cannot place any brick at (" + x + "," + y + "," + z + "). " +
                "Allowed dimensions must include 1x1 as fallback."
            );
        }

        return new Brick(x, y, z, bestStudX, bestStudY, bestHeightUnits, bestPartId);
    }

    /**
     * Computes a composite placement score.
     *
     * <p>Returns {@link Double#NEGATIVE_INFINITY} if the candidate cannot be
     * placed (any footprint voxel is out of bounds, empty, or already covered).</p>
     *
     * <p>Score = accuracy × 1B + colorUniformity × area × 1K + heightUnits × 150
     *        + neighborCoverage × 100</p>
     * <ul>
     *   <li>accuracy — must be 1.0 to be valid (gates all candidates)</li>
     *   <li>colorUniformity × area × 1K — quality-weighted area: a color-uniform
     *       brick scores its full area, while one spanning a color boundary gets
     *       penalized, potentially letting a smaller single-color brick win</li>
     *   <li>heightUnits × 150 — consolidation bonus: bricks (h=3) beat plates
     *       (h=1) in uniform-color areas (fewer pieces), but the 300-point gap
     *       is easily overridden by even modest color variation across Y layers</li>
     *   <li>neighborCoverage × 100 — among same-area candidates (e.g. rotations),
     *       selects the orientation with the best surrounding fit</li>
     * </ul>
     */
    private static double scorePlacement(VoxelGrid surface, boolean[][][] covered,
                                          PlacementFeatureGrid features,
                                          int x, int y, int z,
                                          int studX, int studY, int heightUnits) {
        int area = studX * studY;

        // Check every voxel in the candidate footprint across all height layers
        for (int dy = 0; dy < heightUnits; dy++) {
            int cy = y + dy;
            if (cy >= surface.height()) return Double.NEGATIVE_INFINITY;
            for (int dx = 0; dx < studX; dx++) {
                for (int dz = 0; dz < studY; dz++) {
                    int cx = x + dx;
                    int cz = z + dz;
                    if (cx >= surface.width() || cz >= surface.depth()) {
                        return Double.NEGATIVE_INFINITY;
                    }
                    if (!surface.isFilled(cx, cy, cz) || covered[cx][cy][cz]) {
                        return Double.NEGATIVE_INFINITY;
                    }
                }
            }
        }

        // Color uniformity across entire volume (XZ footprint × all Y layers)
        double colorUniformity = features != null
            ? features.computeRegionUniformity(x, y, z, studX, studY, heightUnits)
            : 1.0;
        double neighborCoverage = computeNeighborCoverage(surface, x, y, z, studX, studY);

        // Consolidation bonus: bricks (h=3) get +450, plates (h=1) get +150.
        // The 300-point gap lets bricks win in uniform areas (fewer pieces).
        // For a 2×4 brick, a ~4% uniformity drop wipes out the bonus;
        // for a 1×1, the high-variance map handles detail forcing.
        return 1_000_000_000 + colorUniformity * area * 1_000
             + heightUnits * 150 + neighborCoverage * 100;
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
            return 1.0;  // No border = perfect fit (brick fills entire region)
        }
        return (double) filledNeighbors / neighborCount;
    }
}
