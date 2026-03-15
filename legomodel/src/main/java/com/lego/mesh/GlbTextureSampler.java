package com.lego.mesh;

import java.awt.image.BufferedImage;

import com.lego.model.ColorMath;
import com.lego.model.ColorRgb;

/**
 * Samples textures at UV coordinates, handling sRGB→linear conversion,
 * UV wrapping, and UV-padding detection. Separated from {@link GlbLoader}.
 */
final class GlbTextureSampler {

    /**
     * sRGB channel threshold for UV-padding detection. Pixels with all channels
     * at or below this value (out of 255) are treated as texture atlas padding
     * and excluded from color sampling.
     */
    private static final int UV_PADDING_SRGB_THRESHOLD = 10;

    private GlbTextureSampler() {}

    /**
     * Samples a texture at the given UV coordinate, converting from sRGB to linear RGB.
     * UV coordinates are wrapped to [0,1] (glTF default repeat mode).
     */
    static ColorRgb sampleTexture(BufferedImage image, float u, float v) {
        u = wrapUv(u);
        v = wrapUv(v);

        int x = Math.min((int) (u * image.getWidth()), image.getWidth() - 1);
        int y = uvToPixelY(v, image.getHeight());

        int argb = image.getRGB(x, y);
        float sR = ((argb >> 16) & 0xFF) / 255f;
        float sG = ((argb >> 8) & 0xFF) / 255f;
        float sB = (argb & 0xFF) / 255f;

        return new ColorRgb(
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sR)),
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sG)),
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sB))
        );
    }

    /**
     * Samples a texture at the given UV coordinate, returning null if the pixel
     * is likely UV-atlas padding (all sRGB channels &le; threshold).
     * Non-padding pixels are converted from sRGB to linear RGB.
     */
    static ColorRgb sampleTextureFiltered(BufferedImage image, float u, float v) {
        u = wrapUv(u);
        v = wrapUv(v);

        int x = Math.min((int) (u * image.getWidth()), image.getWidth() - 1);
        int y = uvToPixelY(v, image.getHeight());

        int argb = image.getRGB(x, y);
        int sR = (argb >> 16) & 0xFF;
        int sG = (argb >> 8) & 0xFF;
        int sB = argb & 0xFF;

        if (sR <= UV_PADDING_SRGB_THRESHOLD
                && sG <= UV_PADDING_SRGB_THRESHOLD
                && sB <= UV_PADDING_SRGB_THRESHOLD) {
            return null; // likely UV-atlas padding
        }

        return new ColorRgb(
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sR / 255f)),
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sG / 255f)),
            ColorMath.clamp01((float) ColorMath.srgbToLinear(sB / 255f))
        );
    }

    /** Wraps UV coordinate into [0,1). */
    static float wrapUv(float uv) {
        return uv - (float) Math.floor(uv);
    }

    /**
     * Converts glTF V (origin at bottom) to image Y (origin at top).
     */
    static int uvToPixelY(float v, int imageHeight) {
        float flippedV = 1f - v;
        return Math.min((int) (flippedV * imageHeight), imageHeight - 1);
    }

}
