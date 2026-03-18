package com.lego.optimize;

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
}
