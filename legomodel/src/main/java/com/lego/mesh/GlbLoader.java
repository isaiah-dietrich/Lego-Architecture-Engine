package com.lego.mesh;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

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
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.MaterialModelV2;

/**
 * Loads {@code .glb} files using the {@code de.javagl:jgltf-model} library.
 *
 * <p>Phase 1 (geometry): Walks the scene graph, applies node transforms to
 * positions, and triangulates indexed {@code GL_TRIANGLES} primitives.
 *
 * <p>Phase 2 (color): Extracts per-triangle color using this priority:
 * <ol>
 *   <li>{@code COLOR_0} vertex attribute (average of 3 vertex colors)</li>
 *   <li>{@code baseColorTexture} sampled at the triangle's UV centroid
 *       (from {@code TEXCOORD_0}), multiplied by {@code baseColorFactor}</li>
 *   <li>{@code baseColorFactor} material property (standalone fallback)</li>
 * </ol>
 * Color is returned as a side-channel {@code Map<Triangle, ColorRgb>}
 * inside {@link LoadedModel}. Texture samples are converted from sRGB to
 * linear RGB to match the pipeline's color-space convention.
 */
public final class GlbLoader implements ModelLoader {

    private static final int GL_TRIANGLES = 4;

    /**
     * sRGB channel threshold for UV-padding detection. Pixels with all channels
     * at or below this value (out of 255) are treated as texture atlas padding
     * and excluded from color sampling.
     */
    private static final int UV_PADDING_SRGB_THRESHOLD = 10;

    @Override
    public LoadedModel load(Path path) throws IOException {
        String filename = path.getFileName().toString().toLowerCase();
        if (filename.endsWith(".gltf")) {
            throw new IllegalArgumentException(
                "Unsupported format: .gltf files are not accepted. Convert to .glb first."
            );
        }
        if (!filename.endsWith(".glb")) {
            throw new IllegalArgumentException(
                "GlbLoader only supports .glb files, got: " + path.getFileName()
            );
        }

        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.read(path);

        List<Triangle> triangles = new ArrayList<>();
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        List<TexturedTriangle> texturedTriangles = new ArrayList<>();
        boolean hasAnyColor = false;

        List<SceneModel> scenes = gltfModel.getSceneModels();
        if (scenes.isEmpty()) {
            // Fall back to all nodes if no scenes defined
            for (NodeModel node : gltfModel.getNodeModels()) {
                hasAnyColor |= processNode(node, triangles, colorMap, texturedTriangles);
            }
        } else {
            for (SceneModel scene : scenes) {
                for (NodeModel rootNode : scene.getNodeModels()) {
                    hasAnyColor |= processNode(rootNode, triangles, colorMap, texturedTriangles);
                }
            }
        }

        if (triangles.isEmpty()) {
            throw new IllegalArgumentException(
                "GLB file contains no triangle geometry: " + path.getFileName()
            );
        }

        Mesh mesh = new Mesh(triangles);
        if (hasAnyColor) {
            return LoadedModel.withColorAndTexture(mesh, colorMap, texturedTriangles);
        }
        return LoadedModel.geometryOnly(mesh);
    }

    /**
     * Recursively processes a scene-graph node and its children.
     *
     * @return true if any color data was found
     */
    private boolean processNode(
        NodeModel node,
        List<Triangle> triangles,
        Map<Triangle, ColorRgb> colorMap,
        List<TexturedTriangle> texturedTriangles
    ) {
        boolean hasColor = false;

        // Compute this node's global transform (includes all ancestor transforms)
        float[] globalTransform = node.computeGlobalTransform(null);

        for (MeshModel meshModel : node.getMeshModels()) {
            for (MeshPrimitiveModel primitive : meshModel.getMeshPrimitiveModels()) {
                hasColor |= processPrimitive(primitive, globalTransform, triangles, colorMap, texturedTriangles);
            }
        }

        for (NodeModel child : node.getChildren()) {
            hasColor |= processNode(child, triangles, colorMap, texturedTriangles);
        }

        return hasColor;
    }

