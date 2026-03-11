package com.lego.mesh;

import java.awt.image.BufferedImage;

import com.lego.model.ColorRgb;

/**
 * Per-triangle texture and material data for direct texture sampling.
 *
 * <p>Stored parallel to the mesh triangle list (same index correspondence).
 * Each instance carries everything needed to sample a color at any point
 * on the triangle surface: per-vertex UV coordinates, the texture image,
 * per-vertex colors, and the material base-color factor.
 *
 * <p>Color priority matches glTF convention:
 * <ol>
 *   <li>Per-vertex colors (interpolated via barycentrics)</li>
 *   <li>Texture sampled at interpolated UV × baseColorFactor</li>
 *   <li>baseColorFactor alone</li>
 * </ol>
 */
public record TexturedTriangle(
    float u0, float v0,
    float u1, float v1,
    float u2, float v2,
    BufferedImage texture,
    ColorRgb vc0, ColorRgb vc1, ColorRgb vc2,
    ColorRgb materialColor
) {

    /**
     * Interpolates UV coordinates using barycentric weights.
     *
     * @param w0 weight for vertex 0
     * @param w1 weight for vertex 1
     * @param w2 weight for vertex 2
     * @return float[2] = {u, v}
     */
    public float[] interpolateUV(double w0, double w1, double w2) {
        return new float[] {
            (float) (u0 * w0 + u1 * w1 + u2 * w2),
            (float) (v0 * w0 + v1 * w1 + v2 * w2)
        };
    }

    /**
     * Interpolates per-vertex colors using barycentric weights.
     *
     * @return interpolated linear RGB, or null if no vertex colors
     */
    public ColorRgb interpolateVertexColor(double w0, double w1, double w2) {
        if (vc0 == null) return null;
        float r = (float) (vc0.r() * w0 + vc1.r() * w1 + vc2.r() * w2);
        float g = (float) (vc0.g() * w0 + vc1.g() * w1 + vc2.g() * w2);
        float b = (float) (vc0.b() * w0 + vc1.b() * w1 + vc2.b() * w2);
        return new ColorRgb(
            Math.max(0, Math.min(1, r)),
            Math.max(0, Math.min(1, g)),
            Math.max(0, Math.min(1, b))
        );
    }

    /** Returns true if this triangle has a texture to sample. */
    public boolean hasTexture() {
        return texture != null;
    }

    /** Returns true if this triangle has per-vertex colors. */
    public boolean hasVertexColors() {
        return vc0 != null;
    }
}
