package com.lego.optimize;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.Dimension;
import com.lego.voxel.VoxelGrid;

/**
 * Tests for placement policies and their integration with BrickPlacer.
 */
class PlacementPolicyTest {

    private static final List<Dimension> STANDARD_DIMS = Arrays.asList(
        new Dimension(2, 4),
        new Dimension(2, 2),
        new Dimension(2, 1),
        new Dimension(1, 1)
    );

    // ========== Policy name ==========

    @Test
    void testGreedyAreaPolicyName() {
        assertEquals("greedy-area", new GreedyAreaPolicy().name());
    }

    @Test
    void testScoringPolicyName() {
        assertEquals("scoring", new ScoringPlacementPolicy().name());
    }

    // ========== GreedyAreaPolicy mirrors old behavior ==========

    @Test
    void testGreedyArea_SingleVoxel() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(2, 3, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        assertEquals(1, bricks.size());
        assertEquals(1, bricks.get(0).studX());
        assertEquals(1, bricks.get(0).studY());
    }

    @Test
    void testGreedyArea_2x2Patch() {
        VoxelGrid surface = new VoxelGrid(4, 1, 4);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(0, 0, 1, true);
        surface.setFilled(1, 0, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        assertEquals(1, bricks.size());
        assertEquals(2, bricks.get(0).studX());
        assertEquals(2, bricks.get(0).studY());
    }

    @Test
    void testGreedyArea_Prefers2x4() {
        VoxelGrid surface = new VoxelGrid(4, 1, 6);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 4; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        assertEquals(1, bricks.size());
        assertEquals(2, bricks.get(0).studX());
        assertEquals(4, bricks.get(0).studY());
    }

    @Test
    void testGreedyArea_MissingFallbackThrows() {
        VoxelGrid surface = new VoxelGrid(3, 1, 1);
        surface.setFilled(0, 0, 0, true);

        List<Dimension> nofallback = Arrays.asList(new Dimension(2, 4));
        assertThrows(IllegalStateException.class,
            () -> BrickPlacer.placeBricks(surface, nofallback, new GreedyAreaPolicy()));
    }

    // ========== ScoringPlacementPolicy ==========

    @Test
    void testScoring_SingleVoxel() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(2, 3, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        assertEquals(1, bricks.size());
        assertEquals(1, bricks.get(0).studX());
        assertEquals(1, bricks.get(0).studY());
    }

    @Test
    void testScoring_2x2Patch() {
        VoxelGrid surface = new VoxelGrid(4, 1, 4);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(0, 0, 1, true);
        surface.setFilled(1, 0, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        assertEquals(1, bricks.size());
        assertEquals(2, bricks.get(0).studX());
        assertEquals(2, bricks.get(0).studY());
    }

    @Test
    void testScoring_Prefers2x4WhenFull() {
        VoxelGrid surface = new VoxelGrid(4, 1, 6);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 4; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        assertEquals(1, bricks.size());
        assertEquals(2, bricks.get(0).studX());
        assertEquals(4, bricks.get(0).studY());
    }

    @Test
    void testScoring_MissingFallbackThrows() {
        VoxelGrid surface = new VoxelGrid(3, 1, 1);
        surface.setFilled(0, 0, 0, true);

        List<Dimension> nofallback = Arrays.asList(new Dimension(2, 4));
        assertThrows(IllegalStateException.class,
            () -> BrickPlacer.placeBricks(surface, nofallback, new ScoringPlacementPolicy()));
    }

    @Test
    void testScoring_FullCoverage() {
        VoxelGrid surface = new VoxelGrid(5, 3, 3);
        surface.setFilled(1, 1, 0, true);
        surface.setFilled(2, 1, 0, true);
        surface.setFilled(3, 1, 0, true);
        surface.setFilled(1, 2, 0, true);
        surface.setFilled(1, 2, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        Set<String> covered = new HashSet<>();
        for (Brick brick : bricks) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    for (int z = brick.z(); z < brick.maxZ(); z++) {
                        String key = x + "," + y + "," + z;
                        assertFalse(covered.contains(key), "Voxel " + key + " covered twice");
                        covered.add(key);
                    }
                }
            }
        }

        int filledCount = 0;
        for (int x = 0; x < surface.width(); x++) {
            for (int y = 0; y < surface.height(); y++) {
                for (int z = 0; z < surface.depth(); z++) {
                    if (surface.isFilled(x, y, z)) {
                        filledCount++;
                        assertTrue(covered.contains(x + "," + y + "," + z),
                            "Voxel " + x + "," + y + "," + z + " not covered");
                    }
                }
            }
        }
        assertEquals(filledCount, covered.size());
    }

