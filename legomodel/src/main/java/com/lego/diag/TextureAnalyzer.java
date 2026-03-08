package com.lego.diag;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
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
 * Diagnostic tool: loads a GLB file, walks mesh UV coordinates, and
 * performs color cluster analysis on only the texture pixels that are
 * actually referenced by the model geometry.
 *
 * <p>Unlike a naive full-texture scan, this avoids counting UV-atlas
 * padding/background pixels that the model never renders.
 *
 * <p>For each triangle it samples 4 UV points (3 vertices + centroid),
 * reads the corresponding texture pixel, and accumulates statistics.
 *
 * Usage: mvn compile exec:java -Dexec.mainClass=com.lego.diag.TextureAnalyzer -Dexec.args="/path/to/file.glb"
 */
public final class TextureAnalyzer {

    private static final int GL_TRIANGLES = 4;

    /** sRGB channel threshold for UV-padding detection (same as GlbLoader). */
    private static final int UV_PADDING_THRESHOLD = 10;

    /** Bucket size for color clustering (round sRGB 0-255 to nearest this value). */
    private static final int BUCKET_SIZE = 20;

    public static void main(String[] args) throws IOException {
        String glbPath;
        if (args.length > 0) {
            glbPath = args[0];
        } else {
            glbPath = "models/the_cats_body.glb";
        }

        System.out.println("=== GLB UV-Aware Texture Color Analyzer ===");
        System.out.println("Loading: " + glbPath);

        GltfModelReader reader = new GltfModelReader();
        GltfModel model = reader.read(Path.of(glbPath));

        // Derive output directory from the GLB filename
        String baseName = Path.of(glbPath).getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path outputDir = Path.of("output", "analysis");
        Files.createDirectories(outputDir);

        // Report image metadata and export textures as JPGs
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

                    // Save texture as JPG
                    String suffix = imageModels.size() == 1 ? "" : "_" + i;
                    File jpgFile = outputDir.resolve(baseName + "_texture" + suffix + ".jpg").toFile();
                    // Convert to RGB if needed (JPEG doesn't support alpha)
                    BufferedImage rgbImg = img;
                    if (img.getType() == BufferedImage.TYPE_4BYTE_ABGR
                            || img.getType() == BufferedImage.TYPE_INT_ARGB) {
                        rgbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                        rgbImg.createGraphics().drawImage(img, 0, 0, null);
                    }
                    ImageIO.write(rgbImg, "jpg", jpgFile);
                    System.out.println("  -> Exported: " + jpgFile.getPath());
                }
            }
        }
        System.out.println();

        // Walk the scene graph and sample texture at actual UV coordinates
        SampleAccumulator acc = new SampleAccumulator();

        // First, dump the scene graph structure for diagnostics
        System.out.println("--- Scene Graph Structure ---");
        List<SceneModel> scenes = model.getSceneModels();
        if (!scenes.isEmpty()) {
            for (int si = 0; si < scenes.size(); si++) {
                System.out.println("Scene " + si + ":");
                for (NodeModel rootNode : scenes.get(si).getNodeModels()) {
                    dumpNode(rootNode, "  ");
                    walkNode(rootNode, acc);
                }
            }
        } else {
            System.out.println("(No scenes — using top-level nodes)");
            for (NodeModel node : model.getNodeModels()) {
                dumpNode(node, "  ");
                walkNode(node, acc);
            }
        }
        System.out.println();

        acc.printReport();

        // Generate UV heatmap overlay
        if (acc.textureImage != null) {
            File heatmapFile = outputDir.resolve(baseName + "_uv_heatmap.jpg").toFile();
            generateUvHeatmap(acc, heatmapFile);
            System.out.println("UV heatmap exported: " + heatmapFile.getPath());
        }
    }

    // ---- Scene graph walking ----

    /**
     * Dumps the scene graph hierarchy with mesh/triangle counts and bounding boxes.
     */
    private static void dumpNode(NodeModel node, String indent) {
        String name = node.getName() != null ? node.getName() : "(unnamed)";
        List<MeshModel> meshes = node.getMeshModels();

        if (meshes.isEmpty()) {
            System.out.println(indent + "Node: " + name);
        } else {
            for (MeshModel mesh : meshes) {
                int totalTris = 0;
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

                for (MeshPrimitiveModel prim : mesh.getMeshPrimitiveModels()) {
                    AccessorModel posAcc = prim.getAttributes().get("POSITION");
                    if (posAcc != null) {
                        AccessorModel idxAcc = prim.getIndices();
                        totalTris += (idxAcc != null ? idxAcc.getCount() : posAcc.getCount()) / 3;

                        // Compute bounding box from position accessor min/max
                        AccessorFloatData pos = asFloatData(posAcc);
                        int count = posAcc.getCount();
                        for (int i = 0; i < count; i++) {
                            float px = pos.get(i, 0), py = pos.get(i, 1), pz = pos.get(i, 2);
                            minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
                            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
                        }
                    }
                }

                float sizeX = maxX - minX, sizeY = maxY - minY, sizeZ = maxZ - minZ;
                System.out.printf("%sNode: %-30s  %d tris  bbox: (%.2f, %.2f, %.2f) size: (%.2f x %.2f x %.2f)%n",
                    indent, name, totalTris, minX, minY, minZ, sizeX, sizeY, sizeZ);
            }
        }

        for (NodeModel child : node.getChildren()) {
            dumpNode(child, indent + "  ");
        }
    }

    private static void walkNode(NodeModel node, SampleAccumulator acc) {
        for (MeshModel meshModel : node.getMeshModels()) {
            for (MeshPrimitiveModel prim : meshModel.getMeshPrimitiveModels()) {
                processPrimitive(prim, acc);
            }
        }
        for (NodeModel child : node.getChildren()) {
            walkNode(child, acc);
        }
    }

    private static void processPrimitive(MeshPrimitiveModel prim, SampleAccumulator acc) {
        int mode = prim.getMode();
        if (mode != GL_TRIANGLES) {
            return;
        }

        Map<String, AccessorModel> attributes = prim.getAttributes();
        AccessorModel positionAccessor = attributes.get("POSITION");
        if (positionAccessor == null) return;

        // Must have TEXCOORD_0
        AccessorModel texCoordAccessor = attributes.get("TEXCOORD_0");
        if (texCoordAccessor == null) {
            acc.noTexCoordTriangles += positionAccessor.getCount() / 3;
            return;
        }
        AccessorFloatData texCoords = asFloatData(texCoordAccessor);

        // Must have a texture
        BufferedImage textureImage = extractTextureImage(prim.getMaterialModel());
        if (textureImage == null) {
            acc.noTextureTriangles += positionAccessor.getCount() / 3;
            return;
        }

        // Store texture reference for heatmap generation
        if (acc.textureImage == null) {
            acc.textureImage = textureImage;
        }

        // Report baseColorFactor once
        if (!acc.reportedFactor) {
            if (prim.getMaterialModel() instanceof MaterialModelV2 pbr) {
                float[] factor = pbr.getBaseColorFactor();
                if (factor != null && factor.length >= 3) {
                    System.out.printf("Material baseColorFactor: [%.4f, %.4f, %.4f]%n",
                        factor[0], factor[1], factor[2]);
                }
            }
            acc.reportedFactor = true;
        }

        // Walk triangles
        AccessorModel indicesAccessor = prim.getIndices();
        if (indicesAccessor != null) {
            int indexCount = indicesAccessor.getCount();
            for (int f = 0; f + 2 < indexCount; f += 3) {
                int i0 = readIndex(indicesAccessor, f);
                int i1 = readIndex(indicesAccessor, f + 1);
                int i2 = readIndex(indicesAccessor, f + 2);
                sampleTriangle(texCoords, i0, i1, i2, textureImage, acc);
            }
        } else {
            int vertexCount = positionAccessor.getCount();
            for (int f = 0; f + 2 < vertexCount; f += 3) {
                sampleTriangle(texCoords, f, f + 1, f + 2, textureImage, acc);
            }
        }
    }

    /**
     * Samples 4 UV points per triangle: 3 vertices + centroid.
     * Each sample is recorded in the accumulator.
     */
    private static void sampleTriangle(
        AccessorFloatData texCoords,
        int i0, int i1, int i2,
        BufferedImage image,
        SampleAccumulator acc
    ) {
        acc.totalTriangles++;

        float u0 = texCoords.get(i0, 0), v0 = texCoords.get(i0, 1);
        float u1 = texCoords.get(i1, 0), v1 = texCoords.get(i1, 1);
        float u2 = texCoords.get(i2, 0), v2 = texCoords.get(i2, 1);

        // 3 vertex samples
        acc.recordSample(image, u0, v0);
        acc.recordSample(image, u1, v1);
        acc.recordSample(image, u2, v2);

        // Centroid sample
        acc.recordSample(image, (u0 + u1 + u2) / 3f, (v0 + v1 + v2) / 3f);
    }

    // ---- Helpers for glTF data access (same as GlbLoader) ----

    private static BufferedImage extractTextureImage(MaterialModel material) {
        if (!(material instanceof MaterialModelV2 pbrMaterial)) return null;
        TextureModel textureModel = pbrMaterial.getBaseColorTexture();
        if (textureModel == null) return null;
        ImageModel imageModel = textureModel.getImageModel();
        if (imageModel == null) return null;
        ByteBuffer imageData = imageModel.getImageData();
        if (imageData == null) return null;
        byte[] bytes = new byte[imageData.remaining()];
        imageData.duplicate().get(bytes);
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    private static int readIndex(AccessorModel accessor, int elementIndex) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorShortData sd) return sd.getInt(elementIndex, 0);
        if (data instanceof AccessorIntData id) return id.get(elementIndex, 0);
        if (data instanceof AccessorFloatData fd) return (int) fd.get(elementIndex, 0);
        throw new IllegalArgumentException(
            "Unsupported index accessor data type: " + data.getClass().getSimpleName());
    }

    private static AccessorFloatData asFloatData(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorFloatData fd) return fd;
        throw new IllegalArgumentException(
            "Expected float accessor data, got: " + data.getClass().getSimpleName());
    }

    /** Wraps UV coordinate into [0,1). */
    private static float wrapUv(float uv) {
        return uv - (float) Math.floor(uv);
    }

    /** Converts glTF V (origin at bottom) to image Y (origin at top). */
    private static int uvToPixelY(float v, int imageHeight) {
        float flippedV = 1f - v;
        return Math.min((int) (flippedV * imageHeight), imageHeight - 1);
    }

    private static int roundToBucket(int value) {
        return Math.min(((value + BUCKET_SIZE / 2) / BUCKET_SIZE) * BUCKET_SIZE, 260);
    }

    // ---- Accumulator for UV-sampled colors ----

    private static final class SampleAccumulator {
        long totalSamples;
        long paddingSamples;
        long validSamples;
        long totalTriangles;
        long noTexCoordTriangles;
        long noTextureTriangles;
        boolean reportedFactor;
        BufferedImage textureImage;

        /** Pixel-space hit counts for UV heatmap (sized to texture). */
        int[][] hitMap;

        long rSum, gSum, bSum;
        int rMin = 255, gMin = 255, bMin = 255;
        int rMax, gMax, bMax;

        /** key = "bR_bG_bB", value = [count, rSum, gSum, bSum] */
        final Map<String, long[]> buckets = new HashMap<>();

        void recordSample(BufferedImage image, float u, float v) {
            u = wrapUv(u);
            v = wrapUv(v);

            int x = Math.min((int) (u * image.getWidth()), image.getWidth() - 1);
            int y = uvToPixelY(v, image.getHeight());

            int argb = image.getRGB(x, y);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            totalSamples++;

            // Track pixel hit for heatmap
            if (hitMap == null && textureImage != null) {
                hitMap = new int[image.getHeight()][image.getWidth()];
            }
            if (hitMap != null) {
                hitMap[y][x]++;
            }

            if (r <= UV_PADDING_THRESHOLD && g <= UV_PADDING_THRESHOLD && b <= UV_PADDING_THRESHOLD) {
                paddingSamples++;
                return;
            }

            validSamples++;
            rSum += r; gSum += g; bSum += b;
            rMin = Math.min(rMin, r); gMin = Math.min(gMin, g); bMin = Math.min(bMin, b);
            rMax = Math.max(rMax, r); gMax = Math.max(gMax, g); bMax = Math.max(bMax, b);

            int br = roundToBucket(r);
            int bg = roundToBucket(g);
            int bb = roundToBucket(b);
            String key = br + "_" + bg + "_" + bb;
            long[] bucket = buckets.computeIfAbsent(key, k -> new long[4]);
            bucket[0]++;
            bucket[1] += r;
            bucket[2] += g;
            bucket[3] += b;
        }

        void printReport() {
            System.out.println();
            System.out.println("========================================");
            System.out.println("UV-Aware Texture Analysis Results");
            System.out.println("========================================");
            System.out.println("Total triangles analyzed: " + totalTriangles);
            if (noTexCoordTriangles > 0) {
                System.out.println("  (skipped " + noTexCoordTriangles + " triangles without TEXCOORD_0)");
            }
            if (noTextureTriangles > 0) {
                System.out.println("  (skipped " + noTextureTriangles + " triangles without texture)");
            }
            System.out.println("Samples per triangle: 4 (3 vertices + centroid)");
            System.out.println("Total UV samples: " + totalSamples);
            System.out.println("Padding samples (sRGB ≤ " + UV_PADDING_THRESHOLD + "): " + paddingSamples
                + String.format(" (%.1f%%)", totalSamples > 0 ? 100.0 * paddingSamples / totalSamples : 0));
            System.out.println("Valid color samples: " + validSamples
                + String.format(" (%.1f%%)", totalSamples > 0 ? 100.0 * validSamples / totalSamples : 0));
            System.out.println();

            if (validSamples == 0) {
                System.out.println("No valid UV samples to analyze.");
                return;
            }

            System.out.println("--- Channel Statistics (sRGB 0-255) ---");
            System.out.printf("  R: min=%d  max=%d  mean=%.1f%n", rMin, rMax, (double) rSum / validSamples);
            System.out.printf("  G: min=%d  max=%d  mean=%.1f%n", gMin, gMax, (double) gSum / validSamples);
            System.out.printf("  B: min=%d  max=%d  mean=%.1f%n", bMin, bMax, (double) bSum / validSamples);
            System.out.println();

            List<Map.Entry<String, long[]>> sorted = new ArrayList<>(buckets.entrySet());
            sorted.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed());

            System.out.println("--- Top 30 Color Clusters (bucket size = " + BUCKET_SIZE + ") ---");
            System.out.printf("%-6s  %-20s  %-22s  %-10s  %s%n",
                "Rank", "Bucket Center (sRGB)", "Avg Actual (sRGB)", "Count", "% of valid");
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
                double pct = 100.0 * count / validSamples;

                System.out.printf("  %2d    (%3d, %3d, %3d)        (%5.1f, %5.1f, %5.1f)     %7d     %5.1f%%%n",
                    i + 1, br, bg, bb, avgR, avgG, avgB, count, pct);
            }

            System.out.println();
            System.out.println("Total distinct buckets: " + buckets.size());
        }
    }

    /**
     * Generates a UV heatmap overlay: the original texture dimmed to 40%,
     * with red dots at every sampled UV pixel location.
     */
    private static void generateUvHeatmap(SampleAccumulator acc, File outputFile) throws IOException {
        BufferedImage tex = acc.textureImage;
        int w = tex.getWidth(), h = tex.getHeight();

        // Create output image
        BufferedImage heatmap = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = heatmap.createGraphics();

        // Draw dimmed original texture
        g.drawImage(tex, 0, 0, null);
        // Dim it by drawing semi-transparent black over it
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, w, h);

        // Draw red dots at hit locations, brightness by hit count
        if (acc.hitMap != null) {
            // Find max hits for normalization
            int maxHits = 1;
            for (int[] row : acc.hitMap) {
                for (int hits : row) {
                    maxHits = Math.max(maxHits, hits);
                }
            }

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int hits = acc.hitMap[y][x];
                    if (hits > 0) {
                        // Brightness proportional to log(hits), range [100, 255]
                        int brightness = (int) (100 + 155 * Math.log1p(hits) / Math.log1p(maxHits));
                        heatmap.setRGB(x, y, (brightness << 16)); // pure red channel
                    }
                }
            }
        }

        g.dispose();
        ImageIO.write(heatmap, "jpg", outputFile);
    }
}
