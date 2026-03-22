package com.lego.optimize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Deterministic brick placer with pluggable placement policies.
 *
 * Converts a surface voxel grid into a list of LEGO bricks using catalog-driven
 * dimensions. The placement policy controls which brick dimension is selected at
 * each position.
 *
 * Scan order (deterministic):
 * 
 *   - Layer-by-layer: y ascending (VoxelGrid Y = OBJ height axis)
 *   - Within each layer: z ascending (depth), then x ascending (width)
 * 
 *
 * Available policies:
 * 
 *   - ScoringPlacementPolicy (default) — accuracy-first with neighbor
 *       coverage scoring and area as tie-breaker
 *   - GreedyAreaPolicy — legacy largest-area-first greedy selection
 * 
 *
 * Brick dimensions occupy the horizontal X-Z plane (one voxel tall in Y):
 * 
 *   - studX spans VoxelGrid X (width)
 *   - studY spans VoxelGrid Z (depth)
 * 
 */
public final class BrickPlacer {

    private static final PlacementPolicy DEFAULT_POLICY = new ScoringPlacementPolicy();

    /** Non-instantiable utility class. */
    private BrickPlacer() {
        // Utility class, prevent instantiation
    }

    /**
     * Generates a list of bricks from a surface voxel grid.
     * Loads allowed dimensions from the curated catalog.
     * Uses the default ScoringPlacementPolicy.
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
     * Uses the default ScoringPlacementPolicy.
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
     *
     * Before placement begins, pre-marks voxels adjacent to slope-eligible
     * positions in the slope-facing direction. This prevents wide flat bricks
     * from spanning into the slope's visual zone when they are scanned before
     * the slope in z/x order.
     *
     * After the main scan, resolves visual conflicts where tall bricks placed at
     * lower Y layers extend into the height range of slopes placed at higher layers.
     */
    private static List<Brick> placeBricksInternal(VoxelGrid surface, List<BrickSpec> allowedSpecs,
                                                    PlacementPolicy policy) {
        List<Brick> bricks = new ArrayList<>();
        boolean[][][] covered = new boolean[surface.width()][surface.height()][surface.depth()];

        if (containsSlopeSpecs(allowedSpecs)) {
            preMarkSlopeAdjacentZones(surface, covered, allowedSpecs);
        }

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

        boolean enableAdjacentConsolidation =
            (policy instanceof ScoringPlacementPolicy scoringPolicy)
                && scoringPolicy.allowAdjacentConsolidation();

        List<Brick> consolidated = consolidatePlateStacks(bricks, allowedSpecs);
        if (enableAdjacentConsolidation) {
            consolidated = consolidateAdjacentBricks(consolidated, allowedSpecs);
        }
        consolidated = resolveSlopeAdjacentConflicts(consolidated, allowedSpecs);
        if (enableAdjacentConsolidation) {
            consolidated = consolidateAdjacentBricks(consolidated, allowedSpecs);
        }
        return consolidated;
    }

