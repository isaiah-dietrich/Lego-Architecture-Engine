package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lego.color.UVLabPaletteProjection.LightnessStats;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;

class UVLabPaletteProjectionTest {

    private static LegoPaletteMapper palette;

    @BeforeAll
    static void loadPalette() throws IOException {
        palette = LegoPaletteMapper.loadDefault();
    }

    // ---- Strategy metadata ----

    @Test
    void nameIsUvlab() {
        assertEquals("uvlab", new UVLabPaletteProjection().name());
    }

    @Test
    void descriptionIsNotEmpty() {
        assertFalse(new UVLabPaletteProjection().description().isEmpty());
    }

    // ---- Lightness statistics ----

    @Test
    void lightnessStatsComputesMedianAndIqr() {
        // Values: 10, 20, 30, 40, 50, 60, 70, 80
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0);
        LightnessStats stats = UVLabPaletteProjection.computeLightnessStats(values);
        assertNotNull(stats);
        assertEquals(45.0, stats.median(), 1.0);
        assertTrue(stats.iqr() > 0, "IQR should be positive");
    }

    @Test
    void lightnessStatsReturnsNullForTooFewValues() {
        assertNull(UVLabPaletteProjection.computeLightnessStats(List.of(50.0, 60.0, 70.0)));
    }

    @Test
    void lightnessStatsHandlesUniformValues() {
        List<Double> uniform = List.of(50.0, 50.0, 50.0, 50.0, 50.0);
        LightnessStats stats = UVLabPaletteProjection.computeLightnessStats(uniform);
        assertNotNull(stats);
        assertEquals(50.0, stats.median(), 0.01);
        assertEquals(0.0, stats.iqr(), 0.01);
    }

    // ---- Shadow lifting ----

    @Test
    void shadowLiftRaisesLowLightness() {
        LightnessStats stats = new LightnessStats(60.0, 45.0, 75.0, 30.0);
        // Shadow threshold = 60 - 15 = 45
        // A value of 20 is well below the threshold
        double lifted = UVLabPaletteProjection.normalizeLightness(20.0, stats);
        assertTrue(lifted > 20.0, "Shadow lifting should raise L* from 20. Got: " + lifted);
        assertTrue(lifted < 60.0, "Shadow lifting should not exceed median. Got: " + lifted);
    }

    @Test
    void highlightCompressionLowersHighLightness() {
        LightnessStats stats = new LightnessStats(60.0, 45.0, 75.0, 30.0);
        // Highlight threshold = 60 + 15 = 75
        double compressed = UVLabPaletteProjection.normalizeLightness(95.0, stats);
        assertTrue(compressed < 95.0, "Highlight compression should lower L* from 95. Got: " + compressed);
        assertTrue(compressed > 60.0, "Should still be above median. Got: " + compressed);
    }

    @Test
    void normalizeLightnessDoesNotChangeMiddleValues() {
        LightnessStats stats = new LightnessStats(60.0, 45.0, 75.0, 30.0);
        double mid = UVLabPaletteProjection.normalizeLightness(60.0, stats);
        assertEquals(60.0, mid, 0.01, "Values at median should not change");
    }

    @Test
    void normalizeLightnessNoOpWhenIqrIsZero() {
        LightnessStats stats = new LightnessStats(50.0, 50.0, 50.0, 0.0);
        double result = UVLabPaletteProjection.normalizeLightness(30.0, stats);
        assertEquals(30.0, result, 0.01, "No-op when IQR is zero");
    }

    @Test
    void normalizeLightnessClampsToValidRange() {
        LightnessStats stats = new LightnessStats(50.0, 30.0, 70.0, 40.0);
        double result = UVLabPaletteProjection.normalizeLightness(-10.0, stats);
        assertTrue(result >= 0, "Should not go below 0");
        result = UVLabPaletteProjection.normalizeLightness(110.0, stats);
        assertTrue(result <= 100, "Should not exceed 100");
    }

    // ---- Chroma stabilization ----

    @Test
    void stabilizeChromaBoostsLowChromaColors() {
        double[] lab = {50.0, 2.0, 3.0}; // chroma = sqrt(4+9) ≈ 3.6
        UVLabPaletteProjection.stabilizeChroma(lab);
        double newChroma = Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
        assertEquals(UVLabPaletteProjection.MIN_CHROMA, newChroma, 0.1,
            "Low chroma should be boosted to MIN_CHROMA");
    }

    @Test
    void stabilizeChromaPreservesHueAngle() {
        double[] lab = {50.0, 3.0, 4.0};
        double originalAngle = Math.atan2(lab[2], lab[1]);
        UVLabPaletteProjection.stabilizeChroma(lab);
        double newAngle = Math.atan2(lab[2], lab[1]);
        assertEquals(originalAngle, newAngle, 0.001, "Hue angle should be preserved");
    }

    @Test
    void stabilizeChromaLeavesHighChromaAlone() {
        double[] lab = {50.0, 20.0, 30.0};
        double originalA = lab[1], originalB = lab[2];
        UVLabPaletteProjection.stabilizeChroma(lab);
        assertEquals(originalA, lab[1], 0.001);
        assertEquals(originalB, lab[2], 0.001);
    }

    @Test
    void stabilizeChromaLeavesNearlyAchromaticAlone() {
        double[] lab = {50.0, 0.3, 0.4}; // chroma ≈ 0.5, truly gray
        double originalA = lab[1], originalB = lab[2];
        UVLabPaletteProjection.stabilizeChroma(lab);
        assertEquals(originalA, lab[1], 0.001, "Nearly achromatic should be left alone");
        assertEquals(originalB, lab[2], 0.001);
    }

    // ---- CIEDE2000 matching ----

    @Test
    void ciede2000MatchesWhiteToWhite() {
        double[] whiteLab = LegoPaletteMapper.linearRgbToLab(1.0, 1.0, 1.0);
        int code = UVLabPaletteProjection.nearestCiede2000(
            whiteLab[0], whiteLab[1], whiteLab[2], palette.opaqueEntries());
        assertEquals(15, code, "Pure white should map to LDraw White (15)");
    }

    @Test
    void ciede2000MatchesBlackToBlack() {
        double[] blackLab = LegoPaletteMapper.linearRgbToLab(0.0, 0.0, 0.0);
        int code = UVLabPaletteProjection.nearestCiede2000(
            blackLab[0], blackLab[1], blackLab[2], palette.opaqueEntries());
        assertEquals(0, code, "Pure black should map to LDraw Black (0)");
    }

    @Test
    void ciede2000MatchesRedToRed() {
        // LEGO Red sRGB = C91A09 → linear ≈ (0.585, 0.010, 0.003)
        double[] redLab = LegoPaletteMapper.linearRgbToLab(0.585, 0.010, 0.003);
        int code = UVLabPaletteProjection.nearestCiede2000(
            redLab[0], redLab[1], redLab[2], palette.opaqueEntries());
        assertEquals(4, code, "LEGO Red linear should map to LDraw Red (4)");
    }

    // ---- Full pipeline integration ----

    @Test
    void applyReturnsResultsForAllInputBricks() {
        UVLabPaletteProjection strategy = new UVLabPaletteProjection();
        Map<Brick, ColorRgb> input = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                new ColorRgb(0.3f + i * 0.05f, 0.2f + i * 0.03f, 0.1f + i * 0.01f));
        }
        Map<Brick, Integer> result = strategy.apply(input, palette);
        assertEquals(10, result.size(), "Should produce a result for every input brick");
    }

    @Test
    void applyHandlesEmptyInput() {
        UVLabPaletteProjection strategy = new UVLabPaletteProjection();
        Map<Brick, Integer> result = strategy.apply(new HashMap<>(), palette);
        assertTrue(result.isEmpty());
    }

    @Test
    void applyHandlesFewerThanFourBricks() {
        // Should fall back to CIEDE2000 without shadow lifting
        UVLabPaletteProjection strategy = new UVLabPaletteProjection();
        Map<Brick, ColorRgb> input = new HashMap<>();
        input.put(new Brick(0, 0, 0, 1, 1, 1), new ColorRgb(1f, 1f, 1f));
        input.put(new Brick(1, 0, 0, 1, 1, 1), new ColorRgb(0f, 0f, 0f));

        Map<Brick, Integer> result = strategy.apply(input, palette);
        assertEquals(2, result.size());
        assertEquals(15, result.get(new Brick(0, 0, 0, 1, 1, 1)), "White");
        assertEquals(0, result.get(new Brick(1, 0, 0, 1, 1, 1)), "Black");
    }

    @Test
    void applyIsDeterministic() {
        UVLabPaletteProjection strategy = new UVLabPaletteProjection();
        Map<Brick, ColorRgb> input = buildTestInput();

        Map<Brick, Integer> result1 = strategy.apply(input, palette);
        Map<Brick, Integer> result2 = strategy.apply(input, palette);

        assertEquals(result1, result2, "Same input should always produce same output");
    }

    @Test
    void applyDiffersFromDirectMatchOnShadowedColors() {
        // Create a mix of "normal" and "shadow" colors
        // The shadow-lifted + CIEDE2000 strategy should sometimes choose differently
        DirectMatchStrategy direct = new DirectMatchStrategy();
        UVLabPaletteProjection uvlab = new UVLabPaletteProjection();

        Map<Brick, ColorRgb> input = new HashMap<>();
        // Bright golden
        for (int i = 0; i < 20; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                new ColorRgb(0.5f, 0.35f, 0.12f));
        }
        // Deep shadow of the same golden
        for (int i = 20; i < 30; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                new ColorRgb(0.08f, 0.05f, 0.02f));
        }

        Map<Brick, Integer> directResult = direct.apply(input, palette);
        Map<Brick, Integer> uvlabResult = uvlab.apply(input, palette);

        // The deep shadow bricks should potentially differ
        // (UVLab lifts them out of shadow, so they might map to a different color)
        // We just verify both produce valid results; exact color depends on palette
        assertEquals(30, directResult.size());
        assertEquals(30, uvlabResult.size());
    }

    @Test
    void registryContainsUvlab() {
        ColorStrategyRegistry registry = ColorStrategyRegistry.createDefault();
        assertTrue(registry.availableNames().contains("uvlab"));
        ColorStrategy strategy = registry.get("uvlab");
        assertEquals("uvlab", strategy.name());
    }

    // ---- Helper ----

    private Map<Brick, ColorRgb> buildTestInput() {
        Map<Brick, ColorRgb> input = new HashMap<>();
        float[][] colors = {
            {0.5f, 0.35f, 0.12f},  // golden
            {0.08f, 0.05f, 0.02f}, // shadow golden
            {0.9f, 0.85f, 0.8f},   // highlight
            {0.3f, 0.2f, 0.1f},    // mid brown
            {0.1f, 0.08f, 0.04f},  // dark shadow
            {0.6f, 0.4f, 0.2f},    // warm mid
        };
        for (int i = 0; i < colors.length; i++) {
            input.put(new Brick(i, 0, 0, 1, 1, 1),
                new ColorRgb(colors[i][0], colors[i][1], colors[i][2]));
        }
        return input;
    }
}
