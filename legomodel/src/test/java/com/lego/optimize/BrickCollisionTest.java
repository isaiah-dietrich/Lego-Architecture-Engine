package com.lego.optimize;

import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Collision detection tests for BrickPlacer output.
 *
 * Verifies that no two placed bricks overlap in voxel space, with special
 * attention to slope bricks whose heightUnits (3) extend above their
 * single-layer surface placement.
 */
class BrickCollisionTest {

    // Standard flat specs
    private static final List<BrickSpec> FLAT_SPECS = Arrays.asList(
        new BrickSpec(2, 4, 1, "Bricks", "3001"),
        new BrickSpec(2, 2, 1, "Bricks", "3003"),
        new BrickSpec(2, 1, 1, "Bricks", "3004"),
        new BrickSpec(1, 1, 1, "Bricks", "3005")
    );

    // Slope + flat specs (slope first for priority, fallback 1×1)
    private static final List<BrickSpec> SLOPE_AND_FLAT_SPECS = Arrays.asList(
        new BrickSpec(2, 4, 3, "Bricks Sloped", "3037", "Slope 45° 2x4", 45.0),
        new BrickSpec(2, 2, 3, "Bricks Sloped", "3039", "Slope 45° 2x2", 45.0),
        new BrickSpec(2, 1, 3, "Bricks Sloped", "3040b", "Slope 45° 2x1", 45.0),
        new BrickSpec(3, 2, 3, "Bricks Sloped", "3298", "Slope 33° 3x2", 33.0),
        new BrickSpec(3, 1, 3, "Bricks Sloped", "4286", "Slope 33° 3x1", 33.0),
        new BrickSpec(1, 2, 2, "Bricks Sloped", "85984", "Slope 30° 1x2", 30.0),
        new BrickSpec(2, 4, 1, "Bricks", "3001"),
        new BrickSpec(2, 2, 1, "Bricks", "3003"),
        new BrickSpec(2, 1, 1, "Bricks", "3004"),
        new BrickSpec(1, 1, 1, "Bricks", "3005")
    );

    // ========== Utility ==========

    /**
     * Asserts that no two bricks in the list overlap in voxel space.
     * Uses Brick.overlaps() which checks the full bounding box including heightUnits.
     */
    private static void assertNoCollisions(List<Brick> bricks) {
        for (int i = 0; i < bricks.size(); i++) {
            for (int j = i + 1; j < bricks.size(); j++) {
                Brick a = bricks.get(i);
                Brick b = bricks.get(j);
                assertFalse(a.overlaps(b),
                    "Collision between brick " + i + " (" + a.partId() + " at "
                    + a.x() + "," + a.y() + "," + a.z()
                    + " " + a.studX() + "x" + a.studY() + "xh" + a.heightUnits()
                    + " " + a.facing()
                    + ") and brick " + j + " (" + b.partId() + " at "
                    + b.x() + "," + b.y() + "," + b.z()
                    + " " + b.studX() + "x" + b.studY() + "xh" + b.heightUnits()
                    + " " + b.facing() + ")");
            }
        }
    }

    /**
     * Creates a VoxelGrid with normals pointing in the given direction for
     * all filled voxels in the specified region.
     */
    private static VoxelGrid createSlopeSurface(int width, int height, int depth,
                                                  int fillXStart, int fillXEnd,
                                                  int fillZStart, int fillZEnd,
                                                  int y, Vector3 normal) {
        VoxelGrid surface = new VoxelGrid(width, height, depth);
        for (int x = fillXStart; x < fillXEnd; x++) {
            for (int z = fillZStart; z < fillZEnd; z++) {
                surface.setFilled(x, y, z, true);
                surface.accumulateNormal(x, y, z, normal);
            }
        }
        surface.normalizeNormals();
        return surface;
    }

    // ========== Flat brick collision tests ==========

