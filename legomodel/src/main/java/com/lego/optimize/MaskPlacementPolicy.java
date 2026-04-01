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
import com.lego.optimize.PartMask.VoxelOffset;
import com.lego.voxel.VoxelGrid;

/**
 * Geometry-mask-backed global placement policy with deterministic lexicographic
 * objective ordering:
 * 1) Feasibility via hard constraints (occupancy, blocking, target shell)
 * 2) Fewer pieces (max new coverage per placement)
 * 3) Lower color error (when feature grid is available)
 * 4) More solid voxels, then deterministic spatial tiebreakers
 */
public final class MaskPlacementPolicy implements BatchPlacementPolicy, PlacementStatsProvider {

    private static final double MIN_SLOPE_INCLINATION_DEG = 20.0;

    private final PlacementFeatureGrid featureGrid;
    private final PartMaskProvider maskProvider;
    private int peakCandidateCount;

    public MaskPlacementPolicy() {
        this(null, new ProceduralPartMaskProvider());
    }

    public MaskPlacementPolicy(PlacementFeatureGrid featureGrid) {
        this(featureGrid, new ProceduralPartMaskProvider());
    }

    public MaskPlacementPolicy(PlacementFeatureGrid featureGrid, PartMaskProvider maskProvider) {
        this.featureGrid = featureGrid;
        this.maskProvider = maskProvider != null ? maskProvider : new ProceduralPartMaskProvider();
    }

    @Override
    public String name() {
        return "mask";
    }

