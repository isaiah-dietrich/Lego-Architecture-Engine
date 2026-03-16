package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;

class RegionColorStrategyTest {

    private static LegoPaletteMapper palette;

    @BeforeAll
    static void loadPalette() throws IOException {
        palette = LegoPaletteMapper.loadDefault();
    }

    // ---- Metadata ----

    @Test
    void nameIsRegion() {
        assertEquals("region", new RegionColorStrategy().name());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertFalse(new RegionColorStrategy().description().isEmpty());
    }

    @Test
    void registryContainsRegion() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        assertTrue(registry.availableNames().contains("region"));
    }

    // ---- apply() — standard interface ----

    @Test
    void applyReturnsEmptyForEmptyInput() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, Integer> result = strategy.apply(new HashMap<>(), palette);
        assertTrue(result.isEmpty());
    }

    @Test
    void applyReturnsResultForEveryBrick() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, ColorRgb> input = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1), new ColorRgb(0.8f, 0.7f, 0.0f));
        }
        Map<Brick, Integer> result = strategy.apply(input, palette);
        assertEquals(10, result.size());
    }

    @Test
    void largeUniformRegionGetsUniformColor() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, ColorRgb> input = new HashMap<>();
        // 10 adjacent yellow bricks — should merge into one region
        for (int i = 0; i < 10; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1), new ColorRgb(0.8f, 0.7f, 0.0f));
        }
        Map<Brick, Integer> result = strategy.apply(input, palette);
        int firstCode = result.values().iterator().next();
        for (int code : result.values()) {
            assertEquals(firstCode, code, "All bricks in a uniform region should get the same color");
        }
    }

    @Test
    void differentColorRegionsGetDifferentColors() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, ColorRgb> input = new HashMap<>();
        // 8 yellow bricks on the left
        for (int i = 0; i < 8; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1), new ColorRgb(0.8f, 0.7f, 0.0f));
        }
        // 8 blue bricks on the right (not adjacent to yellow)
        for (int i = 20; i < 28; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1), new ColorRgb(0.0f, 0.0f, 0.8f));
        }
        Map<Brick, Integer> result = strategy.apply(input, palette);

        int yellowCode = result.get(new Brick(0, 0, 0, 1, 1, 1));
        int blueCode = result.get(new Brick(20, 0, 0, 1, 1, 1));
        assertNotEquals(yellowCode, blueCode, "Different color regions should get different codes");
    }

    @Test
    void smallRegionPreservesPerBrickColor() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, ColorRgb> input = new HashMap<>();
        // 3 bricks with very different colors, each isolated (< DETAIL_REGION_SIZE)
        input.put(new Brick(0, 0, 0, 1, 1, 1), new ColorRgb(1.0f, 0.0f, 0.0f));  // red
        input.put(new Brick(10, 0, 0, 1, 1, 1), new ColorRgb(0.0f, 0.0f, 1.0f)); // blue
        input.put(new Brick(20, 0, 0, 1, 1, 1), new ColorRgb(0.0f, 1.0f, 0.0f)); // green

        Map<Brick, Integer> result = strategy.apply(input, palette);
        assertEquals(3, result.size());
        int red = result.get(new Brick(0, 0, 0, 1, 1, 1));
        int blue = result.get(new Brick(10, 0, 0, 1, 1, 1));
        int green = result.get(new Brick(20, 0, 0, 1, 1, 1));
        assertNotEquals(red, blue);
        assertNotEquals(red, green);
    }

    @Test
    void shadowVariationMergesIntoSameRegion() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, ColorRgb> input = new HashMap<>();
        // 10 adjacent bricks with lightness variation (simulating shadows) but same hue
        for (int i = 0; i < 10; i++) {
            float darkening = 1.0f - (i * 0.05f); // 1.0 → 0.55
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                new ColorRgb(0.8f * darkening, 0.7f * darkening, 0.0f));
        }
        Map<Brick, Integer> result = strategy.apply(input, palette);
        int firstCode = result.values().iterator().next();
        long uniformCount = result.values().stream().filter(c -> c == firstCode).count();
        assertTrue(uniformCount >= 8,
            "Shadow-varied same-hue bricks should mostly merge into one uniform region");
    }

    // ---- shouldMerge ----

    @Test
    void shouldMergeTrueForSimilarColors() {
        // Yellow vs slightly darker yellow (only lightness difference)
        double[] lab1 = {75.0, 10.0, 80.0};
        double[] lab2 = {55.0, 10.0, 80.0};
        // Weighted distance: sqrt(0.09*400 + 0 + 0) = sqrt(36) = 6 < 18
        assertTrue(RegionColorStrategy.shouldMerge(lab1, lab2),
            "Bricks with same hue but different lightness should merge");
    }

    @Test
    void shouldMergeFalseForDifferentColors() {
        double[] lab1 = {75.0, 10.0, 80.0};   // yellow
        double[] lab2 = {50.0, -20.0, -50.0};  // blue
        assertFalse(RegionColorStrategy.shouldMerge(lab1, lab2),
            "Bricks with different hues should not merge");
    }

    @Test
    void shouldMergeExactlyAtThreshold() {
        // Exactly at threshold should NOT merge (uses strict <)
        double[] lab1 = {50.0, 0.0, 0.0};
        double[] lab2 = {50.0, RegionColorStrategy.MERGE_THRESHOLD, 0.0};
        assertFalse(RegionColorStrategy.shouldMerge(lab1, lab2),
            "Distance exactly at threshold should not merge");
    }

    // ---- Adjacency graph ----

    @Test
    void adjacencyGraphFindsNeighbors() {
        Brick a = new Brick(0, 0, 0, 1, 1, 1);
        Brick b = new Brick(1, 0, 0, 1, 1, 1);
        Brick c = new Brick(5, 0, 0, 1, 1, 1);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(List.of(a, b, c));

        assertTrue(adj.get(a).contains(b), "a should be adjacent to b");
        assertTrue(adj.get(b).contains(a), "b should be adjacent to a");
        assertFalse(adj.get(a).contains(c), "a should not be adjacent to c");
        assertFalse(adj.get(c).contains(a), "c should not be adjacent to a");
    }

    @Test
    void adjacencyGraphHandlesMultiVoxelBricks() {
        // 2x1 brick at origin and 1x1 brick at (2,0,0) — should be adjacent
        Brick wide = new Brick(0, 0, 0, 2, 1, 1);
        Brick small = new Brick(2, 0, 0, 1, 1, 1);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(List.of(wide, small));

        assertTrue(adj.get(wide).contains(small), "Wide brick should be adjacent to small brick");
        assertTrue(adj.get(small).contains(wide), "Small brick should be adjacent to wide brick");
    }

    @Test
    void adjacencyGraphVerticalNeighbors() {
        Brick bottom = new Brick(0, 0, 0, 1, 1, 1);
        Brick top = new Brick(0, 1, 0, 1, 1, 1);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(List.of(bottom, top));

        assertTrue(adj.get(bottom).contains(top), "Vertically stacked bricks should be adjacent");
    }

    // ---- segmentRegions ----

    @Test
    void segmentationGroupsConnectedSimilarBricks() {
        Brick a = new Brick(0, 0, 0, 1, 1, 1);
        Brick b = new Brick(1, 0, 0, 1, 1, 1);
        Brick c = new Brick(2, 0, 0, 1, 1, 1);
        List<Brick> bricks = List.of(a, b, c);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(bricks);

        Map<Brick, double[]> brickLab = new HashMap<>();
        brickLab.put(a, new double[]{70, 10, 80});
        brickLab.put(b, new double[]{70, 10, 80});
        brickLab.put(c, new double[]{70, 10, 80});

        List<List<Brick>> regions = RegionColorStrategy.segmentRegions(bricks, adj, brickLab);
        assertEquals(1, regions.size(), "All similar adjacent bricks should form one region");
        assertEquals(3, regions.get(0).size());
    }

    @Test
    void segmentationSplitsDissimilarBricks() {
        Brick a = new Brick(0, 0, 0, 1, 1, 1);
        Brick b = new Brick(1, 0, 0, 1, 1, 1);
        Brick c = new Brick(2, 0, 0, 1, 1, 1);
        List<Brick> bricks = List.of(a, b, c);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(bricks);

        Map<Brick, double[]> brickLab = new HashMap<>();
        brickLab.put(a, new double[]{70, 10, 80});    // yellow
        brickLab.put(b, new double[]{70, 10, 80});    // yellow
        brickLab.put(c, new double[]{50, -20, -50});   // blue

        List<List<Brick>> regions = RegionColorStrategy.segmentRegions(bricks, adj, brickLab);
        assertEquals(2, regions.size(), "Dissimilar adjacent bricks should form separate regions");
    }

    @Test
    void segmentationDisconnectedSameColorBricks() {
        Brick a = new Brick(0, 0, 0, 1, 1, 1);
        Brick b = new Brick(10, 0, 0, 1, 1, 1); // same color but disconnected
        List<Brick> bricks = List.of(a, b);

        Map<Brick, List<Brick>> adj = RegionColorStrategy.buildAdjacencyGraph(bricks);

        Map<Brick, double[]> brickLab = new HashMap<>();
        brickLab.put(a, new double[]{70, 10, 80});
        brickLab.put(b, new double[]{70, 10, 80});

        List<List<Brick>> regions = RegionColorStrategy.segmentRegions(bricks, adj, brickLab);
        assertEquals(2, regions.size(), "Disconnected bricks should be in separate regions");
    }

    // ---- applyWithVoxelColors — per-voxel pathway ----

    @Test
    void applyWithVoxelColorsReturnsEmptyForEmptyInput() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, Integer> result = strategy.applyWithVoxelColors(new HashMap<>(), palette);
        assertTrue(result.isEmpty());
    }

    @Test
    void applyWithVoxelColorsReturnsResultForEveryBrick() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                List.of(new ColorRgb(0.8f, 0.7f, 0.0f),
                        new ColorRgb(0.75f, 0.65f, 0.0f)));
        }
        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        assertEquals(10, result.size(), "Should produce a result for every input brick");
    }

    @Test
    void applyWithVoxelColorsLargeRegionGetsUniformColor() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        // 10 adjacent bricks, each with two yellow-ish voxel samples
        for (int i = 0; i < 10; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                List.of(new ColorRgb(0.8f, 0.7f, 0.0f),
                        new ColorRgb(0.6f, 0.5f, 0.0f)));
        }
        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        int firstCode = result.values().iterator().next();
        for (int code : result.values()) {
            assertEquals(firstCode, code, "All bricks in uniform region should get same color");
        }
    }

    @Test
    void applyWithVoxelColorsShadowLiftingImprovesConsistency() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        ColorRgb litYellow = new ColorRgb(0.8f, 0.7f, 0.0f);
        ColorRgb shadowYellow = new ColorRgb(0.15f, 0.12f, 0.0f);
        for (int i = 0; i < 8; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                List.of(litYellow, shadowYellow, litYellow));
        }
        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        int firstCode = result.values().iterator().next();
        long uniformCount = result.values().stream().filter(c -> c == firstCode).count();
        assertEquals(8, uniformCount,
            "Shadow lifting should make all bricks vote for the same region color");
    }

    @Test
    void applyWithVoxelColorsDifferentRegionsGetDifferentColors() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        // 8 yellow bricks
        for (int i = 0; i < 8; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                List.of(new ColorRgb(0.8f, 0.7f, 0.0f)));
        }
        // 8 blue bricks (not adjacent)
        for (int i = 20; i < 28; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                List.of(new ColorRgb(0.0f, 0.0f, 0.8f)));
        }
        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        int yellowCode = result.get(new Brick(0, 0, 0, 1, 1, 1));
        int blueCode = result.get(new Brick(20, 0, 0, 1, 1, 1));
        assertNotEquals(yellowCode, blueCode,
            "Different color regions should get different codes via voxel path");
    }

    @Test
    void applyWithVoxelColorsSmallRegionPreservesPerBrickDetail() {
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        // 3 isolated bricks with very different colors (each forms region < DETAIL_REGION_SIZE)
        input.put(new Brick(0, 0, 0, 1, 1, 1),
            List.of(new ColorRgb(1.0f, 0.0f, 0.0f)));
        input.put(new Brick(10, 0, 0, 1, 1, 1),
            List.of(new ColorRgb(0.0f, 0.0f, 1.0f)));
        input.put(new Brick(20, 0, 0, 1, 1, 1),
            List.of(new ColorRgb(0.0f, 1.0f, 0.0f)));

        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        int red = result.get(new Brick(0, 0, 0, 1, 1, 1));
        int blue = result.get(new Brick(10, 0, 0, 1, 1, 1));
        int green = result.get(new Brick(20, 0, 0, 1, 1, 1));
        assertNotEquals(red, blue, "Detail regions should preserve distinct colors");
        assertNotEquals(red, green, "Detail regions should preserve distinct colors");
    }

    @Test
    void applyWithVoxelColorsFewBricksSkipsShadowLifting() {
        // Fewer than 4 voxel samples → shadow lifting stats return null → still works
        RegionColorStrategy strategy = new RegionColorStrategy();
        Map<Brick, List<ColorRgb>> input = new HashMap<>();
        input.put(new Brick(0, 0, 0, 1, 1, 1),
            List.of(new ColorRgb(1.0f, 1.0f, 1.0f)));
        input.put(new Brick(1, 0, 0, 1, 1, 1),
            List.of(new ColorRgb(0.0f, 0.0f, 0.0f)));

        Map<Brick, Integer> result = strategy.applyWithVoxelColors(input, palette);
        assertEquals(2, result.size(), "Should handle few bricks gracefully");
    }
}
