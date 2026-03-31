package com.lego.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.PartMask;
import com.lego.optimize.PartMaskProvider;
import com.lego.optimize.PlacementFeatureGrid;
import com.lego.optimize.ProceduralPartMaskProvider;
import com.lego.optimize.SurfaceMatcher;
import com.lego.voxel.VoxelGrid;

/**
 * Computes policy quality/efficiency metrics for A/B benchmark output.
 */
final class PlacementBenchmarkCalculator {

    private static final double MIN_SLOPE_INCLINATION_DEG = 20.0;

    private PlacementBenchmarkCalculator() {}

    static PlacementBenchmarkMetrics compute(
        String policyName,
        VoxelGrid surface,
        List<BrickSpec> allowedSpecs,
        List<Brick> bricks,
        PlacementFeatureGrid featureGrid,
        long runtimeMs,
        int peakCandidateCount
    ) {
        return compute(
            policyName,
            surface,
            allowedSpecs,
            bricks,
            featureGrid,
            runtimeMs,
            peakCandidateCount,
            new ProceduralPartMaskProvider(),
            MaskSource.LEGACY_PROCEDURAL.cliLabel()
        );
    }

    static PlacementBenchmarkMetrics compute(
        String policyName,
        VoxelGrid surface,
        List<BrickSpec> allowedSpecs,
        List<Brick> bricks,
        PlacementFeatureGrid featureGrid,
        long runtimeMs,
        int peakCandidateCount,
        PartMaskProvider maskProvider,
        String maskSource
    ) {
        if (maskProvider == null) {
            maskProvider = new ProceduralPartMaskProvider();
        }
        if (maskSource == null || maskSource.isBlank()) {
            maskSource = MaskSource.LEGACY_PROCEDURAL.cliLabel();
        }
        Map<String, BrickSpec> specByPartId = new HashMap<>();
        for (BrickSpec spec : allowedSpecs) {
            specByPartId.putIfAbsent(spec.partId(), spec);
        }

        int[][][] occupancyCount = new int[surface.width()][surface.height()][surface.depth()];
        int[][][] coverageCount = new int[surface.width()][surface.height()][surface.depth()];
        int outsideTargetCoverageCount = 0;
        int slopePlacementCount = 0;
        int flatSlopeErrorCount = 0;
        int slopeFacingChecks = 0;
        int slopeFacingMatches = 0;
        int overlapPlacementCount;
        int outsideCoveragePlacementCount = 0;
        int slopeAngleMismatchPlacementCount = 0;
        int slopeFacingMismatchPlacementCount = 0;
        int slopeMissingNormalPlacementCount = 0;
        int slopeShadowIntrusionPlacementCount;
        int slopeAdjacentTallFlatConflictPlacementCount;
        int shellLeakResidualVoxelCount;

        List<PlacementMask> placementMasks = new ArrayList<>(bricks.size());
        List<Brick> slopeBricks = new ArrayList<>();

        List<Double> deltaEs = new ArrayList<>();

        for (Brick brick : bricks) {
            BrickSpec spec = specByPartId.get(brick.partId());
            if (spec == null) {
                spec = new BrickSpec(brick.studX(), brick.studY(), brick.heightUnits(), "unknown", brick.partId());
            }
            PartMask mask = maskProvider.getMask(spec, brick.facing(), brick.studX(), brick.studY());
            placementMasks.add(new PlacementMask(brick, mask));

            boolean hasOutsideCoverage = false;
            for (PartMask.VoxelOffset offset : mask.solidOccupancyMask()) {
                int x = brick.x() + offset.dx();
                int y = brick.y() + offset.dy();
                int z = brick.z() + offset.dz();
                if (inBounds(surface, x, y, z)) {
                    occupancyCount[x][y][z]++;
                }
            }
            for (PartMask.VoxelOffset offset : mask.topCoverageMask()) {
                int x = brick.x() + offset.dx();
                int y = brick.y() + offset.dy();
                int z = brick.z() + offset.dz();
                if (!inBounds(surface, x, y, z)) {
                    outsideTargetCoverageCount++;
                    hasOutsideCoverage = true;
                    continue;
                }
                coverageCount[x][y][z]++;
                if (!surface.isFilled(x, y, z)) {
                    outsideTargetCoverageCount++;
                    hasOutsideCoverage = true;
                }
            }
            if (hasOutsideCoverage) {
                outsideCoveragePlacementCount++;
            }

            if (brick.facing() != Facing.NONE) {
                slopePlacementCount++;
                slopeBricks.add(brick);
                Vector3 normal = surface.getNormal(brick.x(), brick.y(), brick.z());
                if (normal != null && normal.length() > 1e-6) {
                    double cosAngle = Math.abs(normal.y());
                    double inclination = Math.toDegrees(Math.acos(Math.min(1.0, cosAngle)));
                    if (inclination < MIN_SLOPE_INCLINATION_DEG) {
                        flatSlopeErrorCount++;
                        slopeAngleMismatchPlacementCount++;
                    }
                    slopeFacingChecks++;
                    Facing resolvedFacing = SurfaceMatcher.resolveCardinalFacing(normal);
                    if (resolvedFacing == brick.facing()) {
                        slopeFacingMatches++;
                    } else {
                        slopeFacingMismatchPlacementCount++;
                    }
                } else {
                    slopeMissingNormalPlacementCount++;
                }
            }

            if (featureGrid != null) {
                int colorHeight = brick.facing() == Facing.NONE ? brick.heightUnits() : 1;
                double uniformity = featureGrid.computeRegionUniformity(
                    brick.x(), brick.y(), brick.z(), brick.studX(), brick.studY(), colorHeight
                );
                double deltaE = (1.0 - uniformity) * featureGrid.colorDiffThreshold();
                deltaEs.add(deltaE);
            }
        }

        int collisionCount = 0;
        int uncoveredRequiredCount = 0;
        int shellLeakCount = 0;
        for (int x = 0; x < surface.width(); x++) {
            for (int y = 0; y < surface.height(); y++) {
                for (int z = 0; z < surface.depth(); z++) {
                    if (occupancyCount[x][y][z] > 1) {
                        collisionCount += (occupancyCount[x][y][z] - 1);
                    }
                    if (surface.isFilled(x, y, z) && coverageCount[x][y][z] == 0) {
                        uncoveredRequiredCount++;
                        shellLeakCount++;
                    }
                }
            }
        }
        shellLeakResidualVoxelCount = shellLeakCount;

        overlapPlacementCount = "mask".equalsIgnoreCase(policyName)
            ? countMaskOverlappingPlacements(surface, placementMasks)
            : countOverlappingPlacements(bricks);
        slopeShadowIntrusionPlacementCount = countShadowIntrusionPlacements(surface, placementMasks, slopeBricks);
        slopeAdjacentTallFlatConflictPlacementCount = countTallFlatAdjacencyConflicts(surface, bricks, slopeBricks);

        double slopeFacingConsistency = slopeFacingChecks == 0
            ? 1.0
            : (double) slopeFacingMatches / slopeFacingChecks;

        double meanDeltaE = -1.0;
        double p95DeltaE = -1.0;
        if (!deltaEs.isEmpty()) {
            deltaEs.sort(Double::compareTo);
            double sum = 0.0;
            for (double d : deltaEs) {
                sum += d;
            }
            meanDeltaE = sum / deltaEs.size();
            int p95Index = (int) Math.ceil(deltaEs.size() * 0.95) - 1;
            p95Index = Math.max(0, Math.min(p95Index, deltaEs.size() - 1));
            p95DeltaE = deltaEs.get(p95Index);
        }

        return new PlacementBenchmarkMetrics(
            policyName,
            maskSource,
            collisionCount,
            uncoveredRequiredCount,
            outsideTargetCoverageCount,
            shellLeakCount,
            slopePlacementCount,
            flatSlopeErrorCount,
            slopeFacingConsistency,
            overlapPlacementCount,
            outsideCoveragePlacementCount,
            slopeAngleMismatchPlacementCount,
            slopeFacingMismatchPlacementCount,
            slopeMissingNormalPlacementCount,
            slopeShadowIntrusionPlacementCount,
            slopeAdjacentTallFlatConflictPlacementCount,
            shellLeakResidualVoxelCount,
            bricks.size(),
            runtimeMs,
            peakCandidateCount,
            meanDeltaE,
            p95DeltaE
        );
    }