    private static boolean containsSlopeSpecs(List<BrickSpec> allowedSpecs) {
        for (BrickSpec spec : allowedSpecs) {
            if (spec.isSlope()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingSlopeSpec(Vector3 normal, List<BrickSpec> allowedSpecs) {
        if (normal == null || normal.length() < 1e-6) {
            return false;
        }
        for (BrickSpec spec : allowedSpecs) {
            if (!spec.isSlope()) {
                continue;
            }
            if (SurfaceMatcher.match(normal, spec).eligible()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingSlopeSpecWithFacing(Vector3 normal, List<BrickSpec> allowedSpecs,
                                                           Facing facing) {
        if (normal == null || normal.length() < 1e-6) {
            return false;
        }
        for (BrickSpec spec : allowedSpecs) {
            if (!spec.isSlope()) {
                continue;
            }
            SurfaceMatcher.MatchResult match = SurfaceMatcher.match(normal, spec);
            if (match.eligible() && match.facing() == facing) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pre-marks voxels adjacent to slope-eligible surface positions as covered.
     *
     * For each filled voxel with a surface normal indicating a slope (inclination
     * greater than 20 degrees from vertical), marks the immediately adjacent voxel
     * in the slope-facing direction as covered. This prevents wide flat bricks from
     * starting at a lower z/x position and spanning into the slope's visual zone
     * before the slope is placed during the main scan.
     *
     * Only marks the adjacent voxel if it is NOT itself a slope-eligible position
     * (to avoid suppressing slope-to-slope stacking).
     */
    private static void preMarkSlopeAdjacentZones(VoxelGrid surface, boolean[][][] covered,
                                                   List<BrickSpec> allowedSpecs) {
        final double MIN_SLOPE_ANGLE = 20.0;

        int width = surface.width();
        int height = surface.height();
        int depth = surface.depth();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    if (!surface.isFilled(x, y, z)) continue;

                    Vector3 normal = surface.getNormal(x, y, z);
                    if (normal == null || normal.length() < 1e-6) continue;

                    double cosAngle = Math.abs(normal.y());
                    double inclination = Math.toDegrees(Math.acos(Math.min(1.0, cosAngle)));
                    if (inclination < MIN_SLOPE_ANGLE) continue;
                    if (!hasMatchingSlopeSpec(normal, allowedSpecs)) continue;

                    Facing facing = SurfaceMatcher.resolveCardinalFacing(normal);

                    int ax = x, az = z;
                    switch (facing) {
                        case NORTH -> az = z - 1;
                        case SOUTH -> az = z + 1;
                        case EAST  -> ax = x + 1;
                        case WEST  -> ax = x - 1;
                        default -> { continue; }
                    }

                    if (ax < 0 || ax >= width || az < 0 || az >= depth) continue;
                    if (!surface.isFilled(ax, y, az)) continue;

                    // Don't suppress if the adjacent voxel can host a slope with the same facing.
                    Vector3 adjNormal = surface.getNormal(ax, y, az);
                    if (hasMatchingSlopeSpecWithFacing(adjNormal, allowedSpecs, facing)) continue;

                    covered[ax][y][az] = true;
                }
            }
        }
    }

    /**
     * Marks all voxels occupied by a brick as covered.
     *
     * Both standard and directional bricks (slopes, curves) mark their full
     * heightUnits volume — slope parts are solid 3D shapes in LDraw and
     * must block placement of other bricks within their bounding box.
     *
     * For slope bricks, also marks a triangular "shadow zone" in the
     * slope-facing direction. This suppresses flat bricks on adjacent
     * staircase steps that would be visible through the slope's angled face,
     * eliminating the visual artifact of bricks appearing inside slopes.
     */
    static void markCovered(boolean[][][] covered, Brick brick) {
        int maxX = Math.min(brick.maxX(), covered.length);
        int maxY = Math.min(brick.maxY(), covered[0].length);
        int maxZ = Math.min(brick.maxZ(), covered[0][0].length);
        for (int x = brick.x(); x < maxX; x++) {
            for (int y = brick.y(); y < maxY; y++) {
                for (int z = brick.z(); z < maxZ; z++) {
                    covered[x][y][z] = true;
                }
            }
        }

        if (brick.facing() != Facing.NONE) {
            markSlopeShadow(covered, brick);
        }
    }

    /**
     * Marks a triangular shadow zone in the slope-facing direction.
     *
     * On a voxelized staircase surface, a slope replaces the angular step
     * pattern. Without the shadow zone, flat bricks placed on adjacent
     * staircase steps (in front of the slope face) share the slope's
     * LDraw height range and appear to be inside the slope geometry.
     *
     * Starting from the base layer (k = 0), marks (k + 1) additional
     * voxels in the slope-facing direction across the full width of the
     * brick. The k = 0 layer marks 1 voxel in the facing direction at
     * the base height, preventing flat bricks from being placed
     * immediately in front of the slope face where their geometry would
     * visually conflict with the slope's angled surface.
     *
     * The +1 offset per layer accounts for the stud-to-plate aspect
     * ratio: each plate height (8 LDU) spans less than one stud (20 LDU),
     * so the visible shadow extends roughly one stud beyond the geometric
     * retraction at each layer.
     */
    private static void markSlopeShadow(boolean[][][] covered, Brick brick) {
        int width = covered.length;
        int height = covered[0].length;
        int depth = covered[0][0].length;

        for (int k = 0; k < brick.heightUnits(); k++) {
            int shadowY = brick.y() + k;
            if (shadowY >= height) break;

            for (int d = 1; d <= k + 1; d++) {
                switch (brick.facing()) {
                    case NORTH -> {
                        int sz = brick.z() - d;
                        if (sz < 0) continue;
                        for (int x = brick.x(); x < Math.min(brick.maxX(), width); x++) {
                            covered[x][shadowY][sz] = true;
                        }
                    }
                    case SOUTH -> {
                        int sz = brick.maxZ() - 1 + d;
                        if (sz >= depth) continue;
                        for (int x = brick.x(); x < Math.min(brick.maxX(), width); x++) {
                            covered[x][shadowY][sz] = true;
                        }
                    }
                    case EAST -> {
                        int sx = brick.maxX() - 1 + d;
                        if (sx >= width) continue;
                        for (int z = brick.z(); z < Math.min(brick.maxZ(), depth); z++) {
                            covered[sx][shadowY][z] = true;
                        }
                    }
                    case WEST -> {
                        int sx = brick.x() - d;
                        if (sx < 0) continue;
                        for (int z = brick.z(); z < Math.min(brick.maxZ(), depth); z++) {
                            covered[sx][shadowY][z] = true;
                        }
                    }
                    default -> { /* NONE — handled by caller guard */ }
                }
            }
        }
    }

    /**
     * Resolves visual conflicts where tall non-slope bricks placed at lower Y
     * layers extend up into the height range of slopes at adjacent positions.
     *
     * Because placement scans Y-ascending, a 3-height brick at y=N is placed
     * before any slope at y=N+1. The slope's shadow zone cannot retroactively
     * suppress the already-placed brick. This post-processing step detects
     * these conflicts and replaces the tall brick with a single-plate-height
     * version using the corresponding plate part ID from the catalog.
     *
     * A conflict exists when a non-slope brick:
     *   - Is adjacent to a slope in the slope's facing direction (sharing a face)
     *   - Has its Y range overlapping with the slope's Y range
     *   - Has heightUnits greater than 1
     */
    static List<Brick> resolveSlopeAdjacentConflicts(List<Brick> bricks,
                                                      List<BrickSpec> allowedSpecs) {
        // Build plate lookup: (studX, studY) → platePartId for h=1 specs
        Map<String, String> plateLookup = new HashMap<>();
        for (BrickSpec spec : allowedSpecs) {
            if (spec.heightUnits() == 1 && !spec.isSlope()) {
                String key = spec.studX() + "x" + spec.studY();
                plateLookup.putIfAbsent(key, spec.partId());
                // Also map the rotated key
                if (spec.studX() != spec.studY()) {
                    String rotKey = spec.studY() + "x" + spec.studX();
                    plateLookup.putIfAbsent(rotKey, spec.partId());
                }
            }
        }

        // Collect slopes
        List<Brick> slopes = new ArrayList<>();
        for (Brick b : bricks) {
            if (b.facing() != Facing.NONE) {
                slopes.add(b);
            }
        }
        if (slopes.isEmpty()) {
            return bricks;
        }

        // Index slopes by their facing-direction edge positions for fast lookup
        // Key: "x,y,z" of voxel positions adjacent to the slope in the facing direction
        Set<String> slopeInfluence = new HashSet<>();
        for (Brick slope : slopes) {
            addSlopeInfluenceZone(slopeInfluence, slope);
        }

        // Find conflicting non-slope bricks
        Set<Integer> toShorten = new HashSet<>();
        for (int i = 0; i < bricks.size(); i++) {
            Brick b = bricks.get(i);
            if (b.facing() != Facing.NONE) continue;
            if (b.heightUnits() <= 1) continue;

            // Check if any voxel in this brick's upper layers (y+1 .. y+h-1)
            // is in a slope's influence zone
            if (isInSlopeInfluence(slopeInfluence, b)) {
                toShorten.add(i);
            }
        }

        if (toShorten.isEmpty()) {
            return bricks;
        }

        // Replace conflicting bricks with plate-height versions
        List<Brick> result = new ArrayList<>(bricks.size());
        for (int i = 0; i < bricks.size(); i++) {
            Brick b = bricks.get(i);
            if (toShorten.contains(i)) {
                String plateId = plateLookup.get(b.studX() + "x" + b.studY());
                if (plateId != null) {
                    result.add(new Brick(b.x(), b.y(), b.z(),
                                         b.studX(), b.studY(), 1, plateId));
                } else {
                    // No plate equivalent found — keep the original brick
                    result.add(b);
                }
            } else {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Merges three aligned plate-height bricks into a single full-height brick
     * when a matching (studX, studY, height=3) catalog spec exists.
     *
     * This reduces visible "triple plate stack" artifacts while preserving
     * footprint and deterministic scan ordering.
     */
    static List<Brick> consolidatePlateStacks(List<Brick> bricks, List<BrickSpec> allowedSpecs) {
        Map<String, String> fullBrickLookup = new HashMap<>();
        for (BrickSpec spec : allowedSpecs) {
            if (spec.heightUnits() == 3 && !spec.isSlope()) {
                String key = spec.studX() + "x" + spec.studY();
                fullBrickLookup.putIfAbsent(key, spec.partId());
                if (spec.studX() != spec.studY()) {
                    String rotKey = spec.studY() + "x" + spec.studX();
                    fullBrickLookup.putIfAbsent(rotKey, spec.partId());
                }
            }
        }

        if (fullBrickLookup.isEmpty()) {
            return bricks;
        }

        Map<String, Integer> plateIndexByPos = new HashMap<>();
        for (int i = 0; i < bricks.size(); i++) {
            Brick b = bricks.get(i);
            if (b.facing() == Facing.NONE && b.heightUnits() == 1) {
                plateIndexByPos.put(stackKey(b.x(), b.y(), b.z(), b.studX(), b.studY()), i);
            }
        }

        boolean[] consumed = new boolean[bricks.size()];
        List<Brick> result = new ArrayList<>(bricks.size());

        for (int i = 0; i < bricks.size(); i++) {
            if (consumed[i]) {
                continue;
            }

            Brick b = bricks.get(i);
            if (b.facing() != Facing.NONE || b.heightUnits() != 1) {
                consumed[i] = true;
                result.add(b);
                continue;
            }

            String fullBrickPartId = fullBrickLookup.get(b.studX() + "x" + b.studY());
            if (fullBrickPartId == null) {
                consumed[i] = true;
                result.add(b);
                continue;
            }

            Integer midIdx = plateIndexByPos.get(stackKey(b.x(), b.y() + 1, b.z(), b.studX(), b.studY()));
            Integer topIdx = plateIndexByPos.get(stackKey(b.x(), b.y() + 2, b.z(), b.studX(), b.studY()));

            if (midIdx != null && topIdx != null && !consumed[midIdx] && !consumed[topIdx]) {
                consumed[i] = true;
                consumed[midIdx] = true;
                consumed[topIdx] = true;
                result.add(new Brick(b.x(), b.y(), b.z(), b.studX(), b.studY(), 3, fullBrickPartId));
            } else {
                consumed[i] = true;
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Iteratively merges adjacent standard bricks into larger catalog-supported
     * bricks with the same y/height, maximizing consolidation by footprint area.
     *
     * This enables upgrades like:
     * - 1x1 + 1x1 -> 1x2
     * - 1x2 + 1x1 -> 1x3
     * - 2x2 + 2x2 -> 2x4
     */
    static List<Brick> consolidateAdjacentBricks(List<Brick> bricks, List<BrickSpec> allowedSpecs) {
        Map<String, String> partLookup = new HashMap<>();
        for (BrickSpec spec : allowedSpecs) {
            if (spec.isSlope()) continue;
            String key = mergeKey(spec.studX(), spec.studY(), spec.heightUnits());
            partLookup.putIfAbsent(key, spec.partId());
            if (spec.studX() != spec.studY()) {
                String rotKey = mergeKey(spec.studY(), spec.studX(), spec.heightUnits());
                partLookup.putIfAbsent(rotKey, spec.partId());
            }
        }
        if (partLookup.isEmpty() || bricks.size() < 2) {
            return bricks;
        }

        List<Brick> working = new ArrayList<>(bricks);
        boolean changed;
        do {
            changed = false;

            for (int i = 0; i < working.size(); i++) {
                Brick a = working.get(i);
                if (a.facing() != Facing.NONE) continue;

                int bestJ = -1;
                Brick bestMerged = null;
                int bestArea = -1;

                for (int j = i + 1; j < working.size(); j++) {
                    Brick b = working.get(j);
                    Brick merged = tryMerge(a, b, partLookup);
                    if (merged == null) continue;

                    int area = merged.studX() * merged.studY();
                    if (area > bestArea || (area == bestArea && isEarlier(merged, bestMerged))) {
                        bestArea = area;
                        bestJ = j;
                        bestMerged = merged;
                    }
                }

                if (bestJ >= 0) {
                    working.set(i, bestMerged);
                    working.remove(bestJ);
                    changed = true;
                    i--; // Reconsider merged brick for further growth.
                }
            }
        } while (changed);

        working.sort((a, b) -> {
            if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
            if (a.z() != b.z()) return Integer.compare(a.z(), b.z());
            return Integer.compare(a.x(), b.x());
        });
        return working;
    }

    private static Brick tryMerge(Brick a, Brick b, Map<String, String> partLookup) {
        if (a.facing() != Facing.NONE || b.facing() != Facing.NONE) return null;
        if (a.y() != b.y() || a.heightUnits() != b.heightUnits()) return null;

        boolean adjacentAlongX = a.z() == b.z()
            && a.studY() == b.studY()
            && (a.maxX() == b.x() || b.maxX() == a.x());
        boolean adjacentAlongZ = a.x() == b.x()
            && a.studX() == b.studX()
            && (a.maxZ() == b.z() || b.maxZ() == a.z());

        if (!adjacentAlongX && !adjacentAlongZ) {
            return null;
        }

        int x = Math.min(a.x(), b.x());
        int z = Math.min(a.z(), b.z());
        int studX = Math.max(a.maxX(), b.maxX()) - x;
        int studY = Math.max(a.maxZ(), b.maxZ()) - z;
        int h = a.heightUnits();

        // Must represent exactly the area of the two source bricks.
        int unionArea = studX * studY;
        int sourceArea = a.studX() * a.studY() + b.studX() * b.studY();
        if (unionArea != sourceArea) {
            return null;
        }

        String partId = partLookup.get(mergeKey(studX, studY, h));
        if (partId == null) {
            return null;
        }
        return new Brick(x, a.y(), z, studX, studY, h, partId);
    }

    private static boolean isEarlier(Brick a, Brick b) {
        if (b == null) return true;
        if (a.y() != b.y()) return a.y() < b.y();
        if (a.z() != b.z()) return a.z() < b.z();
        return a.x() < b.x();
    }

    private static String mergeKey(int studX, int studY, int h) {
        return studX + "x" + studY + "x" + h;
    }

    private static String stackKey(int x, int y, int z, int studX, int studY) {
        return x + "," + y + "," + z + "," + studX + "," + studY;
    }

    /**
     * Adds the influence zone positions for a slope: all voxel positions
     * adjacent to the slope in its facing direction, across the slope's
     * full height range.
     */
    private static void addSlopeInfluenceZone(Set<String> zone, Brick slope) {
        for (int y = slope.y(); y < slope.maxY(); y++) {
            switch (slope.facing()) {
                case NORTH -> {
                    int z = slope.z() - 1;
                    if (z >= 0) {
                        for (int x = slope.x(); x < slope.maxX(); x++) {
                            zone.add(x + "," + y + "," + z);
                        }
                    }
                }
                case SOUTH -> {
                    int z = slope.maxZ();
                    for (int x = slope.x(); x < slope.maxX(); x++) {
                        zone.add(x + "," + y + "," + z);
                    }
                }
                case EAST -> {
                    int x = slope.maxX();
                    for (int z = slope.z(); z < slope.maxZ(); z++) {
                        zone.add(x + "," + y + "," + z);
                    }
                }
                case WEST -> {
                    int x = slope.x() - 1;
                    if (x >= 0) {
                        for (int z = slope.z(); z < slope.maxZ(); z++) {
                            zone.add(x + "," + y + "," + z);
                        }
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Returns true if any voxel in the brick's UPPER layers (y+1 and above)
     * falls within a slope's influence zone — meaning the brick's geometry
     * at those layers visually conflicts with an adjacent slope.
     */
    private static boolean isInSlopeInfluence(Set<String> slopeInfluence, Brick brick) {
        for (int dy = 1; dy < brick.heightUnits(); dy++) {
            int y = brick.y() + dy;
            for (int dx = 0; dx < brick.studX(); dx++) {
                for (int dz = 0; dz < brick.studY(); dz++) {
                    if (slopeInfluence.contains((brick.x() + dx) + "," + y + "," + (brick.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