    /**
     * Processes a single mesh primitive, extracting triangles and optional color.
     */
    private boolean processPrimitive(
        MeshPrimitiveModel primitive,
        float[] globalTransform,
        List<Triangle> triangles,
        Map<Triangle, ColorRgb> colorMap,
        List<TexturedTriangle> texturedTriangles
    ) {
        int mode = primitive.getMode();
        if (mode != GL_TRIANGLES) {
            // Skip non-triangle primitives (points, lines, strips, fans)
            return false;
        }

        Map<String, AccessorModel> attributes = primitive.getAttributes();
        AccessorModel positionAccessor = attributes.get("POSITION");
        if (positionAccessor == null) {
            return false;
        }

        AccessorFloatData positions = asFloatData(positionAccessor);
        int vertexCount = positionAccessor.getCount();

        // Read optional COLOR_0
        AccessorModel colorAccessor = attributes.get("COLOR_0");
        AccessorFloatData colors = null;
        int colorComponents = 0;
        if (colorAccessor != null) {
            colors = asFloatData(colorAccessor);
            // COLOR_0 can be VEC3 (RGB) or VEC4 (RGBA)
            colorComponents = colorAccessor.getElementType().getNumComponents();
        }

        // Read optional TEXCOORD_0 for texture sampling
        AccessorModel texCoordAccessor = attributes.get("TEXCOORD_0");
        AccessorFloatData texCoords = null;
        if (texCoordAccessor != null) {
            texCoords = asFloatData(texCoordAccessor);
        }

        // Read material baseColorFactor as fallback
        ColorRgb materialColor = extractMaterialColor(primitive.getMaterialModel());

        // Decode baseColorTexture image (if present)
        BufferedImage textureImage = extractTextureImage(primitive.getMaterialModel());

        // Read indices
        AccessorModel indicesAccessor = primitive.getIndices();
        boolean hasColor = false;

        if (indicesAccessor != null) {
            // Indexed primitives
            int indexCount = indicesAccessor.getCount();
            for (int f = 0; f + 2 < indexCount; f += 3) {
                int i0 = readIndex(indicesAccessor, f);
                int i1 = readIndex(indicesAccessor, f + 1);
                int i2 = readIndex(indicesAccessor, f + 2);

                Triangle tri = createTriangle(positions, globalTransform, i0, i1, i2);
                triangles.add(tri);
                texturedTriangles.add(buildTexturedTriangle(
                    texCoords, textureImage, materialColor, colors, colorComponents, i0, i1, i2));

                ColorRgb triColor = resolveTriangleColor(
                    colors, colorComponents, i0, i1, i2,
                    texCoords, textureImage, materialColor
                );
                if (triColor != null) {
                    colorMap.put(tri, triColor);
                    hasColor = true;
                }
            }
        } else {
            // Non-indexed: every 3 sequential vertices form a triangle
            for (int f = 0; f + 2 < vertexCount; f += 3) {
                Triangle tri = createTriangle(positions, globalTransform, f, f + 1, f + 2);
                triangles.add(tri);
                texturedTriangles.add(buildTexturedTriangle(
                    texCoords, textureImage, materialColor, colors, colorComponents, f, f + 1, f + 2));

                ColorRgb triColor = resolveTriangleColor(
                    colors, colorComponents, f, f + 1, f + 2,
                    texCoords, textureImage, materialColor
                );
                if (triColor != null) {
                    colorMap.put(tri, triColor);
                    hasColor = true;
                }
            }
        }

        return hasColor;
    }

    /**
     * Creates a Triangle by reading 3 vertex positions and applying the node's
     * global transform.
     */
    private Triangle createTriangle(
        AccessorFloatData positions,
        float[] m,
        int i0, int i1, int i2
    ) {
        Vector3 v0 = transformPosition(positions, i0, m);
        Vector3 v1 = transformPosition(positions, i1, m);
        Vector3 v2 = transformPosition(positions, i2, m);
        return new Triangle(v0, v1, v2);
    }

