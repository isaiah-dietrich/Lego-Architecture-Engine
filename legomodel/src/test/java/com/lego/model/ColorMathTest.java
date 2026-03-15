package com.lego.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ColorMathTest {

    @Test
    void srgbToLinearZeroReturnsZero() {
        assertEquals(0.0, ColorMath.srgbToLinear(0.0), 1e-12);
    }

    @Test
    void srgbToLinearOneReturnsOne() {
        assertEquals(1.0, ColorMath.srgbToLinear(1.0), 1e-12);
    }

    @Test
    void srgbToLinearBelowThresholdIsLinear() {
        // Below 0.04045, formula is c / 12.92
        double input = 0.04;
        double expected = 0.04 / 12.92;
        assertEquals(expected, ColorMath.srgbToLinear(input), 1e-12);
    }

    @Test
    void srgbToLinearAtThresholdBoundary() {
        // At exactly 0.04045 → linear branch
        double linear = ColorMath.srgbToLinear(0.04045);
        assertEquals(0.04045 / 12.92, linear, 1e-12);
    }

    @Test
    void srgbToLinearAboveThresholdUsesGamma() {
        // Above 0.04045, formula is ((c + 0.055) / 1.055)^2.4
        double input = 0.5;
        double expected = Math.pow((0.5 + 0.055) / 1.055, 2.4);
        assertEquals(expected, ColorMath.srgbToLinear(input), 1e-12);
    }

    @Test
    void srgbToLinearMidGrayIsLessThanHalf() {
        // sRGB 0.5 → linear ~0.214 (gamma correction makes it darker)
        double result = ColorMath.srgbToLinear(0.5);
        assertTrue(result < 0.5, "Linear value should be less than sRGB value");
        assertEquals(0.214, result, 0.001);
    }

    @Test
    void clamp01PassthroughInRange() {
        assertEquals(0.5f, ColorMath.clamp01(0.5f));
        assertEquals(0.0f, ColorMath.clamp01(0.0f));
        assertEquals(1.0f, ColorMath.clamp01(1.0f));
    }

    @Test
    void clamp01ClampsNegativeToZero() {
        assertEquals(0.0f, ColorMath.clamp01(-0.5f));
        assertEquals(0.0f, ColorMath.clamp01(-100f));
    }

    @Test
    void clamp01ClampsAboveOneToOne() {
        assertEquals(1.0f, ColorMath.clamp01(1.5f));
        assertEquals(1.0f, ColorMath.clamp01(100f));
    }
}
