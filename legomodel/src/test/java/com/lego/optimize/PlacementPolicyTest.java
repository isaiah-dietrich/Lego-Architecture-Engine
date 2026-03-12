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
import com.lego.model.ColorRgb;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Tests for placement policies and their integration with BrickPlacer.
 */
class PlacementPolicyTest {

    private static final List<BrickSpec> STANDARD_DIMS = Arrays.asList(
        new BrickSpec(2, 4, 3, "Bricks", "3001"),
        new BrickSpec(2, 2, 3, "Bricks", "3003"),
        new BrickSpec(2, 1, 3, "Bricks", "3004"),
        new BrickSpec(1, 1, 3, "Bricks", "3005")
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

        List<BrickSpec> nofallback = Arrays.asList(new BrickSpec(2, 4, 3, "Bricks", "3001"));
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

        List<BrickSpec> nofallback = Arrays.asList(new BrickSpec(2, 4, 3, "Bricks", "3001"));
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

    // ========== Scoring: orientation exploration ==========

    @Test
    void testScoring_TriesBothOrientations_4x2() {
        // 4-wide × 2-deep filled region.
        // Greedy only sees (2,4) orientation — doesn't fit (depth too shallow).
        // Falls back to two 2×2 bricks = 2 bricks.
        // Scoring tries rotated (4,2) orientation — fits perfectly = 1 brick.
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }

        List<Brick> scoringBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());
        List<Brick> greedyBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        // Scoring finds the rotated 4×2 placement
        assertEquals(1, scoringBricks.size(), "Scoring should use 1 rotated 4x2 brick");
        assertEquals(4, scoringBricks.get(0).studX());
        assertEquals(2, scoringBricks.get(0).studY());

