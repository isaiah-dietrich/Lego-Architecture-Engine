package com.lego.mesh;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import com.lego.model.ColorMath;
import com.lego.model.ColorRgb;

import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;

/**
 * Extracts material properties (baseColorFactor, baseColorTexture) from
 * glTF v2 PBR materials. Separated from {@link GlbLoader} for clarity.
 */
final class GlbMaterialExtractor {

    private GlbMaterialExtractor() {}

    /**
     * Extracts baseColorFactor from a glTF v2 PBR material, or returns null.
     */
    static ColorRgb extractMaterialColor(MaterialModel material) {
        if (material instanceof MaterialModelV2 pbrMaterial) {
            float[] factor = pbrMaterial.getBaseColorFactor();
            if (factor != null && factor.length >= 3) {
                return new ColorRgb(ColorMath.clamp01(factor[0]), ColorMath.clamp01(factor[1]), ColorMath.clamp01(factor[2]));
            }
        }
        return null;
    }

    /**
     * Decodes the baseColorTexture image from a glTF v2 PBR material, or returns null.
     */
    static BufferedImage extractTextureImage(MaterialModel material) {
        if (!(material instanceof MaterialModelV2 pbrMaterial)) {
            return null;
        }
        TextureModel textureModel = pbrMaterial.getBaseColorTexture();
        if (textureModel == null) {
            return null;
        }
        ImageModel imageModel = textureModel.getImageModel();
        if (imageModel == null) {
            return null;
        }
        ByteBuffer imageData = imageModel.getImageData();
        if (imageData == null) {
            return null;
        }
        byte[] bytes = new byte[imageData.remaining()];
        imageData.duplicate().get(bytes);
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

}