    /**
     * Reads a vertex position, applies the 4x4 global transform, and returns a Vector3.
     * The transform matrix {@code m} is in column-major order (OpenGL/glTF convention).
     */
    private Vector3 transformPosition(AccessorFloatData positions, int index, float[] m) {
        float px = positions.get(index, 0);
        float py = positions.get(index, 1);
        float pz = positions.get(index, 2);

        // Column-major 4x4: m[col*4 + row]
        double x = m[0] * px + m[4] * py + m[8]  * pz + m[12];
        double y = m[1] * px + m[5] * py + m[9]  * pz + m[13];
        double z = m[2] * px + m[6] * py + m[10] * pz + m[14];

        return new Vector3(x, y, z);
    }

    /**
     * Determines the color for a triangle. Priority:
     * 1. COLOR_0 vertex attribute (average of 3 vertex colors)
     * 2. baseColorTexture multi-sampled across the triangle's UV footprint
     *    (× baseColorFactor if set)
     * 3. baseColorFactor alone
     * 4. null (no color)
     */
    private ColorRgb resolveTriangleColor(
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
            return new ColorRgb(clamp01(r), clamp01(g), clamp01(b));
        }

        if (textureImage != null && texCoords != null) {
            float u0 = texCoords.get(i0, 0), v0 = texCoords.get(i0, 1);
            float u1 = texCoords.get(i1, 0), v1 = texCoords.get(i1, 1);
            float u2 = texCoords.get(i2, 0), v2 = texCoords.get(i2, 1);

            // Multi-sample: 3 vertices + centroid + 3 edge midpoints = 7 samples.
            // This captures interior color that vertex-only sampling misses
            // (e.g., white body fur where vertices sit at texture island edges).
            float rSum = 0, gSum = 0, bSum = 0;
            int validCount = 0;

            // Vertex samples
            ColorRgb s;
            s = sampleTextureFiltered(textureImage, u0, v0);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = sampleTextureFiltered(textureImage, u1, v1);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = sampleTextureFiltered(textureImage, u2, v2);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            // Centroid sample
            s = sampleTextureFiltered(textureImage,
                (u0 + u1 + u2) / 3f, (v0 + v1 + v2) / 3f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            // Edge midpoint samples
            s = sampleTextureFiltered(textureImage,
                (u0 + u1) / 2f, (v0 + v1) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = sampleTextureFiltered(textureImage,
                (u1 + u2) / 2f, (v1 + v2) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }
            s = sampleTextureFiltered(textureImage,
                (u0 + u2) / 2f, (v0 + v2) / 2f);
            if (s != null) { rSum += s.r(); gSum += s.g(); bSum += s.b(); validCount++; }

            if (validCount > 0) {
                ColorRgb texColor = new ColorRgb(
                    rSum / validCount, gSum / validCount, bSum / validCount);
                if (materialColor != null) {
                    return new ColorRgb(
                        clamp01(texColor.r() * materialColor.r()),
                        clamp01(texColor.g() * materialColor.g()),
                        clamp01(texColor.b() * materialColor.b())
                    );
                }
                return texColor;
            }
            // All samples were UV padding — fall through to materialColor
        }

        return materialColor; // may be null
    }

    /**
     * Extracts baseColorFactor from a glTF v2 PBR material, or returns null.
     */
    private ColorRgb extractMaterialColor(MaterialModel material) {
        if (material instanceof MaterialModelV2 pbrMaterial) {
            float[] factor = pbrMaterial.getBaseColorFactor();
            if (factor != null && factor.length >= 3) {
                return new ColorRgb(clamp01(factor[0]), clamp01(factor[1]), clamp01(factor[2]));
            }
        }
        return null;
    }

    /**
     * Decodes the baseColorTexture image from a glTF v2 PBR material, or returns null.
     */
    private BufferedImage extractTextureImage(MaterialModel material) {
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

    /**
     * Samples a texture at the given UV coordinate, converting from sRGB to linear RGB.
     * UV coordinates are wrapped to [0,1] (glTF default repeat mode).
     */
    private ColorRgb sampleTexture(BufferedImage image, float u, float v) {
        u = wrapUv(u);
        v = wrapUv(v);

        int x = Math.min((int) (u * image.getWidth()), image.getWidth() - 1);
        int y = uvToPixelY(v, image.getHeight());

        int argb = image.getRGB(x, y);
        float sR = ((argb >> 16) & 0xFF) / 255f;
        float sG = ((argb >> 8) & 0xFF) / 255f;
        float sB = (argb & 0xFF) / 255f;

        return new ColorRgb(
            clamp01((float) srgbToLinear(sR)),
            clamp01((float) srgbToLinear(sG)),
            clamp01((float) srgbToLinear(sB))
        );
    }

    /**
     * Samples a texture at the given UV coordinate, returning null if the pixel
     * is likely UV-atlas padding (all sRGB channels ≤ {@link #UV_PADDING_SRGB_THRESHOLD}).
     * Non-padding pixels are converted from sRGB to linear RGB.
     */
    private ColorRgb sampleTextureFiltered(BufferedImage image, float u, float v) {
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
            clamp01((float) srgbToLinear(sR / 255f)),
            clamp01((float) srgbToLinear(sG / 255f)),
            clamp01((float) srgbToLinear(sB / 255f))
        );
    }

    /** sRGB gamma-encoded [0,1] to linear [0,1]. */
    private static double srgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        }
        return Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Wraps UV coordinate into [0,1). */
    private static float wrapUv(float uv) {
        return uv - (float) Math.floor(uv);
    }

