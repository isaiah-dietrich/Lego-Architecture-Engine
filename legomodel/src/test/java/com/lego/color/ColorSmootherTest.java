package com.lego.color;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;

class ColorSmootherTest {

    // LDraw color codes
    private static final int BLACK = 0;   // 05131D
    private static final int YELLOW = 14; // F2CD37
    private static final int WHITE = 15;  // FFFFFF
    private static final int TAN = 19;    // E4CD9E (or similar)
    private static final int MAGENTA = 26; // 923978

    private static LegoPaletteMapper palette;

    @BeforeAll
    static void loadPalette() throws IOException {
        palette = LegoPaletteMapper.loadDefault();
    }

    // ---- Contrast guard: high-contrast features are preserved ----

    @Test
    void smoothPreservesHighContrastOutlier() {
        // A single black brick (eye) surrounded by yellow bricks (face)
        // Black-to-yellow ΔE is huge (>80) → contrast guard should protect it
        Brick eye = new Brick(2, 2, 0, 1, 1, 1);
        Brick n1 = new Brick(1, 2, 0, 1, 1, 1);
        Brick n2 = new Brick(3, 2, 0, 1, 1, 1);
        Brick n3 = new Brick(2, 1, 0, 1, 1, 1);
        Brick n4 = new Brick(2, 3, 0, 1, 1, 1);

        List<Brick> bricks = List.of(eye, n1, n2, n3, n4);
        Map<Brick, Integer> colors = new HashMap<>();
        colors.put(eye, BLACK);
        colors.put(n1, YELLOW);
        colors.put(n2, YELLOW);
        colors.put(n3, YELLOW);
        colors.put(n4, YELLOW);

        int changed = ColorSmoother.smooth(colors, bricks, palette);

        assertEquals(0, changed, "High-contrast outlier (eye) should NOT be smoothed");
        assertEquals(BLACK, colors.get(eye), "Eye brick should remain black");
    }

    @Test
    void smoothRemovesLowContrastOutlier() {
        // A single tan brick surrounded by yellow bricks — low contrast noise
        // Tan-to-yellow ΔE is small (<30) → should be smoothed
        Brick outlier = new Brick(2, 2, 0, 1, 1, 1);
        Brick n1 = new Brick(1, 2, 0, 1, 1, 1);
        Brick n2 = new Brick(3, 2, 0, 1, 1, 1);
        Brick n3 = new Brick(2, 1, 0, 1, 1, 1);
        Brick n4 = new Brick(2, 3, 0, 1, 1, 1);

        // Find two colors with ΔE < CONTRAST_GUARD_DELTA_E
        // Yellow (14) and Bright Light Yellow (226) or similar close pair
        // Use tan (19) vs yellow (14) — verify they're close enough
        double[] yellowLab = palette.labForCode(YELLOW);
        double[] tanLab = palette.labForCode(TAN);
        assertNotNull(yellowLab, "Yellow should be in palette");
        assertNotNull(tanLab, "Tan should be in palette");
        double de = LegoPaletteMapper.deltaE(yellowLab[0], yellowLab[1], yellowLab[2],
                                             tanLab[0], tanLab[1], tanLab[2]);
        assertTrue(de < ColorSmoother.CONTRAST_GUARD_DELTA_E,
            "Tan-Yellow ΔE=" + de + " should be < " + ColorSmoother.CONTRAST_GUARD_DELTA_E
                + " for this test to be valid");

        List<Brick> bricks = List.of(outlier, n1, n2, n3, n4);
        Map<Brick, Integer> colors = new HashMap<>();
        colors.put(outlier, TAN);
        colors.put(n1, YELLOW);
        colors.put(n2, YELLOW);
        colors.put(n3, YELLOW);
        colors.put(n4, YELLOW);

        int changed = ColorSmoother.smooth(colors, bricks, palette);

        assertEquals(1, changed, "Low-contrast outlier should be smoothed");
        assertEquals(YELLOW, colors.get(outlier), "Outlier should be recolored to yellow");
    }

