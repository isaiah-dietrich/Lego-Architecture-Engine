package com.lego.model;

/**
 * Immutable linear RGB color with components in [0, 1].
 *
 * <p>Used as a side-channel color annotation for triangles loaded from GLB inputs.
 * {@code Triangle}, {@code Mesh}, and the voxelizer pipeline do not reference this
 * type; color data flows in a separate {@code Map<Triangle, ColorRgb>} alongside
 * geometry without modifying any existing record types.
 */
public record ColorRgb(float r, float g, float b) {

    public ColorRgb {
        if (r < 0f || r > 1f) throw new IllegalArgumentException("r out of [0,1]: " + r);
        if (g < 0f || g > 1f) throw new IllegalArgumentException("g out of [0,1]: " + g);
        if (b < 0f || b > 1f) throw new IllegalArgumentException("b out of [0,1]: " + b);
    }

    /**
     * Parses a 6-character hex string (e.g. {@code "FF8014"}) into a {@code ColorRgb}.
     * Characters must be uppercase or lowercase hex digits; no leading {@code #}.
     */
    public static ColorRgb fromHex(String hex) {
        if (hex == null || hex.length() != 6) {
            throw new IllegalArgumentException("Expected 6-char hex string, got: " + hex);
        }
        int r8 = Integer.parseInt(hex.substring(0, 2), 16);
        int g8 = Integer.parseInt(hex.substring(2, 4), 16);
        int b8 = Integer.parseInt(hex.substring(4, 6), 16);
        return new ColorRgb(r8 / 255f, g8 / 255f, b8 / 255f);
    }
}