        // Greedy uses two 2×2 bricks (can't see the rotated orientation)
        assertEquals(2, greedyBricks.size(), "Greedy should use 2 bricks (no rotation)");
    }

    @Test
    void testScoring_RotatedOrientation_1x2Column() {
        // 1-wide × 2-deep filled column.
        // Greedy has (2,1) in list — needs 2 wide, doesn't fit at width=1.
        // Falls back to two 1×1 bricks.
        // Scoring tries rotated (1,2) — fits! Uses 1 brick.
        VoxelGrid surface = new VoxelGrid(1, 1, 2);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(0, 0, 1, true);

        List<Brick> scoringBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());
        List<Brick> greedyBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        assertEquals(1, scoringBricks.size(), "Scoring should use 1 rotated 1x2 brick");
        assertEquals(1, scoringBricks.get(0).studX());
        assertEquals(2, scoringBricks.get(0).studY());

        assertEquals(2, greedyBricks.size(), "Greedy should use 2 × 1x1 bricks");
    }

    @Test
    void testScoring_OrientationReducesBrickCount() {
        // Irregular region where orientation exploration produces fewer bricks.
        // 4-wide × 3-deep:
        //   Z=0: [x][x][x][x]
        //   Z=1: [x][x][x][x]
        //   Z=2: [x][x][ ][ ]
        // Greedy: 2x2 at (0,0,0), 2x2 at (2,0,0)... but (2,0) has depth 2
        //         so 2x2 needs z=0,1,  at (2,0,0): ok. Then z=2: 2x1 at (0,0,2) = 3 bricks.
        // Scoring: 4x2 at (0,0,0) covers x=0..3,z=0..1 = 1 brick. Then 2x1
        //         at (0,0,2) = 2 bricks total. Fewer!
        VoxelGrid surface = new VoxelGrid(4, 1, 3);
        for (int x = 0; x < 4; x++) {
            surface.setFilled(x, 0, 0, true);
            surface.setFilled(x, 0, 1, true);
        }
        surface.setFilled(0, 0, 2, true);
        surface.setFilled(1, 0, 2, true);

        List<Brick> scoringBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new ScoringPlacementPolicy());
        List<Brick> greedyBricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS, new GreedyAreaPolicy());

        assertEquals(10, countCoveredVoxels(scoringBricks));
        assertEquals(10, countCoveredVoxels(greedyBricks));

        assertTrue(scoringBricks.size() < greedyBricks.size(),
            "Scoring (" + scoringBricks.size() + ") should use fewer bricks than greedy (" +
            greedyBricks.size() + ") via orientation exploration");
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

    // ========== Color-aware scoring ==========

    @Test
    void testScoring_NullColorsDefaultsToAreaPreference() {
        // Without color data, scoring should still prefer larger bricks
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(null));

        assertEquals(1, bricks.size(), "Null color grid should behave like no-color mode");
    }

    @Test
    void testScoring_UniformColorPrefersLargeBrick() {
        // 4×2 region with identical red voxels — should place a single 4×2
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        ColorRgb[][][] colors = new ColorRgb[4][1][2];
        ColorRgb red = new ColorRgb(1.0f, 0.0f, 0.0f);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
                colors[x][0][z] = red;
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(colors));

        assertEquals(1, bricks.size(), "Uniform color should still prefer 1 large brick");
        assertEquals(4, bricks.get(0).studX());
        assertEquals(2, bricks.get(0).studY());
    }

    @Test
    void testScoring_SharpColorBoundaryProducesMoreBricks() {
        // 2×2 region: top row red, bottom row blue (very different colors).
        // With color awareness: the 2×2 brick straddles the boundary (low uniformity),
        // so the policy should prefer 2×1 bricks that each cover a single color.
        VoxelGrid surface = new VoxelGrid(2, 1, 2);
        ColorRgb[][][] colors = new ColorRgb[2][1][2];
        ColorRgb red = new ColorRgb(1.0f, 0.0f, 0.0f);
        ColorRgb blue = new ColorRgb(0.0f, 0.0f, 1.0f);
        for (int x = 0; x < 2; x++) {
            surface.setFilled(x, 0, 0, true);
            surface.setFilled(x, 0, 1, true);
            colors[x][0][0] = red;
            colors[x][0][1] = blue;
        }

        List<Brick> colorAware = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(colors));
        List<Brick> noColor = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy());

        // Without color: 1 brick (2×2). With color: 2 bricks (each 2×1, single color).
        assertEquals(1, noColor.size(), "No-color mode should use 1 × 2x2 brick");
        assertEquals(2, colorAware.size(), "Color-aware should split at boundary into 2 bricks");

        // Verify each color-aware brick is 2×1 (covers one color row)
        for (Brick b : colorAware) {
            assertTrue(b.studX() * b.studY() <= 2,
                "Color-boundary bricks should be small: " + b.studX() + "x" + b.studY());
        }
    }

    @Test
    void testScoring_ColorAwareStillCoversAllVoxels() {
        // Checkerboard-ish color pattern on a 4×4 surface
        VoxelGrid surface = new VoxelGrid(4, 1, 4);
        ColorRgb[][][] colors = new ColorRgb[4][1][4];
        ColorRgb white = new ColorRgb(1.0f, 1.0f, 1.0f);
        ColorRgb black = new ColorRgb(0.0f, 0.0f, 0.0f);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                surface.setFilled(x, 0, z, true);
                colors[x][0][z] = (x + z) % 2 == 0 ? white : black;
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(colors));

        assertEquals(16, countCoveredVoxels(bricks), "All 16 voxels must be covered");
    }

    // ========== High color variance → smallest brick ==========

    @Test
    void testScoring_HighVarianceForcesSmallestBrick() {
        // 4×2 region with a checkerboard of very different colors.
        // Every interior voxel has ≥2 different-color neighbors → high variance.
        // Policy should force 1×1 bricks everywhere.
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        ColorRgb[][][] colors = new ColorRgb[4][1][2];
        ColorRgb white = new ColorRgb(1.0f, 1.0f, 1.0f);
        ColorRgb black = new ColorRgb(0.0f, 0.0f, 0.0f);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
                colors[x][0][z] = (x + z) % 2 == 0 ? white : black;
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(colors));

        // Every brick should be 1×1 because all anchor voxels are high-variance
        for (Brick b : bricks) {
            assertEquals(1, b.studX() * b.studY(),
                "High-variance region should produce 1x1 bricks, got " + b.studX() + "x" + b.studY());
        }
        assertEquals(8, bricks.size(), "Should be exactly 8 × 1x1 bricks");
        assertEquals(8, countCoveredVoxels(bricks), "All 8 voxels must be covered");
    }

    @Test
    void testScoring_LowVarianceStillPrefersLargeBrick() {
        // 4×2 region with uniform color — no high variance, so large bricks win
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        ColorRgb[][][] colors = new ColorRgb[4][1][2];
        ColorRgb red = new ColorRgb(1.0f, 0.0f, 0.0f);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
                colors[x][0][z] = red;
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(colors));

        assertEquals(1, bricks.size(), "Uniform color should still produce 1 large brick");
        assertEquals(4, bricks.get(0).studX());
        assertEquals(2, bricks.get(0).studY());
    }

    @Test
    void testScoring_VarianceMapComputedCorrectly() {
        // Set up a 3×1×3 grid with known colors:
        // Row z=0: red, red, red
        // Row z=1: red, blue, red    (blue center has 2 red neighbors → high variance)
        // Row z=2: red, red, red
        ColorRgb[][][] colors = new ColorRgb[3][1][3];
        ColorRgb red = new ColorRgb(1.0f, 0.0f, 0.0f);
        ColorRgb blue = new ColorRgb(0.0f, 0.0f, 1.0f);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                colors[x][0][z] = red;
            }
        }
        colors[1][0][1] = blue; // center voxel is blue

        boolean[][][] variance = ScoringPlacementPolicy.computeVarianceMap(colors);

        // Center voxel (1,0,1) has 4 red neighbors, all different from blue → 4 changes ≥ 2
        assertTrue(variance[1][0][1], "Blue center with 4 different neighbors should be high-variance");

        // Corner voxel (0,0,0) has 2 same-color neighbors → 0 changes
        assertFalse(variance[0][0][0], "Red corner with all-red neighbors should NOT be high-variance");

        // Adjacent to blue: (1,0,0) has neighbors: (0,0,0)=red, (2,0,0)=red, (1,0,1)=blue
        // Only 1 different → below threshold of 2
        assertFalse(variance[1][0][0], "Voxel with only 1 different neighbor should NOT be high-variance");
    }

    @Test
    void testScoring_VarianceMapNullColors() {
        // Null voxel colors should produce no high-variance voxels (no early exit)
        VoxelGrid surface = new VoxelGrid(4, 1, 2);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 2; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface, STANDARD_DIMS,
            new ScoringPlacementPolicy(null));

        // Without color data, should behave normally (large bricks preferred)
        assertEquals(1, bricks.size(), "Null colors should not trigger variance early-exit");
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
