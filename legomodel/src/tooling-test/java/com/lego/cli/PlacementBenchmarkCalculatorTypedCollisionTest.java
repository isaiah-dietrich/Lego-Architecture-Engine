package com.lego.cli;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

class PlacementBenchmarkCalculatorTypedCollisionTest {

    private static final List<BrickSpec> SPECS = List.of(
        new BrickSpec(2, 2, 1, "Bricks", "3003"),
        new BrickSpec(2, 1, 1, "Bricks", "3004"),
        new BrickSpec(1, 1, 1, "Bricks", "3005"),
        new BrickSpec(2, 2, 3, "Bricks Sloped", "3039", "Slope 45 2x2", 45.0),
        new BrickSpec(2, 1, 3, "Bricks Sloped", "3040b", "Slope 45 2x1", 45.0)
    );

    @Test
    void overlapPlacementCount_countsUniqueConflictingPlacements() {
        VoxelGrid surface = filledSurface(8, 4, 4);
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 2, 2, 1, "3003"),
            new Brick(1, 0, 0, 2, 2, 1, "3003"),
            new Brick(2, 0, 0, 2, 2, 1, "3003")
        );

        PlacementBenchmarkMetrics metrics = metrics(surface, bricks);

        assertEquals(3, metrics.overlapPlacementCount(), "All three placements should be marked conflicting");
        assertTrue(metrics.collisionCount() > 0, "Legacy collision voxels should still be non-zero");
    }

    @Test
    void outsideCoveragePlacementCount_countsOncePerOffendingPlacement() {
        VoxelGrid surface = filledSurface(3, 2, 3);
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 1, 1, 1, "3005"),
            new Brick(2, 0, 2, 2, 2, 1, "3003")
        );

        PlacementBenchmarkMetrics metrics = metrics(surface, bricks);

        assertEquals(1, metrics.outsideCoveragePlacementCount());
        assertTrue(metrics.outsideTargetCoverageCount() > 1,
            "Legacy outside coverage should still count all offending cells");
    }

    @Test
    void missingNormalAndFacingMismatch_areClassifiedSeparately() {
        VoxelGrid surface = filledSurface(6, 6, 6);
        surface.accumulateNormal(2, 0, 0, new Vector3(0.707f, 0.707f, 0f));
        surface.normalizeNormals();

        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 2, 1, 3, "3040b", Facing.NORTH),
            new Brick(2, 0, 0, 2, 1, 3, "3040b", Facing.NORTH)
        );

        PlacementBenchmarkMetrics metrics = metrics(surface, bricks);

        assertEquals(1, metrics.slopeMissingNormalPlacementCount());
        assertEquals(1, metrics.slopeFacingMismatchPlacementCount());
        assertEquals(0, metrics.slopeAngleMismatchPlacementCount());
        assertEquals(0, metrics.flatSlopeErrorCount(),
            "Legacy angle mismatch should remain separate and unchanged");
    }

    @Test
    void slopeShadowIntrusionPlacementCount_detectsIntrudingFlatPlacement() {
        VoxelGrid surface = filledSurface(8, 8, 8);
        List<Brick> bricks = List.of(
            new Brick(3, 0, 3, 2, 1, 3, "3040b", Facing.NORTH),
            new Brick(3, 0, 2, 1, 1, 1, "3005")
        );

        PlacementBenchmarkMetrics metrics = metrics(surface, bricks);

        assertEquals(1, metrics.slopeShadowIntrusionPlacementCount());
        assertEquals(0, metrics.slopeAdjacentTallFlatConflictPlacementCount(),
            "Single-plate intrusions should not be counted as tall-flat adjacency conflicts");
    }

    @Test
    void slopeAdjacentTallFlatConflictPlacementCount_detectsTallFlatConflict() {
        VoxelGrid surface = filledSurface(10, 10, 10);
        List<Brick> bricks = List.of(
            new Brick(4, 0, 5, 1, 1, 3, "3005"),
            new Brick(5, 1, 5, 2, 1, 3, "3040b", Facing.WEST)
        );

        PlacementBenchmarkMetrics metrics = metrics(surface, bricks);

        assertEquals(1, metrics.slopeAdjacentTallFlatConflictPlacementCount());
    }

    @Test
    void shellLeakResidualVoxelCount_staysVoxelBasedAndMirrorsLegacyShellLeak() {
        VoxelGrid surface = new VoxelGrid(2, 2, 2);
        surface.setFilled(0, 0, 0, true);

        PlacementBenchmarkMetrics metrics = metrics(surface, List.of());

        assertEquals(1, metrics.uncoveredRequiredCount());
        assertEquals(1, metrics.shellLeakCount());
        assertEquals(1, metrics.shellLeakResidualVoxelCount());
        assertFalse(metrics.typedHardGatesZero(),
            "Residual shell leak voxels should fail typed hard-gates");
    }

    @Test
    void overlapPlacementCount_maskUsesMaskOccupancyNotAabb() {
        VoxelGrid surface = filledSurface(6, 6, 6);
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH),
            new Brick(0, 2, 0, 2, 1, 1, "3004")
        );

        PlacementBenchmarkMetrics baselineStyle = metrics("test-policy", surface, bricks);
        PlacementBenchmarkMetrics maskStyle = metrics("mask", surface, bricks);

        assertEquals(2, baselineStyle.overlapPlacementCount(),
            "AABB-style overlap should count both placements");
        assertEquals(0, maskStyle.overlapPlacementCount(),
            "Mask overlap must be mask-based and ignore non-overlapping occupied voxels");
    }

    @Test
    void overlapPlacementCount_maskDetectsRealMaskOverlap() {
        VoxelGrid surface = filledSurface(6, 6, 6);
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 2, 2, 1, "3003"),
            new Brick(1, 0, 0, 2, 2, 1, "3003")
        );

        PlacementBenchmarkMetrics metrics = metrics("mask", surface, bricks);
        assertEquals(2, metrics.overlapPlacementCount(),
            "Mask overlap should count true occupied-voxel overlaps");
    }

    private static PlacementBenchmarkMetrics metrics(VoxelGrid surface, List<Brick> bricks) {
        return metrics("test-policy", surface, bricks);
    }

    private static PlacementBenchmarkMetrics metrics(String policyName, VoxelGrid surface, List<Brick> bricks) {
        return PlacementBenchmarkCalculator.compute(
            policyName,
            surface,
            SPECS,
            bricks,
            null,
            1L,
            0
        );
    }

    private static VoxelGrid filledSurface(int width, int height, int depth) {
        VoxelGrid surface = new VoxelGrid(width, height, depth);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    surface.setFilled(x, y, z, true);
                }
            }
        }
        return surface;
    }
}
