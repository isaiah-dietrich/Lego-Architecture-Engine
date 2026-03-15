package com.lego.mesh;

import java.awt.image.BufferedImage;

import com.lego.model.ColorMath;
import com.lego.model.ColorRgb;

import de.javagl.jgltf.model.AccessorFloatData;

/**
 * Resolves per-triangle color from vertex colors, texture sampling, and
 * material fallback. Separated from {@link GlbLoader} for clarity.
 */
final class GlbTriangleColorResolver {

    private GlbTriangleColorResolver() {}

    /**
     * Determines the color for a triangle. Priority:
     * <ol>
     *   <li>COLOR_0 vertex attribute (average of 3 vertex colors)</li>
     *   <li>baseColorTexture multi-sampled across the triangle's UV footprint
     *       (&times; baseColorFactor if set)</li>
     *   <li>baseColorFactor alone</li>
     *   <li>null (no color)</li>
     * </ol>
     */
    static ColorRgb resolveTriangleColor(
        AccessorFloatData colors,
        int colorComponents,
        int i0, int i1, int i2,
        AccessorFloatData texCoords,
        BufferedImage textureImage,
        ColorRgb materialColor
    ) {
        if (colors != null && colorComponents >= 3) {
            float r = (colors.get(i0, 0) + colors.get(i1, 0) + colors.get(i2, 0)) / 3f;
            float g = (colors.get(i0, 1) + colors.get(i1, 1) + colors.get(i2, 1)) / 3f;
            float b = (colors.get(i0, 2) + colors.get(i1, 2) + colors.get(i2, 2)) / 3f;
            return new ColorRgb(ColorMath.clamp01(r), ColorMath.clamp01(g), ColorMath.clamp01(b));
        }

        if (textureImage != null && texCoords != null) {
            float u0 = texCoords.get(i0, 0), v0 = texCoords.get(i0, 1);
            float u1 = texCoords.get(i1, 0), v1 = texCoords.get(i1, 1);
            float u2 = texCoords.get(i2, 0), v2 = texCoords.get(i2, 1);

            // Multi-sample: 3 vertices + centroid + 3 edge midpoints = 7 samples.
            float rSum = 0, gSum = 0, bSum = 0;
            int validCount = 0;

            ColorRgb s;
            s = GlbTextureSampler.sampleTextureFiltered(textureImage, u0, v0);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = GlbTextureSampler.sampleTextureFiltered(textureImage, u1, v1);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = GlbTextureSampler.sampleTextureFiltered(textureImage, u2, v2);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            s = GlbTextureSampler.sampleTextureFiltered(textureImage,
                (u0 + u1 + u2) / 3f, (v0 + v1 + v2) / 3f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            s = GlbTextureSampler.sampleTextureFiltered(textureImage,
                (u0 + u1) / 2f, (v0 + v1) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = GlbTextureSampler.sampleTextureFiltered(textureImage,
                (u1 + u2) / 2f, (v1 + v2) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = GlbTextureSampler.sampleTextureFiltered(textureImage,
                (u0 + u2) / 2f, (v0 + v2) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            if (validCount > 0) {
                ColorRgb texColor = new ColorRgb(
                    rSum / validCount, gSum / validCount, bSum / validCount);
                if (materialColor != null) {
                    return new ColorRgb(
                        ColorMath.clamp01(texColor.r() * materialColor.r()),
                        ColorMath.clamp01(texColor.g() * materialColor.g()),
                        ColorMath.clamp01(texColor.b() * materialColor.b())
                    );
                }
                return texColor;
            }
        }

        return materialColor; // may be null
    }

}
