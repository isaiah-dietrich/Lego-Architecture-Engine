package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Quality-first scoring placement policy.
 *
 * For each candidate brick dimension at a position, computes a score
 * that maximizes surface quality through orientation-aware placement:
 *
 * 
 *   - Accuracy (primary): ratio of filled, uncovered voxels
 *       to total voxels in the candidate footprint. Higher is better.
 *       Only candidates with perfect accuracy (1.0) are placed — this
 *       matches the current requirement that every voxel under a brick
 *       must be filled.
 *   - Color uniformity (secondary, when color data available):
 *       measures how visually consistent the voxel colors are under the candidate
 *       footprint using CIELAB ΔE. A brick covering a single color region scores
 *       1.0; one straddling a sharp color boundary (e.g. eye detail) scores
 *       lower, allowing a smaller single-color brick to win.
 *   - Area (tertiary): larger bricks win over smaller
 *       ones, reducing total piece count and seam count for a more
 *       cohesive surface.
 *   - Neighbor coverage (quaternary): fraction of the brick's
 *       border voxels (one step outside the footprint) that are also filled.
 *       Among same-area candidates (especially rotated orientations), the
 *       one with higher coverage is preferred — it fits more snugly in the
 *       surrounding surface.
 * 
 *
 * Orientation exploration: unlike GreedyAreaPolicy,
 * this policy tries both orientations of each non-square dimension (e.g.
 * 2×4 and 4×2). This is the primary quality feature — it finds better-fitting
 * rotations that the fixed-orientation greedy policy misses. Coverage then
 * selects the rotation that meshes best with the surrounding surface.
 *
 * Color awareness: when constructed with a PlacementFeatureGrid
 * (built via ColorFeatureGridFactory), the policy will prefer
 * bricks that cover visually uniform regions. In areas with intense color
 * variation (eyes, patterns), smaller bricks are chosen to preserve detail.
 * When no feature grid is provided, color uniformity defaults to 1.0 and the
 * policy behaves identically to its color-unaware mode.
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
     *                     ColorFeatureGridFactory.create),
     *                     or null for color-unaware mode
     */
    public ScoringPlacementPolicy(PlacementFeatureGrid featureGrid) {
        this.featureGrid = featureGrid;
    }

    @Override
    /** Returns the policy name ("scoring"). */
    public String name() {
        return "scoring";
    }

    @Override
    /** Selects the highest-scoring brick at the given position, trying both orientations. */
    public Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                              int x, int y, int z, List<BrickSpec> allowedSpecs) {
        // Read surface normal for slope/curve matching
        Vector3 normal = surface.getNormal(x, y, z);

        // In high color-variance regions, force the smallest footprint that fits.
        // Prefer taller pieces for that footprint (e.g. 1x1x3 over 1x1x1)
        // so we avoid unnecessary stacks of three plates.
        if (featureGrid != null && isHighVarianceAnchor(featureGrid, x, y, z)) {
            Brick fallback = selectSmallestFootprintTallestThatFits(
                surface, covered, normal, x, y, z, allowedSpecs);
            if (fallback != null) {
                return fallback;
            }
        }

        int bestStudX = 0;
        int bestStudY = 0;
        int bestHeightUnits = 0;
        String bestPartId = null;
        Facing bestFacing = Facing.NONE;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (BrickSpec spec : allowedSpecs) {
            // Check surface match eligibility
            SurfaceMatcher.MatchResult matchResult = SurfaceMatcher.match(normal, spec);
            if (!matchResult.eligible()) continue;

            // Slopes occupy a rectangular base but extend upward as a wedge;
            // only the base layer needs filled voxels for placement validation.
            int scoreHeight = spec.isSlope() ? 1 : spec.heightUnits();

            if (spec.isSlope()) {
                // For slopes, the voxel footprint is determined by the facing direction
                // to match the post-rotation LDraw part dimensions.
                // LDraw default: catalog stud_y studs along X, stud_x studs along Z.
                // NORTH/SOUTH (identity/Y180): dimensions unchanged from LDraw default.
                // EAST/WEST (Y270/Y90): dimensions swap (X↔Z).
                Facing facing = matchResult.facing();
                int sStudX, sStudY;
                if (facing == Facing.NORTH || facing == Facing.SOUTH) {
                    sStudX = spec.studY();
                    sStudY = spec.studX();
                } else {
                    sStudX = spec.studX();
                    sStudY = spec.studY();
                }

                double score = scorePlacement(surface, covered, featureGrid, x, y, z,
                                              sStudX, sStudY, scoreHeight);
                score += 10_000;

                if (score > bestScore) {
                    bestScore = score;
                    bestStudX = sStudX;
                    bestStudY = sStudY;
                    bestHeightUnits = spec.heightUnits();
                    bestPartId = spec.partId();
                    bestFacing = facing;
                }
            } else {
                // Standard bricks: try both orientations
                double score = scorePlacement(surface, covered, featureGrid, x, y, z,
                                              spec.studX(), spec.studY(), scoreHeight);

                if (score > bestScore) {
                    bestScore = score;
                    bestStudX = spec.studX();
                    bestStudY = spec.studY();
                    bestHeightUnits = spec.heightUnits();
                    bestPartId = spec.partId();
                    bestFacing = matchResult.facing();
                }

                // Try rotated orientation (skip square bricks — identical)
                if (spec.studX() != spec.studY()) {
                    score = scorePlacement(surface, covered, featureGrid, x, y, z,
                                           spec.studY(), spec.studX(), scoreHeight);
                    if (score > bestScore) {
                        bestScore = score;
                        bestStudX = spec.studY();
                        bestStudY = spec.studX();
                        bestHeightUnits = spec.heightUnits();
                        bestPartId = spec.partId();
                        bestFacing = matchResult.facing();
                    }
                }
            }
        }

        if (bestScore == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException(
                "Cannot place any brick at (" + x + "," + y + "," + z + "). " +
                "Allowed dimensions must include 1x1 as fallback."
            );
        }

        return new Brick(x, y, z, bestStudX, bestStudY, bestHeightUnits, bestPartId, bestFacing);
    }

    /**
     * Computes a composite placement score.
     *
     * Returns Double#NEGATIVE_INFINITY if the candidate cannot be
     * placed (any footprint voxel is out of bounds, empty, or already covered).
     *
     * Score = accuracy × 1B + colorUniformity × area × 1K + heightUnits × 150
     *        + neighborCoverage × 100
     * 
     *   - accuracy — must be 1.0 to be valid (gates all candidates)
     *   - colorUniformity × area × 1K — quality-weighted area: a color-uniform
     *       brick scores its full area, while one spanning a color boundary gets
     *       penalized, potentially letting a smaller single-color brick win
     *   - heightUnits × 150 — consolidation bonus: bricks (h=3) beat plates
     *       (h=1) in uniform-color areas (fewer pieces), but the 300-point gap
     *       is easily overridden by even modest color variation across Y layers
     *   - neighborCoverage × 100 — among same-area candidates (e.g. rotations),
     *       selects the orientation with the best surrounding fit
     * 
     */
    private static double scorePlacement(VoxelGrid surface, boolean[][][] covered,
                                          PlacementFeatureGrid features,
                                          int x, int y, int z,
                                          int studX, int studY, int heightUnits) {
        int area = studX * studY;

        if (!canPlace(surface, covered, x, y, z, studX, studY, heightUnits)) {
            return Double.NEGATIVE_INFINITY;
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
        return 1_000_000_000 + colorUniformity * colorUniformity * area * 1_000
             + heightUnits * 150 + neighborCoverage * 100;
    }

    /** Returns true when the candidate volume is fully in-bounds, filled, and uncovered. */
    private static boolean canPlace(VoxelGrid surface, boolean[][][] covered,
                                     int x, int y, int z,
                                     int studX, int studY, int heightUnits) {
        for (int dy = 0; dy < heightUnits; dy++) {
            int cy = y + dy;
            if (cy >= surface.height()) return false;
            for (int dx = 0; dx < studX; dx++) {
                for (int dz = 0; dz < studY; dz++) {
                    int cx = x + dx;
                    int cz = z + dz;
                    if (cx >= surface.width() || cz >= surface.depth()) {
                        return false;
                    }
                    if (!surface.isFilled(cx, cy, cz) || covered[cx][cy][cz]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Computes the fraction of border-adjacent voxels that are filled.
     *
     * Border voxels are one step outside the candidate footprint in the
     * X-Z plane (same Y layer). A high ratio means the brick is surrounded
     * by more surface, indicating an interior placement.
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

    /**
     * High-variance gate for detail preservation.
     *
     * Uses only the anchor voxel, avoiding over-aggressive forcing caused by
     * scanning large surrounding regions.
     */
    private static boolean isHighVarianceAnchor(PlacementFeatureGrid featureGrid,
                                                 int x, int y, int z) {
        return featureGrid.isHighVariance(x, y, z);
    }

    /**
     * Selects the smallest footprint that fits at the anchor, preferring taller
     * pieces within that footprint. Used as the detail-preserving fallback in
     * high-variance regions.
     */
    private static Brick selectSmallestFootprintTallestThatFits(VoxelGrid surface,
                                                                  boolean[][][] covered,
                                                                  Vector3 normal,
                                                                  int x, int y, int z,
                                                                  List<BrickSpec> allowedSpecs) {
        int bestArea = Integer.MAX_VALUE;
        int bestHeight = -1;
        int bestStudX = 0;
        int bestStudY = 0;
        String bestPartId = null;

        for (BrickSpec spec : allowedSpecs) {
            if (spec.isSlope()) {
                continue;
            }
            if (!SurfaceMatcher.match(normal, spec).eligible()) {
                continue;
            }

            if (canPlace(surface, covered, x, y, z, spec.studX(), spec.studY(), spec.heightUnits())) {
                int area = spec.studX() * spec.studY();
                if (area < bestArea || (area == bestArea && spec.heightUnits() > bestHeight)) {
                    bestArea = area;
                    bestHeight = spec.heightUnits();
                    bestStudX = spec.studX();
                    bestStudY = spec.studY();
                    bestPartId = spec.partId();
                }
            }

            if (spec.studX() != spec.studY()
                    && canPlace(surface, covered, x, y, z, spec.studY(), spec.studX(), spec.heightUnits())) {
                int area = spec.studX() * spec.studY();
                if (area < bestArea || (area == bestArea && spec.heightUnits() > bestHeight)) {
                    bestArea = area;
                    bestHeight = spec.heightUnits();
                    bestStudX = spec.studY();
                    bestStudY = spec.studX();
                    bestPartId = spec.partId();
                }
            }
        }

        if (bestPartId == null) {
            return null;
        }
        return new Brick(x, y, z, bestStudX, bestStudY, bestHeight, bestPartId);
    }
}
