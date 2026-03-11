package com.lego.optimize;

import java.util.List;

import com.lego.color.LegoPaletteMapper;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.optimize.AllowedBrickDimensions.Dimension;
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
 * <p><strong>Color awareness:</strong> when constructed with a voxel color grid
 * (from {@code ColorSampler.sampleVoxelColorGrid}), the policy will prefer
 * bricks that cover visually uniform regions. In areas with intense color
 * variation (eyes, patterns), smaller bricks are chosen to preserve detail.
 * When no color grid is provided, color uniformity defaults to 1.0 and the
 * policy behaves identically to its color-unaware mode.</p>
 */
public final class ScoringPlacementPolicy implements PlacementPolicy {

    /** ΔE threshold: above this, colors are considered perceptually different. */
    private static final double COLOR_DIFF_THRESHOLD = 25.0;

    private final ColorRgb[][][] voxelColors;

    /** Creates a scoring policy without color awareness. */
    public ScoringPlacementPolicy() {
        this(null);
    }

    /**
     * Creates a scoring policy with optional color-aware placement.
     *
     * @param voxelColors per-voxel color grid from {@code ColorSampler.sampleVoxelColorGrid},
     *                    or null for color-unaware mode
     */
    public ScoringPlacementPolicy(ColorRgb[][][] voxelColors) {
        this.voxelColors = voxelColors;
    }

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
            double score = scorePlacement(surface, covered, voxelColors, x, y, z,
                                          dim.studX(), dim.studY());
            if (score > bestScore) {
                bestScore = score;
                bestStudX = dim.studX();
                bestStudY = dim.studY();
            }

            // Try rotated orientation (skip square bricks — identical)
            if (dim.studX() != dim.studY()) {
                score = scorePlacement(surface, covered, voxelColors, x, y, z,
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
     * <p>Score = accuracy × 1B + colorUniformity × area × 1K + neighborCoverage × 100</p>
     * <ul>
     *   <li>accuracy — must be 1.0 to be valid (gates all candidates)</li>
     *   <li>colorUniformity × area × 1K — quality-weighted area: a color-uniform
     *       brick scores its full area, while one spanning a color boundary gets
     *       penalized, potentially letting a smaller single-color brick win</li>
     *   <li>neighborCoverage × 100 — among same-area candidates (e.g. rotations),
     *       selects the orientation with the best surrounding fit</li>
     * </ul>
     */
    private static double scorePlacement(VoxelGrid surface, boolean[][][] covered,
                                          ColorRgb[][][] colors,
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

        // Color uniformity: how visually consistent are the voxels under this brick?
        double colorUniformity = computeColorUniformity(colors, x, y, z, studX, studY);

        // Neighbor coverage: how many border-adjacent voxels are filled?
        double neighborCoverage = computeNeighborCoverage(surface, x, y, z, studX, studY);

        return accuracy * 1_000_000_000 + colorUniformity * area * 1_000 + neighborCoverage * 100;
    }

    /**
     * Computes color uniformity across the candidate footprint.
     *
     * <p>Converts each voxel's color to CIELAB and finds the maximum pairwise
     * ΔE76 distance. Maps this to [0.0, 1.0] via a threshold: ΔE=0 → 1.0
     * (perfectly uniform), ΔE≥threshold → 0.0 (maximally varied).</p>
     *
     * @return 1.0 if colors are null, area is 1, or all voxels are the same color
     */
    private static double computeColorUniformity(ColorRgb[][][] colors,
                                                   int x, int y, int z,
                                                   int studX, int studY) {
        if (colors == null) {
            return 1.0;
        }

        int area = studX * studY;
        if (area <= 1) {
            return 1.0;
        }

        // Collect Lab values for voxels in the footprint
        double[][] labs = new double[area][];
        int count = 0;
        for (int dx = 0; dx < studX; dx++) {
            for (int dz = 0; dz < studY; dz++) {
                int cx = x + dx;
                int cz = z + dz;
                if (cx < colors.length && y < colors[0].length && cz < colors[0][0].length) {
                    ColorRgb c = colors[cx][y][cz];
                    if (c != null) {
                        labs[count++] = LegoPaletteMapper.linearRgbToLab(c.r(), c.g(), c.b());
                    }
                }
            }
        }

        if (count <= 1) {
            return 1.0;
        }

        // Find maximum pairwise ΔE
        double maxDeltaE = 0;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double de = LegoPaletteMapper.deltaE(
                    labs[i][0], labs[i][1], labs[i][2],
                    labs[j][0], labs[j][1], labs[j][2]);
                if (de > maxDeltaE) {
                    maxDeltaE = de;
                }
            }
        }

        // Linear mapping: 0 → 1.0, threshold → 0.0
        return Math.max(0.0, 1.0 - maxDeltaE / COLOR_DIFF_THRESHOLD);
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