    @Test
    void flatBricksOnSingleLayer_noCollisions() {
        VoxelGrid surface = new VoxelGrid(10, 1, 10);
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }
        List<Brick> bricks = BrickPlacer.placeBricks(surface, FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void flatBricksOnMultipleLayers_noCollisions() {
        VoxelGrid surface = new VoxelGrid(8, 5, 8);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
                surface.setFilled(x, 2, z, true);
                surface.setFilled(x, 4, z, true);
            }
        }
        List<Brick> bricks = BrickPlacer.placeBricks(surface, FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Slope-only collision tests ==========

    @Test
    void slopesOnSingleLayer_northFacing_noCollisions() {
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        VoxelGrid surface = createSlopeSurface(10, 5, 10, 0, 8, 0, 8, 0, northNormal);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesOnSingleLayer_eastFacing_noCollisions() {
        Vector3 eastNormal = new Vector3(0.707f, 0.707f, 0f);
        VoxelGrid surface = createSlopeSurface(10, 5, 10, 0, 8, 0, 8, 0, eastNormal);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesOnSingleLayer_southFacing_noCollisions() {
        Vector3 southNormal = new Vector3(0f, 0.707f, 0.707f);
        VoxelGrid surface = createSlopeSurface(10, 5, 10, 0, 8, 0, 8, 0, southNormal);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesOnSingleLayer_westFacing_noCollisions() {
        Vector3 westNormal = new Vector3(-0.707f, 0.707f, 0f);
        VoxelGrid surface = createSlopeSurface(10, 5, 10, 0, 8, 0, 8, 0, westNormal);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Multi-layer slope collision tests (key regression) ==========

    @Test
    void slopesWithFilledLayerAbove_noCollisions() {
        // Slope at y=0, flat surface at y=1 — slopes with heightUnits=3
        // extend into y=1 and y=2, so bricks at y=1 must not be placed
        // within a slope's footprint.
        VoxelGrid surface = new VoxelGrid(10, 5, 10);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                // Slope surface at y=0
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, northNormal);
                // Flat surface at y=1 (inside slope's height range)
                surface.setFilled(x, 1, z, true);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesWithFilledLayersTwoAbove_noCollisions() {
        // Slope at y=0 (heightUnits=3 covers y=0..2), flat at y=2.
        VoxelGrid surface = new VoxelGrid(10, 5, 10);
        Vector3 eastNormal = new Vector3(0.707f, 0.707f, 0f);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, eastNormal);
                surface.setFilled(x, 2, z, true);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesStackedOnConsecutiveLayers_noCollisions() {
        // Filled surface at y=0, y=1, y=2 — all with slope normals.
        // Slopes at y=0 (h=3 → covers y=0..2) should block slopes at y=1, y=2.
        VoxelGrid surface = new VoxelGrid(10, 6, 10);
        Vector3 southNormal = new Vector3(0f, 0.707f, 0.707f);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, southNormal);
                surface.setFilled(x, 1, z, true);
                surface.accumulateNormal(x, 1, z, southNormal);
                surface.setFilled(x, 2, z, true);
                surface.accumulateNormal(x, 2, z, southNormal);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Mixed slope + flat at different layers ==========

    @Test
    void slopesAboveFlatBricks_noCollisions() {
        // Flat bricks at y=0 (h=1, covers y=0), slopes at y=1 (h=3, covers y=1..3).
        // No overlap because flat at y=0 doesn't reach y=1.
        VoxelGrid surface = new VoxelGrid(10, 6, 10);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                surface.setFilled(x, 0, z, true);
                // No normal at y=0 → will place flat bricks
                surface.setFilled(x, 1, z, true);
                surface.accumulateNormal(x, 1, z, northNormal);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void flatBricksAboveSlopes_noCollisions() {
        // Slopes at y=0 (h=3, covers y=0..2), flat surface at y=3.
        // y=3 is outside slope's volume, so no collision.
        VoxelGrid surface = new VoxelGrid(10, 8, 10);
        Vector3 westNormal = new Vector3(-0.707f, 0.707f, 0f);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, westNormal);
                surface.setFilled(x, 3, z, true);
                // No normal at y=3 → flat
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Mixed directions in same surface ==========

    @Test
    void mixedFacingDirections_noCollisions() {
        // Left half faces EAST, right half faces WEST — a ridge shape.
        VoxelGrid surface = new VoxelGrid(12, 5, 12);
        Vector3 eastNormal = new Vector3(0.707f, 0.707f, 0f);
        Vector3 westNormal = new Vector3(-0.707f, 0.707f, 0f);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, eastNormal);
            }
        }
        for (int x = 4; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, westNormal);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void slopesAdjacentToFlatRegion_noCollisions() {
        // Slope region on left, flat region on right, same Y layer.
        VoxelGrid surface = new VoxelGrid(12, 5, 12);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        // Slope region
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, northNormal);
            }
        }
        // Flat region (no normal)
        for (int x = 4; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Sparse / irregular surfaces ==========

    @Test
    void sparseSlopeSurface_noCollisions() {
        // Checkerboard-like slope surface — only every other voxel filled.
        VoxelGrid surface = new VoxelGrid(12, 5, 12);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                if ((x + z) % 2 == 0) {
                    surface.setFilled(x, 0, z, true);
                    surface.accumulateNormal(x, 0, z, northNormal);
                }
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void multiLayerStaircaseSurface_noCollisions() {
        // Staircase: each row at increasing Y — mimics a sloped model surface.
        VoxelGrid surface = new VoxelGrid(10, 12, 10);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int z = 0; z < 8; z++) {
            int y = z; // Each z row is one voxel higher
            for (int x = 0; x < 8; x++) {
                surface.setFilled(x, y, z, true);
                surface.accumulateNormal(x, y, z, northNormal);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    // ========== Brick.overlaps() unit tests ==========

    @Test
    void overlaps_identicalBricks_true() {
        Brick a = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        Brick b = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        assertTrue(a.overlaps(b));
    }

    @Test
    void overlaps_sameXZDifferentY_withinHeight_true() {
        // Slope at y=0 with h=3 occupies y=0..2. Plate at y=1 with h=1 is inside.
        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        Brick plate = new Brick(0, 1, 0, 2, 2, 1, "3005");
        assertTrue(slope.overlaps(plate));
    }

    @Test
    void overlaps_sameXZDifferentY_beyondHeight_false() {
        // Slope at y=0 with h=3 occupies y=0..2. Brick at y=3 is outside.
        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        Brick plate = new Brick(0, 3, 0, 2, 2, 1, "3005");
        assertFalse(slope.overlaps(plate));
    }

    @Test
    void overlaps_adjacentXZ_false() {
        Brick a = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        Brick b = new Brick(2, 0, 0, 2, 2, 3, "3039", Facing.SOUTH);
        assertFalse(a.overlaps(b));
    }

    @Test
    void overlaps_partialXZOverlap_sameY_true() {
        Brick a = new Brick(0, 0, 0, 3, 2, 3, "3298", Facing.NORTH);
        Brick b = new Brick(2, 0, 0, 3, 2, 3, "3298", Facing.NORTH);
        assertTrue(a.overlaps(b)); // X ranges [0,3) and [2,5) overlap at x=2
    }

    // ========== markCovered tests ==========

    @Test
    void markCovered_slopeMarksFullHeight() {
        boolean[][][] covered = new boolean[5][6][5];
        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        BrickPlacer.markCovered(covered, slope);

        // All three Y layers should be covered
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                assertTrue(covered[x][0][z], "y=0 should be covered");
                assertTrue(covered[x][1][z], "y=1 should be covered");
                assertTrue(covered[x][2][z], "y=2 should be covered");
                assertFalse(covered[x][3][z], "y=3 should NOT be covered");
            }
        }
    }

    @Test
    void markCovered_flatBrickMarksFullHeight() {
        boolean[][][] covered = new boolean[5][5][5];
        Brick flat = new Brick(0, 0, 0, 2, 2, 1, "3005");
        BrickPlacer.markCovered(covered, flat);

        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                assertTrue(covered[x][0][z], "y=0 should be covered");
                assertFalse(covered[x][1][z], "y=1 should NOT be covered (h=1)");
            }
        }
    }

    @Test
    void markCovered_slopeBlocksSubsequentPlacement() {
        // Simulate: slope at y=0 should block placement at y=1 for same XZ.
        VoxelGrid surface = new VoxelGrid(4, 6, 4);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, northNormal);
                surface.setFilled(x, 1, z, true); // Would collide with slope
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());

        // Count bricks at y=1 within the slope's XZ footprint
        long bricksAtY1InFootprint = bricks.stream()
            .filter(b -> b.y() == 1 && b.x() < 2 && b.z() < 2)
            .count();
        assertEquals(0, bricksAtY1InFootprint,
            "No bricks should be placed at y=1 within slope's footprint");
        assertNoCollisions(bricks);
    }

