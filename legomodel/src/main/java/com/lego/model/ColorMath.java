package com.lego.model;

/**
 * Shared low-level math for color operations, avoiding duplication across packages.
 */
public final class ColorMath {

    /** Non-instantiable utility class. */
    private ColorMath() {}

    /**
     * sRGB gamma-encoded [0,1] → linear [0,1].
     *
     * Source: W3C sRGB spec — https://www.w3.org/Graphics/Color/sRGB.html
     * Reference: sRGB transfer function — https://en.wikipedia.org/wiki/SRGB#Transfer_function_("gamma")
     */
    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * Clamp a float to [0, 1].
     *
     * Delegates to Math.max / Math.min.
     */
    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