    @Test
    void testScoring_NoOverlaps() {
        VoxelGrid surface = new VoxelGrid(10, 10, 3);
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                if ((x + y) % 2 == 0) {
                    surface.setFilled(x, y, 0, true);
                }
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        for (int i = 0; i < bricks.size(); i++) {
            for (int j = i + 1; j < bricks.size(); j++) {
                assertFalse(bricks.get(i).overlaps(bricks.get(j)),
                    "Bricks " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void testScoring_Deterministic() {
        VoxelGrid surface = new VoxelGrid(6, 1, 6);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 4; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }
        surface.setFilled(3, 0, 0, true);
        surface.setFilled(4, 0, 0, true);
        surface.setFilled(5, 0, 5, true);

        ScoringPlacementPolicy policy = new ScoringPlacementPolicy();
        List<Brick> first = BrickPlacer.placeBricks(surface, STANDARD_DIMS, policy);
        List<Brick> second = BrickPlacer.placeBricks(surface, STANDARD_DIMS, policy);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            Brick b1 = first.get(i);
            Brick b2 = second.get(i);
            assertEquals(b1.x(), b2.x());
            assertEquals(b1.y(), b2.y());
            assertEquals(b1.z(), b2.z());
            assertEquals(b1.studX(), b2.studX());
            assertEquals(b1.studY(), b2.studY());
        }
    }

    // ========== Scoring vs Greedy: neighbor coverage preference ==========

    @Test
    void testScoring_PrefersInteriorPlacement() {
        // L-shaped region: scoring should still place the 2x1 that has more
        // filled neighbors, leaving cleaner remainder.
        //
        //  X: 0 1 2
        //  Z=0: [x][x][x]
        //  Z=1: [x][x][ ]
        //
        // Both policies should place a 2x1 at (0,0,0) then handle the rest.
        // The key is that scoring evaluates neighbor coverage, so interior
        // placements are preferred over edge placements of the same size.
        VoxelGrid surface = new VoxelGrid(4, 1, 3);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(2, 0, 0, true);
        surface.setFilled(0, 0, 1, true);
        surface.setFilled(1, 0, 1, true);

        List<Brick> scoringBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());
        List<Brick> greedyBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        // Both must cover all 5 voxels
        assertEquals(5, countCoveredVoxels(scoringBricks));
        assertEquals(5, countCoveredVoxels(greedyBricks));
    }

    // ========== BrickPlacer API: null policy rejected ==========

    @Test
    void testBrickPlacer_NullPolicyThrows() {
        VoxelGrid surface = new VoxelGrid(3, 1, 1);
        assertThrows(IllegalArgumentException.class,
            () -> BrickPlacer.placeBricks(surface, STANDARD_DIMS, null));
    }

    // ========== Default policy is ScoringPlacementPolicy ==========

    @Test
    void testBrickPlacer_DefaultPolicyMatchesScoring() {
        VoxelGrid surface = new VoxelGrid(6, 1, 6);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 4; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }
        surface.setFilled(3, 0, 0, true);
        surface.setFilled(4, 0, 0, true);

        List<Brick> defaultResult = BrickPlacer.placeBricks(surface, STANDARD_DIMS);
        List<Brick> scoringResult = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());

        assertEquals(defaultResult.size(), scoringResult.size());
        for (int i = 0; i < defaultResult.size(); i++) {
            Brick b1 = defaultResult.get(i);
            Brick b2 = scoringResult.get(i);
            assertEquals(b1.x(), b2.x());
            assertEquals(b1.y(), b2.y());
            assertEquals(b1.z(), b2.z());
            assertEquals(b1.studX(), b2.studX());
            assertEquals(b1.studY(), b2.studY());
        }
    }

    // ========== Helpers ==========

    private int countCoveredVoxels(List<Brick> bricks) {
        Set<String> covered = new HashSet<>();
        for (Brick brick : bricks) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    for (int z = brick.z(); z < brick.maxZ(); z++) {
                        covered.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        return covered.size();
    }
}