    @Test
    void preMark_adjacentSteepButUnmatchableNormal_isSuppressed() {
        // Regression case: voxel in front of a 45° slope has a steep normal (~25°)
        // that does NOT match any supported slope angle. It should be suppressed
        // during pre-mark so a flat stud is not placed into the slope's visual zone.
        VoxelGrid surface = new VoxelGrid(4, 3, 5);

        // 45° NORTH-facing slope candidate footprint at z=1..2
        Vector3 north45 = new Vector3(0f, 0.707f, -0.707f);
        surface.setFilled(1, 0, 1, true);
        surface.accumulateNormal(1, 0, 1, north45);
        surface.setFilled(1, 0, 2, true);
        surface.accumulateNormal(1, 0, 2, north45);

        // Front-adjacent voxel at z=0 with steep but non-matching ~25° normal
        // (no 25° slope in allowed specs below).
        Vector3 north25 = new Vector3(0f, 0.906f, -0.423f);
        surface.setFilled(1, 0, 0, true);
        surface.accumulateNormal(1, 0, 0, north25);
        surface.normalizeNormals();

        List<BrickSpec> specs = Arrays.asList(
            new BrickSpec(2, 1, 3, "Bricks Sloped", "3040b", "Slope 45° 2x1", 45.0),
            new BrickSpec(1, 1, 1, "Bricks", "3005")
        );

        List<Brick> bricks = BrickPlacer.placeBricks(surface, specs, new ScoringPlacementPolicy());

        assertTrue(bricks.stream().anyMatch(b -> b.facing() == Facing.NORTH),
            "A NORTH-facing slope should still be placed");

        boolean flatAtFrontVoxel = bricks.stream()
            .filter(b -> b.facing() == Facing.NONE)
            .anyMatch(b -> b.x() <= 1 && 1 < b.maxX()
                        && b.y() <= 0 && 0 < b.maxY()
                        && b.z() <= 0 && 0 < b.maxZ());
        assertFalse(flatAtFrontVoxel,
            "Front-adjacent voxel with non-matching steep normal should be pre-suppressed");
    }