    /**
     * Converts glTF V (origin at bottom) to image Y (origin at top).
     */
    private static int uvToPixelY(float v, int imageHeight) {
        float flippedV = 1f - v;
        return Math.min((int) (flippedV * imageHeight), imageHeight - 1);
    }

    /**
     * Reads an index value from an accessor that may be backed by short, int, or byte data.
     */
    private int readIndex(AccessorModel accessor, int elementIndex) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorShortData shortData) {
            return shortData.getInt(elementIndex, 0);
        }
        if (data instanceof AccessorIntData intData) {
            return intData.get(elementIndex, 0);
        }
        // Byte indices: fall back to reading as float and casting
        if (data instanceof AccessorFloatData floatData) {
            return (int) floatData.get(elementIndex, 0);
        }
        throw new IllegalArgumentException(
            "Unsupported index accessor data type: " + data.getClass().getSimpleName()
        );
    }

    /**
     * Casts an AccessorModel's data to AccessorFloatData.
     */
    private AccessorFloatData asFloatData(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorFloatData floatData) {
            return floatData;
        }
        throw new IllegalArgumentException(
            "Expected float accessor data for " + accessor.getElementType()
            + ", got: " + data.getClass().getSimpleName()
        );
    }

    /**
     * Creates a TexturedTriangle record capturing per-vertex UV, vertex color,
     * texture reference, and material color for the supersampled pipeline.
     */
    private TexturedTriangle buildTexturedTriangle(
            AccessorFloatData texCoords,
            BufferedImage textureImage,
            ColorRgb materialColor,
            AccessorFloatData colors,
            int colorComponents,
            int i0, int i1, int i2) {
        float u0 = 0, v0 = 0, u1 = 0, v1 = 0, u2 = 0, v2 = 0;
        if (texCoords != null) {
            u0 = texCoords.get(i0, 0); v0 = texCoords.get(i0, 1);
            u1 = texCoords.get(i1, 0); v1 = texCoords.get(i1, 1);
            u2 = texCoords.get(i2, 0); v2 = texCoords.get(i2, 1);
        }

        ColorRgb vc0 = null, vc1 = null, vc2 = null;
        if (colors != null && colorComponents >= 3) {
            vc0 = new ColorRgb(clamp01(colors.get(i0, 0)), clamp01(colors.get(i0, 1)), clamp01(colors.get(i0, 2)));
            vc1 = new ColorRgb(clamp01(colors.get(i1, 0)), clamp01(colors.get(i1, 1)), clamp01(colors.get(i1, 2)));
            vc2 = new ColorRgb(clamp01(colors.get(i2, 0)), clamp01(colors.get(i2, 1)), clamp01(colors.get(i2, 2)));
        }

        return new TexturedTriangle(u0, v0, u1, v1, u2, v2,
            textureImage, vc0, vc1, vc2, materialColor);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
