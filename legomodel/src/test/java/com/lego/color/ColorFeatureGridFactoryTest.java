package com.lego.color;

import com.lego.model.ColorRgb;
import com.lego.optimize.PlacementFeatureGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorFeatureGridFactoryTest {

    @Test
    void uniformColorsProduceNoHighVariance() {
        // 3×3×3 grid of identical colors → no variance
        ColorRgb[][][] colors = new ColorRgb[3][3][3];
        ColorRgb same = new ColorRgb(0.5f, 0.5f, 0.5f);
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++)
                    colors[x][y][z] = same;

        PlacementFeatureGrid grid = ColorFeatureGridFactory.create(colors);

        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++)
                    assertFalse(grid.isHighVariance(x, y, z),
                        "Uniform colors should not be high variance at (%d,%d,%d)".formatted(x, y, z));
    }

    @Test
    void veryDifferentAdjacentColorsProduceHighVariance() {
        // Center voxel surrounded by very different colors
        ColorRgb[][][] colors = new ColorRgb[3][3][3];
        ColorRgb dark = new ColorRgb(0.0f, 0.0f, 0.0f);
        ColorRgb bright = new ColorRgb(1.0f, 1.0f, 1.0f);

        // Fill everything dark except center
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++)
                    colors[x][y][z] = dark;
        colors[1][1][1] = bright;

        PlacementFeatureGrid grid = ColorFeatureGridFactory.create(colors);

        // Center should be high variance — all 6 neighbors differ dramatically
        assertTrue(grid.isHighVariance(1, 1, 1),
            "Center voxel with all different neighbors should be high variance");
    }

    @Test
    void nullEntriesAreHandledGracefully() {
        // Grid with some null entries (empty voxels)
        ColorRgb[][][] colors = new ColorRgb[2][2][2];
        colors[0][0][0] = new ColorRgb(0.5f, 0.5f, 0.5f);
        // All others null

        PlacementFeatureGrid grid = ColorFeatureGridFactory.create(colors);

        assertNotNull(grid);
        assertTrue(grid.hasColorData());
        // Null voxels should not be high variance
        assertFalse(grid.isHighVariance(1, 1, 1));
    }

    @Test
    void gridDimensionsMatchInput() {
        ColorRgb[][][] colors = new ColorRgb[4][5][6];
        PlacementFeatureGrid grid = ColorFeatureGridFactory.create(colors);

        assertEquals(4, grid.width());
        assertEquals(5, grid.height());
        assertEquals(6, grid.depth());
    }
}
