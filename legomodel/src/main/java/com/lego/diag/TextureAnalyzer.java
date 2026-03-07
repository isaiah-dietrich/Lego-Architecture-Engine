package com.lego.diag;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import de.javagl.jgltf.model.io.GltfModelReader;

/**
 * Diagnostic tool: loads a GLB file, extracts the texture image,
 * and performs color cluster analysis on non-padding pixels.
 *
 * Usage: mvn compile exec:java -Dexec.mainClass=com.lego.diag.TextureAnalyzer -Dexec.args="/path/to/file.glb"
 */
public final class TextureAnalyzer {

    /** sRGB channel threshold for UV-padding detection (same as GlbLoader). */
    private static final int UV_PADDING_THRESHOLD = 10;

    /** Bucket size for color clustering (round sRGB 0-255 to nearest this value). */
    private static final int BUCKET_SIZE = 20;

    public static void main(String[] args) throws IOException {
        String glbPath;
        if (args.length > 0) {
            glbPath = args[0];
        } else {
            glbPath = "/Users/isaiahdietrich/Downloads/the_cats_body.glb";
        }

        System.out.println("=== GLB Texture Color Analyzer ===");
        System.out.println("Loading: " + glbPath);

        GltfModelReader reader = new GltfModelReader();
        GltfModel model = reader.read(Path.of(glbPath));

        // Collect all texture images from the model
        List<BufferedImage> textures = new ArrayList<>();

        // Walk scene graph to find materials with textures
        List<SceneModel> scenes = model.getSceneModels();
        if (!scenes.isEmpty()) {
            for (SceneModel scene : scenes) {
                for (NodeModel rootNode : scene.getNodeModels()) {
                    collectTextures(rootNode, textures);
                }
            }
        } else {
            for (NodeModel node : model.getNodeModels()) {
                collectTextures(node, textures);
            }
        }

        // Also check image models directly
        List<ImageModel> imageModels = model.getImageModels();
        System.out.println("Image models in GLB: " + imageModels.size());
        for (int i = 0; i < imageModels.size(); i++) {
            ImageModel im = imageModels.get(i);
            ByteBuffer data = im.getImageData();
            if (data != null) {
                byte[] bytes = new byte[data.remaining()];
                data.duplicate().get(bytes);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    System.out.println("  Image " + i + ": " + img.getWidth() + "x" + img.getHeight()
                        + " type=" + img.getType() + " mimeType=" + im.getMimeType());
                    // Add if not already collected
                    boolean alreadyHave = false;
                    for (BufferedImage existing : textures) {
                        if (existing.getWidth() == img.getWidth()
                                && existing.getHeight() == img.getHeight()) {
                            alreadyHave = true;
                            break;
                        }
                    }
                    if (!alreadyHave) {
                        textures.add(img);
                    }
                }
            }
        }

        System.out.println("Total unique texture images found: " + textures.size());
        System.out.println();