    // ========== Edge cases ==========

    @Test
    void slopeNearGridBoundary_noCollisions() {
        // Slope at the edge of the grid — heightUnits should be clamped to grid bounds.
        VoxelGrid surface = new VoxelGrid(4, 3, 4);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, northNormal);
            }
        }
        surface.normalizeNormals();

        // Should not throw ArrayIndexOutOfBoundsException even though
        // slope heightUnits=3 matches grid height exactly.
        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);
    }

    @Test
    void singleVoxelSlope_noCollisions() {
        VoxelGrid surface = new VoxelGrid(3, 5, 3);
        surface.setFilled(1, 0, 1, true);
        surface.accumulateNormal(1, 0, 1, new Vector3(0f, 0.707f, -0.707f));
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertEquals(1, bricks.size());
        assertNoCollisions(bricks);
    }

    // ========== Slope shadow zone tests ==========

    @Test
    void markCovered_slopeShadowNorth_coversInFrontAtUpperLayers() {
        // NORTH-facing slope at z=3: shadow extends with d <= k+1
        boolean[][][] covered = new boolean[5][6][8];
        Brick slope = new Brick(1, 0, 3, 2, 2, 3, "3039", Facing.NORTH);
        BrickPlacer.markCovered(covered, slope);

        // Base footprint: x=1..2, y=0..2, z=3..4
        assertTrue(covered[1][0][3], "base footprint should be covered");
        assertTrue(covered[1][2][4], "base footprint top should be covered");

        // Shadow k=1: d=1,2 → z=2, z=1 at y=1
        assertTrue(covered[1][1][2], "shadow at y=1, z=2 should be covered");
        assertTrue(covered[1][1][1], "shadow at y=1, z=1 should be covered");

        // Shadow k=2: d=1,2,3 → z=2, z=1, z=0 at y=2
        assertTrue(covered[1][2][2], "shadow at y=2, z=2 should be covered");
        assertTrue(covered[1][2][1], "shadow at y=2, z=1 should be covered");
        assertTrue(covered[1][2][0], "shadow at y=2, z=0 should be covered");

        // Shadow now starts at k=0: y=0 at z=2 (d=1) SHOULD be covered
        assertTrue(covered[1][0][2], "y=0 at z=2 should be in base-layer shadow");
    }

    @Test
    void markCovered_slopeShadowSouth_coversBehindsAtUpperLayers() {
        // SOUTH-facing slope at z=0 with studY=2: shadow with d <= k+1
        boolean[][][] covered = new boolean[5][6][7];
        Brick slope = new Brick(1, 0, 0, 2, 2, 3, "3039", Facing.SOUTH);
        BrickPlacer.markCovered(covered, slope);

        // Base: x=1..2, y=0..2, z=0..1
        assertTrue(covered[1][0][0]);
        assertTrue(covered[2][2][1]);

        // Shadow k=1: d=1,2 → z=2, z=3 at y=1
        assertTrue(covered[1][1][2], "shadow SOUTH at y=1, z=2");
        assertTrue(covered[1][1][3], "shadow SOUTH at y=1, z=3");
        // Shadow k=2: d=1,2,3 → z=2, z=3, z=4 at y=2
        assertTrue(covered[1][2][2], "shadow SOUTH at y=2, z=2");
        assertTrue(covered[1][2][3], "shadow SOUTH at y=2, z=3");
        assertTrue(covered[1][2][4], "shadow SOUTH at y=2, z=4");
    }

    @Test
    void markCovered_slopeShadowEast_coversRightAtUpperLayers() {
        boolean[][][] covered = new boolean[10][6][5];
        Brick slope = new Brick(2, 0, 0, 2, 2, 3, "3039", Facing.EAST);
        BrickPlacer.markCovered(covered, slope);

        // Shadow EAST k=1: d=1,2 → x=4, x=5 at y=1
        assertTrue(covered[4][1][0], "shadow EAST at y=1, x=4");
        assertTrue(covered[5][1][0], "shadow EAST at y=1, x=5");
        // Shadow k=2: d=1,2,3 → x=4, x=5, x=6 at y=2
        assertTrue(covered[4][2][0], "shadow EAST at y=2, x=4");
        assertTrue(covered[5][2][0], "shadow EAST at y=2, x=5");
        assertTrue(covered[6][2][0], "shadow EAST at y=2, x=6");
    }

    @Test
    void markCovered_slopeShadowWest_coversLeftAtUpperLayers() {
        boolean[][][] covered = new boolean[8][6][5];
        Brick slope = new Brick(3, 0, 0, 2, 2, 3, "3039", Facing.WEST);
        BrickPlacer.markCovered(covered, slope);

        // Shadow WEST k=1: d=1,2 → x=2, x=1 at y=1
        assertTrue(covered[2][1][0], "shadow WEST at y=1, x=2");
        assertTrue(covered[1][1][0], "shadow WEST at y=1, x=1");
        // Shadow k=2: d=1,2,3 → x=2, x=1, x=0 at y=2
        assertTrue(covered[2][2][0], "shadow WEST at y=2, x=2");
        assertTrue(covered[1][2][0], "shadow WEST at y=2, x=1");
        assertTrue(covered[0][2][0], "shadow WEST at y=2, x=0");
    }

    @Test
    void slopeShadow_suppressesStaircaseArtifact() {
        // Staircase: slope voxels at y=0 z=2, flat voxels at y=1 z=1.
        // Without shadow, a flat brick at (x, 1, 1) would be placed and appear
        // visible through the slope's angled face. With shadow, it's suppressed.
        VoxelGrid surface = new VoxelGrid(6, 5, 6);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);

        // Slope surface at y=0, z=2..3
        for (int x = 0; x < 4; x++) {
            surface.setFilled(x, 0, 2, true);
            surface.accumulateNormal(x, 0, 2, northNormal);
            surface.setFilled(x, 0, 3, true);
            surface.accumulateNormal(x, 0, 3, northNormal);
        }
        // Staircase step: flat at y=1, z=1 (in front of slope)
        for (int x = 0; x < 4; x++) {
            surface.setFilled(x, 1, 1, true);
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());
        assertNoCollisions(bricks);

        // Verify: no flat bricks at y=1, z=1 (they should be suppressed by shadow)
        long bricksAtY1Z1 = bricks.stream()
            .filter(b -> b.y() == 1 && b.z() == 1 && b.facing() == Facing.NONE)
            .count();
        assertEquals(0, bricksAtY1Z1,
            "Flat bricks at staircase step (y=1, z=1) should be suppressed by slope shadow");
    }

    @Test
    void slopeShadow_doesNotAffectNonSlopeDirection() {
        // Shadow only extends in the slope-facing direction.
        // Bricks on the opposite side should not be affected.
        VoxelGrid surface = new VoxelGrid(6, 5, 6);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);

        // Slope surface at y=0, z=2
        for (int x = 0; x < 2; x++) {
            surface.setFilled(x, 0, 2, true);
            surface.accumulateNormal(x, 0, 2, northNormal);
        }
        // Flat voxels BEHIND slope (z=3, y=1) — opposite direction, not shadowed
        for (int x = 0; x < 2; x++) {
            surface.setFilled(x, 1, 3, true);
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());

        // Flat bricks behind the slope (z=3, y=1) SHOULD still be placed
        long bricksAtZ3Y1 = bricks.stream()
            .filter(b -> b.z() == 3 && b.y() == 1 && b.facing() == Facing.NONE)
            .count();
        assertTrue(bricksAtZ3Y1 > 0,
            "Flat bricks behind slope (opposite of face direction) should still be placed");
    }

    // ========== Post-processing: tall brick below slope ==========

    @Test
    void tallBrickBelowSlope_resolvedToPlate() {
        // A 3-height flat brick placed at y=0 with a slope at y=1 (adjacent in
        // the slope-facing direction). The flat brick's upper layers (y=1, y=2)
        // overlap with the slope's Y range, creating a visual conflict.
        // Post-processing should shorten the flat brick to h=1.
        List<Brick> input = new ArrayList<>();
        // Tall flat brick at x=4, y=0, z=5 (h=3, extends to y=0..2)
        input.add(new Brick(4, 0, 5, 1, 1, 3, "3005"));
        // Slope facing WEST at x=5, y=1, z=5 (h=3, extends to y=1..3)
        // Facing WEST means the flat at x=4 is in the slope-facing direction
        input.add(new Brick(5, 1, 5, 2, 1, 3, "3040b", Facing.WEST));

        List<Brick> result = BrickPlacer.resolveSlopeAdjacentConflicts(input, SLOPE_AND_FLAT_SPECS);

        // The tall brick should have been shortened to h=1 (plate)
        Brick resolved = result.get(0);
        assertEquals(1, resolved.heightUnits(),
            "Conflicting tall brick should be shortened to plate height");
        // Part ID comes from the plate lookup in the test spec list — the test
        // spec list uses "3005" for the 1x1 h=1 fallback. In production, the
        // catalog maps to the proper plate ID (e.g. 3024).
        assertEquals("3005", resolved.partId(),
            "Part ID should come from plate-height spec in catalog");
        // Position unchanged
        assertEquals(4, resolved.x());
        assertEquals(0, resolved.y());
        assertEquals(5, resolved.z());
    }

    @Test
    void tallBrickNotAdjacentToSlope_unchanged() {
        // A tall brick that is NOT in the slope-facing direction should be unaffected.
        List<Brick> input = new ArrayList<>();
        // Tall brick at x=4, y=0, z=5
        input.add(new Brick(4, 0, 5, 1, 1, 3, "3005"));
        // Slope facing EAST at x=5, y=1, z=5 — flat at x=4 is WEST, not EAST
        input.add(new Brick(5, 1, 5, 2, 1, 3, "3040b", Facing.EAST));

        List<Brick> result = BrickPlacer.resolveSlopeAdjacentConflicts(input, SLOPE_AND_FLAT_SPECS);

        // The brick should be unchanged (not in the slope's facing direction)
        Brick resolved = result.get(0);
        assertEquals(3, resolved.heightUnits(),
            "Brick not in slope-facing direction should keep original height");
    }

    @Test
    void staircaseSurfaceWithTallBricksBelow_noVisualConflicts() {
        // Multi-layer staircase with slope normals — ensures the placement
        // pipeline handles tall-brick-below-slope conflicts end-to-end.
        VoxelGrid surface = new VoxelGrid(12, 15, 12);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);

        // Staircase: each z row is one voxel higher
        for (int z = 0; z < 8; z++) {
            int y = z;
            for (int x = 0; x < 8; x++) {
                surface.setFilled(x, y, z, true);
                surface.accumulateNormal(x, y, z, northNormal);
            }
        }
        // Also fill some flat layers below to allow tall brick placement
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                if (z > 0) {
                    surface.setFilled(x, z - 1, z, true);
                }
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new ScoringPlacementPolicy());

        // Verify no AABB collisions in voxel space
        assertNoCollisions(bricks);

        // Verify no tall flat bricks adjacent to slopes in the facing direction
        List<Brick> slopeList = bricks.stream()
            .filter(b -> b.facing() != Facing.NONE)
            .toList();
        for (Brick slope : slopeList) {
            for (Brick flat : bricks) {
                if (flat.facing() != Facing.NONE) continue;
                if (flat.heightUnits() <= 1) continue;
                // Check: flat's upper layers overlap slope's Y range
                if (flat.maxY() <= slope.y() || flat.y() >= slope.maxY()) continue;
                // Check: flat is adjacent in facing direction
                boolean adjacent = switch (slope.facing()) {
                    case NORTH -> flat.maxZ() == slope.z()
                        && flat.x() < slope.maxX() && flat.maxX() > slope.x();
                    case SOUTH -> flat.z() == slope.maxZ()
                        && flat.x() < slope.maxX() && flat.maxX() > slope.x();
                    case EAST -> flat.x() == slope.maxX()
                        && flat.z() < slope.maxZ() && flat.maxZ() > slope.z();
                    case WEST -> flat.maxX() == slope.x()
                        && flat.z() < slope.maxZ() && flat.maxZ() > slope.z();
                    default -> false;
                };
                assertFalse(adjacent,
                    "No tall flat brick should be adjacent to slope in facing direction: "
                    + "flat at (" + flat.x() + "," + flat.y() + "," + flat.z()
                    + " h=" + flat.heightUnits() + ") vs slope at ("
                    + slope.x() + "," + slope.y() + "," + slope.z()
                    + " facing=" + slope.facing() + ")");
            }
        }
    }

    @Test
    void staircaseSurfaceWithTallBricksBelow_maskPolicy_noVisualConflicts() {
        VoxelGrid surface = new VoxelGrid(12, 15, 12);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);

        for (int z = 0; z < 8; z++) {
            int y = z;
            for (int x = 0; x < 8; x++) {
                surface.setFilled(x, y, z, true);
                surface.accumulateNormal(x, y, z, northNormal);
            }
        }
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                if (z > 0) {
                    surface.setFilled(x, z - 1, z, true);
                }
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new MaskPlacementPolicy());

        assertNoCollisions(bricks);
        assertNoTallFlatInSlopeFacingDirection(bricks);
    }

    @Test
    void slopesWithFilledLayerAbove_maskPolicy_noCollisions() {
        VoxelGrid surface = new VoxelGrid(10, 5, 10);
        Vector3 northNormal = new Vector3(0f, 0.707f, -0.707f);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                surface.setFilled(x, 0, z, true);
                surface.accumulateNormal(x, 0, z, northNormal);
                surface.setFilled(x, 1, z, true);
            }
        }
        surface.normalizeNormals();

        List<Brick> bricks = BrickPlacer.placeBricks(surface, SLOPE_AND_FLAT_SPECS, new MaskPlacementPolicy());
        assertNoCollisions(bricks);
        assertNoTallFlatInSlopeFacingDirection(bricks);
    }

    private static void assertNoTallFlatInSlopeFacingDirection(List<Brick> bricks) {
        List<Brick> slopeList = bricks.stream()
            .filter(b -> b.facing() != Facing.NONE)
            .toList();
        for (Brick slope : slopeList) {
            for (Brick flat : bricks) {
                if (flat.facing() != Facing.NONE) continue;
                if (flat.heightUnits() <= 1) continue;
                if (flat.maxY() <= slope.y() || flat.y() >= slope.maxY()) continue;
                boolean adjacent = switch (slope.facing()) {
                    case NORTH -> flat.maxZ() == slope.z()
                        && flat.x() < slope.maxX() && flat.maxX() > slope.x();
                    case SOUTH -> flat.z() == slope.maxZ()
                        && flat.x() < slope.maxX() && flat.maxX() > slope.x();
                    case EAST -> flat.x() == slope.maxX()
                        && flat.z() < slope.maxZ() && flat.maxZ() > slope.z();
                    case WEST -> flat.maxX() == slope.x()
                        && flat.z() < slope.maxZ() && flat.maxZ() > slope.z();
                    default -> false;
                };
                assertFalse(adjacent,
                    "No tall flat brick should be adjacent to slope in facing direction: "
                    + "flat at (" + flat.x() + "," + flat.y() + "," + flat.z()
                    + " h=" + flat.heightUnits() + ") vs slope at ("
                    + slope.x() + "," + slope.y() + "," + slope.z()
                    + " facing=" + slope.facing() + ")");
            }
        }
    }
}