    @Override
    public Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                             int x, int y, int z, List<BrickSpec> allowedSpecs) {
        throw new UnsupportedOperationException("mask policy requires batch placement via placeAll()");
    }

    @Override
    public List<Brick> placeAll(VoxelGrid surface, List<BrickSpec> allowedSpecs) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (allowedSpecs == null || allowedSpecs.isEmpty()) {
            throw new IllegalArgumentException("allowedSpecs must not be null/empty");
        }

        this.peakCandidateCount = 0;
        PlacementTargetGrid target = PlacementTargetGrid.fromSurface(surface, allowedSpecs, featureGrid);
        boolean[][][] occupied = new boolean[target.width()][target.height()][target.depth()];
        boolean[][][] blocked = new boolean[target.width()][target.height()][target.depth()];
        boolean[][][] covered = new boolean[target.width()][target.height()][target.depth()];
        Map<MaskKey, PartMask> resolvedMasks = new HashMap<>();

        int uncovered = target.requiredCount();
        List<Brick> selected = new ArrayList<>();

        while (uncovered > 0) {
            Candidate best = null;

            for (int y = 0; y < target.height(); y++) {
                for (int z = 0; z < target.depth(); z++) {
                    for (int x = 0; x < target.width(); x++) {
                        if (!target.isRequired(x, y, z) || covered[x][y][z]) {
                            continue;
                        }
                        List<Candidate> candidates = generateCandidatesAtAnchor(
                            target, occupied, blocked, covered, x, y, z, allowedSpecs, resolvedMasks
                        );
                        if (candidates.size() > peakCandidateCount) {
                            peakCandidateCount = candidates.size();
                        }
                        for (Candidate c : candidates) {
                            if (best == null || better(c, best)) {
                                best = c;
                            }
                        }
                    }
                }
            }

            if (best == null) {
                throw new IllegalStateException(
                    "mask policy could not find a feasible candidate while uncovered voxels remain"
                );
            }

            selected.add(best.brick());
            for (Cell cell : best.solidCells()) {
                occupied[cell.x()][cell.y()][cell.z()] = true;
                blocked[cell.x()][cell.y()][cell.z()] = true;
            }
            for (Cell cell : best.blockedCells()) {
                blocked[cell.x()][cell.y()][cell.z()] = true;
            }
            // For slopes, block the entire bounding box so no flat brick can be
            // placed within the slope's height envelope from any direction.
            if (best.brick().facing() != Facing.NONE) {
                Brick s = best.brick();
                for (int by = s.y(); by < s.maxY(); by++) {
                    for (int bx = s.x(); bx < s.maxX(); bx++) {
                        for (int bz = s.z(); bz < s.maxZ(); bz++) {
                            if (inBounds(target, bx, by, bz)) {
                                blocked[bx][by][bz] = true;
                            }
                        }
                    }
                }
            }
            for (Cell cell : best.coverageCells()) {
                if (!covered[cell.x()][cell.y()][cell.z()]) {
                    covered[cell.x()][cell.y()][cell.z()] = true;
                    uncovered--;
                }
            }
        }

        selected.sort((a, b) -> {
            if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
            if (a.z() != b.z()) return Integer.compare(a.z(), b.z());
            return Integer.compare(a.x(), b.x());
        });
        return selected;
    }

    @Override
    public int peakCandidateCount() {
        return peakCandidateCount;
    }

    private List<Candidate> generateCandidatesAtAnchor(PlacementTargetGrid target,
                                                        boolean[][][] occupied,
                                                        boolean[][][] blocked,
                                                        boolean[][][] covered,
                                                        int x, int y, int z,
                                                        List<BrickSpec> specs,
                                                        Map<MaskKey, PartMask> resolvedMasks) {
        List<Candidate> out = new ArrayList<>();
        Vector3 normal = target.normalAt(x, y, z);

        for (BrickSpec spec : specs) {
            if (spec.isSlope()) {
                SurfaceMatcher.MatchResult match = SurfaceMatcher.match(normal, spec);
                if (!match.eligible()) {
                    continue;
                }
                if (!target.isSlopeEligible(x, y, z) || !passesSlopeInclination(normal)) {
                    continue;
                }
                Facing facing = match.facing();
                int[] dims = orientedSlopeDims(spec, facing);
                Candidate candidate = buildCandidate(
                    target, occupied, blocked, covered, x, y, z, dims[0], dims[1], spec, facing, resolvedMasks
                );
                if (candidate != null) {
                    out.add(candidate);
                }
                continue;
            }

            Candidate identity = buildCandidate(
                target, occupied, blocked, covered, x, y, z, spec.studX(), spec.studY(), spec, Facing.NONE, resolvedMasks
            );
            if (identity != null) {
                out.add(identity);
            }

            if (spec.studX() != spec.studY()) {
                Candidate rotated = buildCandidate(
                    target, occupied, blocked, covered, x, y, z, spec.studY(), spec.studX(), spec, Facing.NONE, resolvedMasks
                );
                if (rotated != null) {
                    out.add(rotated);
                }
            }
        }
        return out;
    }

    private Candidate buildCandidate(PlacementTargetGrid target,
                                     boolean[][][] occupied,
                                     boolean[][][] blocked,
                                     boolean[][][] covered,
                                     int x, int y, int z,
                                     int studX, int studY,
                                     BrickSpec spec,
                                     Facing facing,
                                     Map<MaskKey, PartMask> resolvedMasks) {
        MaskKey maskKey = new MaskKey(spec.partId(), spec.heightUnits(), facing, studX, studY);
        PartMask mask = resolvedMasks.computeIfAbsent(maskKey, ignored ->
            maskProvider.getMask(spec, facing, studX, studY)
        );
        List<Cell> solidCells = new ArrayList<>(mask.solidOccupancyMask().size());
        List<Cell> coverageCells = new ArrayList<>(mask.topCoverageMask().size());
        List<Cell> blockedCells = new ArrayList<>();

        for (VoxelOffset offset : mask.solidOccupancyMask()) {
            int cx = x + offset.dx();
            int cy = y + offset.dy();
            int cz = z + offset.dz();
            if (!inBounds(target, cx, cy, cz)) return null;
            if (occupied[cx][cy][cz]) return null;
            if (blocked[cx][cy][cz]) return null;
            // Hard constraint: no occupancy outside required target shell.
            if (!target.isRequired(cx, cy, cz)) return null;
            solidCells.add(new Cell(cx, cy, cz));
        }

        // Slopes: reject if any cell in the full bounding box is already occupied or blocked.
        // The wedge-shaped solid mask omits the "air pocket" at the slope toe (top-front corner),
        // so previously placed flat bricks there would not be detected by the solid mask check.
        // This extra sweep prevents visual collisions caused by bricks inside the height envelope.
        if (facing != Facing.NONE) {
            for (int dy = 0; dy < spec.heightUnits(); dy++) {
                for (int dx = 0; dx < studX; dx++) {
                    for (int dz = 0; dz < studY; dz++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        int cz = z + dz;
                        if (inBounds(target, cx, cy, cz)
                                && (occupied[cx][cy][cz] || blocked[cx][cy][cz])) {
                            return null;
                        }
                    }
                }
            }
        }

        int newCoverage = 0;
        for (VoxelOffset offset : mask.topCoverageMask()) {
            int cx = x + offset.dx();
            int cy = y + offset.dy();
            int cz = z + offset.dz();
            if (!inBounds(target, cx, cy, cz)) return null;
            if (!target.isRequired(cx, cy, cz)) return null; // no outside-target coverage
            if (blocked[cx][cy][cz]) return null;
            coverageCells.add(new Cell(cx, cy, cz));
        }

        if (facing != Facing.NONE) {
            List<Cell> shadowCells = computeSlopeShadowCells(target, x, y, z, studX, studY, spec.heightUnits(), facing);
            for (Cell shadow : shadowCells) {
                if (!target.isRequired(shadow.x(), shadow.y(), shadow.z())) {
                    continue; // non-surface cells can't hold bricks — no need to block
                }
                // Only cover shadow cells whose normal matches this slope's angle and facing.
                // Flat or mismatched cells (e.g. paw base connecting to leg) are left unblocked
                // so flat bricks can cover them; resolveSlopeAdjacentConflicts handles aesthetics.
                Vector3 shadowNormal = target.normalAt(shadow.x(), shadow.y(), shadow.z());
                SurfaceMatcher.MatchResult shadowMatch = SurfaceMatcher.match(shadowNormal, spec);
                if (shadowMatch.eligible() && shadowMatch.facing() == facing) {
                    if (blocked[shadow.x()][shadow.y()][shadow.z()]) {
                        return null;
                    }
                    blockedCells.add(shadow);
                    coverageCells.add(shadow);
                }
            }
        }

        coverageCells = dedupe(coverageCells);
        blockedCells = dedupe(blockedCells);
        for (Cell cell : coverageCells) {
            if (!covered[cell.x()][cell.y()][cell.z()]) {
                newCoverage++;
            }
        }

        if (newCoverage <= 0) {
            return null;
        }

        Brick brick = new Brick(x, y, z, studX, studY, spec.heightUnits(), spec.partId(), facing);
        int colorHeight = spec.isSlope() ? 1 : spec.heightUnits();
        double colorError = target.colorErrorForRegion(x, y, z, studX, studY, colorHeight);
        return new Candidate(brick, solidCells, blockedCells, coverageCells, newCoverage, colorError);
    }

    private static boolean better(Candidate a, Candidate b) {
        // Lexicographic proxy:
        // 1) max new coverage (fewer pieces)
        // 2) min color error
        // 3) max solid voxels
        // 4) deterministic tiebreakers
        if (a.newCoverage() != b.newCoverage()) {
            return a.newCoverage() > b.newCoverage();
        }
        int colorCmp = Double.compare(a.colorError(), b.colorError());
        if (colorCmp != 0) {
            return colorCmp < 0;
        }
        if (a.solidCells().size() != b.solidCells().size()) {
            return a.solidCells().size() > b.solidCells().size();
        }
        Brick ab = a.brick();
        Brick bb = b.brick();
        if (ab.y() != bb.y()) return ab.y() < bb.y();
        if (ab.z() != bb.z()) return ab.z() < bb.z();
        if (ab.x() != bb.x()) return ab.x() < bb.x();
        int idCmp = ab.partId().compareTo(bb.partId());
        if (idCmp != 0) return idCmp < 0;
        if (ab.studX() != bb.studX()) return ab.studX() > bb.studX();
        return ab.studY() > bb.studY();
    }

    private static boolean inBounds(PlacementTargetGrid target, int x, int y, int z) {
        return x >= 0 && x < target.width()
            && y >= 0 && y < target.height()
            && z >= 0 && z < target.depth();
    }

    private static boolean passesSlopeInclination(Vector3 normal) {
        if (normal == null || normal.length() < 1e-6) {
            return false;
        }
        double cosAngle = Math.abs(normal.y());
        double inclination = Math.toDegrees(Math.acos(Math.min(1.0, cosAngle)));
        return inclination >= MIN_SLOPE_INCLINATION_DEG;
    }

    private static int[] orientedSlopeDims(BrickSpec spec, Facing facing) {
        if (facing == Facing.NORTH || facing == Facing.SOUTH) {
            return new int[] { spec.studY(), spec.studX() };
        }
        return new int[] { spec.studX(), spec.studY() };
    }

    private static List<Cell> computeSlopeShadowCells(PlacementTargetGrid target,
                                                      int x, int y, int z,
                                                      int studX, int studY,
                                                      int heightUnits,
                                                      Facing facing) {
        List<Cell> cells = new ArrayList<>();
        for (int k = 0; k < heightUnits; k++) {
            int shadowY = y + k;
            if (shadowY >= target.height()) break;
            for (int d = 1; d <= k + 1; d++) {
                switch (facing) {
                    case NORTH -> {
                        int sz = z - d;
                        if (sz < 0) continue;
                        for (int cx = x; cx < x + studX; cx++) {
                            if (cx >= 0 && cx < target.width()) cells.add(new Cell(cx, shadowY, sz));
                        }
                    }
                    case SOUTH -> {
                        int sz = z + studY - 1 + d;
                        if (sz >= target.depth()) continue;
                        for (int cx = x; cx < x + studX; cx++) {
                            if (cx >= 0 && cx < target.width()) cells.add(new Cell(cx, shadowY, sz));
                        }
                    }
                    case EAST -> {
                        int sx = x + studX - 1 + d;
                        if (sx >= target.width()) continue;
                        for (int cz = z; cz < z + studY; cz++) {
                            if (cz >= 0 && cz < target.depth()) cells.add(new Cell(sx, shadowY, cz));
                        }
                    }
                    case WEST -> {
                        int sx = x - d;
                        if (sx < 0) continue;
                        for (int cz = z; cz < z + studY; cz++) {
                            if (cz >= 0 && cz < target.depth()) cells.add(new Cell(sx, shadowY, cz));
                        }
                    }
                    default -> { }
                }
            }
        }
        return dedupe(cells);
    }

    private static List<Cell> dedupe(List<Cell> cells) {
        List<Cell> out = new ArrayList<>(cells.size());
        Set<String> seen = new HashSet<>();
        for (Cell cell : cells) {
            String key = cell.x() + "," + cell.y() + "," + cell.z();
            if (seen.add(key)) {
                out.add(cell);
            }
        }
        return out;
    }

    private record Cell(int x, int y, int z) { }

    private record MaskKey(String partId, int heightUnits, Facing facing, int studX, int studY) { }

    private record Candidate(
        Brick brick,
        List<Cell> solidCells,
        List<Cell> blockedCells,
        List<Cell> coverageCells,
        int newCoverage,
        double colorError
    ) { }
}