    @Test
    void smoothIterativePreservesBlackEyesOnYellowFace() {
        // Build a 5×5 yellow face with 2 black eyes at (1,3) and (3,3)
        List<Brick> bricks = new java.util.ArrayList<>();
        Map<Brick, Integer> colors = new HashMap<>();

        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                Brick b = new Brick(x, y, 0, 1, 1, 1);
                bricks.add(b);
                boolean isEye = (x == 1 && y == 3) || (x == 3 && y == 3);
                colors.put(b, isEye ? BLACK : YELLOW);
            }
        }

        ColorSmoother.smoothIterative(colors, bricks, 3, palette);

        // Both eyes must survive all passes
        for (Brick b : bricks) {
            if ((b.x() == 1 && b.y() == 3) || (b.x() == 3 && b.y() == 3)) {
                assertEquals(BLACK, colors.get(b),
                    "Eye at (" + b.x() + "," + b.y() + ") must remain black after full smoothing");
            }
        }
    }

    // ---- Rare-color smoothing with contrast guard ----

    @Test
    void smoothRareColorsPreservesHighContrastRareColor() {
        // 50 yellow bricks + 1 black brick (< 2% threshold) — black is "rare"
        // but black-to-yellow ΔE is huge → contrast guard should protect it
        List<Brick> bricks = new java.util.ArrayList<>();
        Map<Brick, Integer> colors = new HashMap<>();

        // Line of 50 yellow bricks
        for (int x = 0; x < 50; x++) {
            Brick b = new Brick(x, 0, 0, 1, 1, 1);
            bricks.add(b);
            colors.put(b, YELLOW);
        }
        // One black brick adjacent to the line
        Brick blackBrick = new Brick(25, 1, 0, 1, 1, 1);
        bricks.add(blackBrick);
        colors.put(blackBrick, BLACK);

        int changed = ColorSmoother.smoothRareColors(colors, bricks, 0.02, 3, palette);

        assertEquals(0, changed, "High-contrast rare color (black) should NOT be smoothed");
        assertEquals(BLACK, colors.get(blackBrick));
    }

    @Test
    void smoothRareColorsRemovesLowContrastRareColor() {
        // 50 yellow bricks + 1 tan brick (< 2% threshold) — tan is "rare"
        // tan-to-yellow ΔE is low → should be smoothed away
        List<Brick> bricks = new java.util.ArrayList<>();
        Map<Brick, Integer> colors = new HashMap<>();

        for (int x = 0; x < 50; x++) {
            Brick b = new Brick(x, 0, 0, 1, 1, 1);
            bricks.add(b);
            colors.put(b, YELLOW);
        }
        Brick tanBrick = new Brick(25, 1, 0, 1, 1, 1);
        bricks.add(tanBrick);
        colors.put(tanBrick, TAN);

        int changed = ColorSmoother.smoothRareColors(colors, bricks, 0.02, 3, palette);

        assertEquals(1, changed, "Low-contrast rare color should be smoothed");
        assertEquals(YELLOW, colors.get(tanBrick));
    }

    // ---- Chromatic outliers: high-contrast but wrong-hue → smoothed ----

    @Test
    void smoothRemovesChromaticHighContrastOutlier() {
        // A single magenta brick surrounded by yellow bricks
        // Magenta-to-yellow ΔE is huge, but magenta is chromatic (not achromatic)
        // → should be smoothed away as wrong-hue noise
        Brick outlier = new Brick(2, 2, 0, 1, 1, 1);
        Brick n1 = new Brick(1, 2, 0, 1, 1, 1);
        Brick n2 = new Brick(3, 2, 0, 1, 1, 1);
        Brick n3 = new Brick(2, 1, 0, 1, 1, 1);
        Brick n4 = new Brick(2, 3, 0, 1, 1, 1);

        List<Brick> bricks = List.of(outlier, n1, n2, n3, n4);
        Map<Brick, Integer> colors = new HashMap<>();
        colors.put(outlier, MAGENTA);
        colors.put(n1, YELLOW);
        colors.put(n2, YELLOW);
        colors.put(n3, YELLOW);
        colors.put(n4, YELLOW);

        int changed = ColorSmoother.smooth(colors, bricks, palette);

        assertEquals(1, changed, "Chromatic high-contrast outlier (magenta) SHOULD be smoothed");
        assertEquals(YELLOW, colors.get(outlier));
    }

    @Test
    void smoothRareColorsRemovesChromaticHighContrastRareColor() {
        // 50 yellow bricks + 1 magenta brick (< 2% threshold)
        // Magenta is chromatic → should be smoothed despite high ΔE
        List<Brick> bricks = new java.util.ArrayList<>();
        Map<Brick, Integer> colors = new HashMap<>();

        for (int x = 0; x < 50; x++) {
            Brick b = new Brick(x, 0, 0, 1, 1, 1);
            bricks.add(b);
            colors.put(b, YELLOW);
        }
        Brick magentaBrick = new Brick(25, 1, 0, 1, 1, 1);
        bricks.add(magentaBrick);
        colors.put(magentaBrick, MAGENTA);

        int changed = ColorSmoother.smoothRareColors(colors, bricks, 0.02, 3, palette);

        assertEquals(1, changed, "Chromatic rare color (magenta) should be smoothed");
        assertEquals(YELLOW, colors.get(magentaBrick));
    }
}
