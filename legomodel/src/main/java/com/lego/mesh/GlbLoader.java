package com.lego.mesh;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.model.ColorMath;
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
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.io.GltfModelReader;

/**
 * Loads .glb files using the de.javagl:jgltf-model library.
 *
 * Phase 1 (geometry): Walks the scene graph, applies node transforms to
 * positions, and triangulates indexed GL_TRIANGLES primitives.
 *
 * Phase 2 (color): Extracts per-triangle color using this priority:
 * 
 *   - COLOR_0 vertex attribute (average of 3 vertex colors)
 *   - baseColorTexture sampled at the triangle's UV centroid
 *       (from TEXCOORD_0), multiplied by baseColorFactor
 *   - baseColorFactor material property (standalone fallback)
 * 
 * Color is returned as a side-channel Map<Triangle, ColorRgb>
 * inside LoadedModel. Texture samples are converted from sRGB to
 * linear RGB to match the pipeline's color-space convention.
 */
public final class GlbLoader implements ModelLoader {

    private static final int GL_TRIANGLES = 4;

    @Override
    /** Loads a GLB file, extracting geometry and per-triangle color data. */
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
            boolean unlit = detectUnlit(path);
            return LoadedModel.withColorAndTexture(mesh, colorMap, texturedTriangles, unlit);
        }
        return LoadedModel.geometryOnly(mesh);
    }

    /**
     * Detects whether this GLB file uses the KHR_materials_unlit extension
     * by reading the JSON chunk from the binary container header.
     *
     * When a model is unlit, its textures already contain pure albedo —
     * shadow removal would over-correct and shift colors.
     */
    private static boolean detectUnlit(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            // GLB header: 4 bytes magic + 4 bytes version + 4 bytes total length
            byte[] header = is.readNBytes(12);
            if (header.length < 12) return false;

            // Chunk 0 header: 4 bytes length (little-endian) + 4 bytes type
            byte[] chunkHeader = is.readNBytes(8);
            if (chunkHeader.length < 8) return false;

            int jsonLength = (chunkHeader[0] & 0xFF)
                           | ((chunkHeader[1] & 0xFF) << 8)
                           | ((chunkHeader[2] & 0xFF) << 16)
                           | ((chunkHeader[3] & 0xFF) << 24);

            byte[] jsonBytes = is.readNBytes(jsonLength);
            return new String(jsonBytes, StandardCharsets.UTF_8)
                    .contains("KHR_materials_unlit");
        }
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
        ColorRgb materialColor = GlbMaterialExtractor.extractMaterialColor(primitive.getMaterialModel());

        // Decode baseColorTexture image (if present)
        BufferedImage textureImage = GlbMaterialExtractor.extractTextureImage(primitive.getMaterialModel());

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

                ColorRgb triColor = GlbTriangleColorResolver.resolveTriangleColor(
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

                ColorRgb triColor = GlbTriangleColorResolver.resolveTriangleColor(
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
     * The transform matrix m is in column-major order (OpenGL/glTF convention).
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
            vc0 = new ColorRgb(ColorMath.clamp01(colors.get(i0, 0)), ColorMath.clamp01(colors.get(i0, 1)), ColorMath.clamp01(colors.get(i0, 2)));
            vc1 = new ColorRgb(ColorMath.clamp01(colors.get(i1, 0)), ColorMath.clamp01(colors.get(i1, 1)), ColorMath.clamp01(colors.get(i1, 2)));
            vc2 = new ColorRgb(ColorMath.clamp01(colors.get(i2, 0)), ColorMath.clamp01(colors.get(i2, 1)), ColorMath.clamp01(colors.get(i2, 2)));
        }

        return new TexturedTriangle(u0, v0, u1, v1, u2, v2,
            textureImage, vc0, vc1, vc2, materialColor);
    }

}
