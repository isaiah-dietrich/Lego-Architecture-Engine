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

    @Test
    void paletteAwareVarianceIgnoresShadingThatQuantizesToSameLegoColor() throws Exception {
        LegoPaletteMapper palette = LegoPaletteMapper.loadDefault();
        ColorRgb[] pair = findSamePaletteHighDeltaPair(palette);
        assertNotNull(pair, "Expected to find two shades that quantize to the same LEGO color");

        ColorRgb[][][] colors = new ColorRgb[2][1][1];
        colors[0][0][0] = pair[0];
        colors[1][0][0] = pair[1];

        PlacementFeatureGrid raw = ColorFeatureGridFactory.create(colors);
        PlacementFeatureGrid paletteAware = ColorFeatureGridFactory.create(colors, palette);

        assertTrue(raw.isHighVariance(0, 0, 0) || raw.isHighVariance(1, 0, 0),
            "Raw-Lab variance should detect this high-contrast shade change");
        assertFalse(paletteAware.isHighVariance(0, 0, 0),
            "Palette-aware variance should not split shades that map to the same LEGO color");
        assertFalse(paletteAware.isHighVariance(1, 0, 0),
            "Palette-aware variance should not split shades that map to the same LEGO color");
    }

    private static ColorRgb[] findSamePaletteHighDeltaPair(LegoPaletteMapper palette) {
        for (double r = 0.15; r <= 0.95; r += 0.20) {
            for (double g = 0.15; g <= 0.95; g += 0.20) {
                for (double b = 0.15; b <= 0.95; b += 0.20) {
                    for (double s1 = 0.45; s1 <= 1.0; s1 += 0.05) {
                        for (double s2 = s1 + 0.10; s2 <= 1.0; s2 += 0.05) {
                            ColorRgb c1 = scaled(r, g, b, s1);
                            ColorRgb c2 = scaled(r, g, b, s2);
                            if (palette.nearestLDrawColor(c1) != palette.nearestLDrawColor(c2)) {
                                continue;
                            }
                            if (deltaE76(c1, c2) <= 12.0) {
                                continue;
                            }
                            return new ColorRgb[] { c1, c2 };
                        }
                    }
                }
            }
        }
        return null;
    }

    private static ColorRgb scaled(double r, double g, double b, double scale) {
        return new ColorRgb((float) Math.min(1.0, r * scale),
                            (float) Math.min(1.0, g * scale),
                            (float) Math.min(1.0, b * scale));
    }

    private static double deltaE76(ColorRgb a, ColorRgb b) {
        double[] labA = LegoPaletteMapper.linearRgbToLab(a.r(), a.g(), a.b());
        double[] labB = LegoPaletteMapper.linearRgbToLab(b.r(), b.g(), b.b());
        double dl = labA[0] - labB[0];
        double da = labA[1] - labB[1];
        double db = labA[2] - labB[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }
}