    private static int countOverlappingPlacements(List<Brick> bricks) {
        boolean[] overlaps = new boolean[bricks.size()];
        for (int i = 0; i < bricks.size(); i++) {
            for (int j = i + 1; j < bricks.size(); j++) {
                if (bricks.get(i).overlaps(bricks.get(j))) {
                    overlaps[i] = true;
                    overlaps[j] = true;
                }
            }
        }

        int count = 0;
        for (boolean overlap : overlaps) {
            if (overlap) {
                count++;
            }
        }
        return count;
    }

    private static int countMaskOverlappingPlacements(VoxelGrid surface, List<PlacementMask> placementMasks) {
        int width = surface.width();
        int height = surface.height();
        int depth = surface.depth();

        int[][][] ownerByVoxel = new int[width][height][depth];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    ownerByVoxel[x][y][z] = -1;
                }
            }
        }

        boolean[] overlaps = new boolean[placementMasks.size()];
        for (int i = 0; i < placementMasks.size(); i++) {
            PlacementMask placementMask = placementMasks.get(i);
            Brick brick = placementMask.brick();
            PartMask mask = placementMask.mask();

            for (PartMask.VoxelOffset offset : mask.solidOccupancyMask()) {
                int x = brick.x() + offset.dx();
                int y = brick.y() + offset.dy();
                int z = brick.z() + offset.dz();
                if (!inBounds(surface, x, y, z)) {
                    continue;
                }

                int owner = ownerByVoxel[x][y][z];
                if (owner == -1) {
                    ownerByVoxel[x][y][z] = i;
                } else if (owner != i) {
                    overlaps[i] = true;
                    overlaps[owner] = true;
                }
            }
        }

        int count = 0;
        for (boolean overlap : overlaps) {
            if (overlap) {
                count++;
            }
        }
        return count;
    }

    private static int countShadowIntrusionPlacements(VoxelGrid surface,
                                                      List<PlacementMask> placementMasks,
                                                      List<Brick> slopeBricks) {
        boolean[][][] shadowCells = new boolean[surface.width()][surface.height()][surface.depth()];
        for (Brick slope : slopeBricks) {
            markSlopeShadowCells(surface, shadowCells, slope);
        }

        int count = 0;
        for (PlacementMask placementMask : placementMasks) {
            Brick brick = placementMask.brick();
            if (brick.facing() != Facing.NONE) {
                continue;
            }
            if (intersectsShadow(surface, placementMask, shadowCells)) {
                count++;
            }
        }
        return count;
    }

    private static int countTallFlatAdjacencyConflicts(VoxelGrid surface,
                                                       List<Brick> bricks,
                                                       List<Brick> slopeBricks) {
        boolean[][][] slopeInfluence = new boolean[surface.width()][surface.height()][surface.depth()];
        for (Brick slope : slopeBricks) {
            markSlopeInfluenceCells(surface, slopeInfluence, slope);
        }

        int count = 0;
        for (Brick brick : bricks) {
            if (brick.facing() != Facing.NONE || brick.heightUnits() <= 1) {
                continue;
            }
            if (isInSlopeInfluence(surface, slopeInfluence, brick)) {
                count++;
            }
        }
        return count;
    }

    private static boolean intersectsShadow(VoxelGrid surface,
                                            PlacementMask placementMask,
                                            boolean[][][] shadowCells) {
        Brick brick = placementMask.brick();
        PartMask mask = placementMask.mask();
        for (PartMask.VoxelOffset offset : mask.solidOccupancyMask()) {
            int x = brick.x() + offset.dx();
            int y = brick.y() + offset.dy();
            int z = brick.z() + offset.dz();
            if (inBounds(surface, x, y, z) && shadowCells[x][y][z]) {
                return true;
            }
        }
        return false;
    }

    private static void markSlopeShadowCells(VoxelGrid surface,
                                             boolean[][][] shadowCells,
                                             Brick slope) {
        for (int k = 0; k < slope.heightUnits(); k++) {
            int shadowY = slope.y() + k;
            if (shadowY >= surface.height()) {
                break;
            }

            for (int d = 1; d <= k + 1; d++) {
                switch (slope.facing()) {
                    case NORTH -> {
                        int sz = slope.z() - d;
                        if (sz < 0) {
                            continue;
                        }
                        for (int x = slope.x(); x < Math.min(slope.maxX(), surface.width()); x++) {
                            shadowCells[x][shadowY][sz] = true;
                        }
                    }
                    case SOUTH -> {
                        int sz = slope.maxZ() - 1 + d;
                        if (sz >= surface.depth()) {
                            continue;
                        }
                        for (int x = slope.x(); x < Math.min(slope.maxX(), surface.width()); x++) {
                            shadowCells[x][shadowY][sz] = true;
                        }
                    }
                    case EAST -> {
                        int sx = slope.maxX() - 1 + d;
                        if (sx >= surface.width()) {
                            continue;
                        }
                        for (int z = slope.z(); z < Math.min(slope.maxZ(), surface.depth()); z++) {
                            shadowCells[sx][shadowY][z] = true;
                        }
                    }
                    case WEST -> {
                        int sx = slope.x() - d;
                        if (sx < 0) {
                            continue;
                        }
                        for (int z = slope.z(); z < Math.min(slope.maxZ(), surface.depth()); z++) {
                            shadowCells[sx][shadowY][z] = true;
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    private static void markSlopeInfluenceCells(VoxelGrid surface,
                                                boolean[][][] slopeInfluence,
                                                Brick slope) {
        for (int y = slope.y(); y < slope.maxY() && y < surface.height(); y++) {
            switch (slope.facing()) {
                case NORTH -> {
                    int z = slope.z() - 1;
                    if (z < 0) {
                        continue;
                    }
                    for (int x = slope.x(); x < Math.min(slope.maxX(), surface.width()); x++) {
                        slopeInfluence[x][y][z] = true;
                    }
                }
                case SOUTH -> {
                    int z = slope.maxZ();
                    if (z >= surface.depth()) {
                        continue;
                    }
                    for (int x = slope.x(); x < Math.min(slope.maxX(), surface.width()); x++) {
                        slopeInfluence[x][y][z] = true;
                    }
                }
                case EAST -> {
                    int x = slope.maxX();
                    if (x >= surface.width()) {
                        continue;
                    }
                    for (int z = slope.z(); z < Math.min(slope.maxZ(), surface.depth()); z++) {
                        slopeInfluence[x][y][z] = true;
                    }
                }
                case WEST -> {
                    int x = slope.x() - 1;
                    if (x < 0) {
                        continue;
                    }
                    for (int z = slope.z(); z < Math.min(slope.maxZ(), surface.depth()); z++) {
                        slopeInfluence[x][y][z] = true;
                    }
                }
                default -> { }
            }
        }
    }

    private static boolean isInSlopeInfluence(VoxelGrid surface,
                                              boolean[][][] slopeInfluence,
                                              Brick brick) {
        for (int dy = 1; dy < brick.heightUnits(); dy++) {
            int y = brick.y() + dy;
            if (y < 0 || y >= surface.height()) {
                continue;
            }
            for (int dx = 0; dx < brick.studX(); dx++) {
                int x = brick.x() + dx;
                if (x < 0 || x >= surface.width()) {
                    continue;
                }
                for (int dz = 0; dz < brick.studY(); dz++) {
                    int z = brick.z() + dz;
                    if (z < 0 || z >= surface.depth()) {
                        continue;
                    }
                    if (slopeInfluence[x][y][z]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean inBounds(VoxelGrid grid, int x, int y, int z) {
        return x >= 0 && x < grid.width()
            && y >= 0 && y < grid.height()
            && z >= 0 && z < grid.depth();
    }

    private record PlacementMask(Brick brick, PartMask mask) {}
}