        // Analyze each texture
        for (int t = 0; t < textures.size(); t++) {
            BufferedImage tex = textures.get(t);
            System.out.println("========================================");
            System.out.println("Analyzing texture " + t + " (" + tex.getWidth() + "x" + tex.getHeight() + ")");
            System.out.println("========================================");
            analyzeTexture(tex);
            System.out.println();
        }
    }

    private static void collectTextures(NodeModel node, List<BufferedImage> textures) {
        for (MeshModel meshModel : node.getMeshModels()) {
            for (MeshPrimitiveModel prim : meshModel.getMeshPrimitiveModels()) {
                MaterialModel mat = prim.getMaterialModel();
                if (mat instanceof MaterialModelV2 pbr) {
                    TextureModel texModel = pbr.getBaseColorTexture();
                    if (texModel != null && texModel.getImageModel() != null) {
                        ByteBuffer data = texModel.getImageModel().getImageData();
                        if (data != null) {
                            byte[] bytes = new byte[data.remaining()];
                            data.duplicate().get(bytes);
                            try {
                                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                                if (img != null) {
                                    boolean alreadyHave = false;
                                    for (BufferedImage existing : textures) {
                                        if (existing.getWidth() == img.getWidth()
                                                && existing.getHeight() == img.getHeight()) {
                                            alreadyHave = true;
                                            break;
                                        }
                                    }
                                    if (!alreadyHave) {
                                        textures.add(img);
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Failed to decode texture image: " + e.getMessage());
                            }
                        }
                    }

                    // Also report baseColorFactor
                    float[] factor = pbr.getBaseColorFactor();
                    if (factor != null && factor.length >= 3) {
                        System.out.printf("  Material baseColorFactor: [%.4f, %.4f, %.4f]%n",
                            factor[0], factor[1], factor[2]);
                    }
                }
            }
        }
        for (NodeModel child : node.getChildren()) {
            collectTextures(child, textures);
        }
    }

    private static void analyzeTexture(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int totalPixels = width * height;

        // Stats accumulators
        long nonPaddingCount = 0;
        long paddingCount = 0;
        long rSum = 0, gSum = 0, bSum = 0;
        int rMin = 255, gMin = 255, bMin = 255;
        int rMax = 0, gMax = 0, bMax = 0;

        // Color bucket map: key = "bucketR_bucketG_bucketB", value = [count, rSum, gSum, bSum]
        Map<String, long[]> buckets = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Check UV padding
                if (r <= UV_PADDING_THRESHOLD && g <= UV_PADDING_THRESHOLD && b <= UV_PADDING_THRESHOLD) {
                    paddingCount++;
                    continue;
                }

                nonPaddingCount++;
                rSum += r;
                gSum += g;
                bSum += b;
                rMin = Math.min(rMin, r);
                gMin = Math.min(gMin, g);
                bMin = Math.min(bMin, b);
                rMax = Math.max(rMax, r);
                gMax = Math.max(gMax, g);
                bMax = Math.max(bMax, b);

                // Bucket by rounding to nearest BUCKET_SIZE
                int br = roundToBucket(r);
                int bg = roundToBucket(g);
                int bb = roundToBucket(b);
                String key = br + "_" + bg + "_" + bb;
                long[] bucket = buckets.computeIfAbsent(key, k -> new long[4]);
                bucket[0]++; // count
                bucket[1] += r;
                bucket[2] += g;
                bucket[3] += b;
            }
        }

        System.out.println("Total pixels: " + totalPixels);
        System.out.println("Padding pixels (sRGB all channels <= " + UV_PADDING_THRESHOLD + "): " + paddingCount
            + String.format(" (%.1f%%)", 100.0 * paddingCount / totalPixels));
        System.out.println("Non-padding pixels: " + nonPaddingCount
            + String.format(" (%.1f%%)", 100.0 * nonPaddingCount / totalPixels));
        System.out.println();

        if (nonPaddingCount == 0) {
            System.out.println("No non-padding pixels to analyze.");
            return;
        }

        // Channel statistics
        System.out.println("--- Channel Statistics (non-padding, sRGB 0-255) ---");
        System.out.printf("  R: min=%d  max=%d  mean=%.1f%n", rMin, rMax, (double) rSum / nonPaddingCount);
        System.out.printf("  G: min=%d  max=%d  mean=%.1f%n", gMin, gMax, (double) gSum / nonPaddingCount);
        System.out.printf("  B: min=%d  max=%d  mean=%.1f%n", bMin, bMax, (double) bSum / nonPaddingCount);
        System.out.println();

        // Sort buckets by count descending
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(buckets.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed());

        System.out.println("--- Top 30 Color Clusters (bucket size = " + BUCKET_SIZE + ") ---");
        System.out.printf("%-6s  %-20s  %-22s  %-10s  %s%n",
            "Rank", "Bucket Center (sRGB)", "Avg Actual (sRGB)", "Count", "% of non-padding");
        System.out.println("-".repeat(90));

        int limit = Math.min(30, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, long[]> entry = sorted.get(i);
            String[] parts = entry.getKey().split("_");
            int br = Integer.parseInt(parts[0]);
            int bg = Integer.parseInt(parts[1]);
            int bb = Integer.parseInt(parts[2]);

            long[] vals = entry.getValue();
            long count = vals[0];
            double avgR = (double) vals[1] / count;
            double avgG = (double) vals[2] / count;
            double avgB = (double) vals[3] / count;
            double pct = 100.0 * count / nonPaddingCount;

            System.out.printf("  %2d    (%3d, %3d, %3d)        (%5.1f, %5.1f, %5.1f)     %7d     %5.1f%%%n",
                i + 1, br, bg, bb, avgR, avgG, avgB, count, pct);
        }

        System.out.println();
        System.out.println("Total distinct buckets: " + buckets.size());
    }

    private static int roundToBucket(int value) {
        return Math.min(((value + BUCKET_SIZE / 2) / BUCKET_SIZE) * BUCKET_SIZE, 260);
    }
}
