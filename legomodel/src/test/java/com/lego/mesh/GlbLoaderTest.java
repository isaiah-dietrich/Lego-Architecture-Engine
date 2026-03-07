package com.lego.mesh;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;

class GlbLoaderTest {

    @TempDir
    Path tempDir;

    // ---- Phase 1: Geometry tests ----

    @Test
    void loadSingleTriangle() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };

        Path glb = writeGlb(tempDir.resolve("triangle.glb"), positions, indices, null, null);

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        Mesh mesh = loaded.mesh();
        assertEquals(1, mesh.triangleCount());
        assertTrue(loaded.colorMap().isEmpty(), "geometry-only GLB should have no color map");
    }

    @Test
    void loadTwoTriangles() throws IOException {
        float[] positions = {
            0f, 0f, 0f,  1f, 0f, 0f,  0f, 1f, 0f,
            1f, 0f, 0f,  1f, 1f, 0f,  0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2, 3, 4, 5 };

        Path glb = writeGlb(tempDir.resolve("two_tris.glb"), positions, indices, null, null);

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertEquals(2, loaded.mesh().triangleCount());
        assertTrue(loaded.colorMap().isEmpty());
    }

    @Test
    void vertexPositionsArePreserved() throws IOException {
        float[] positions = {
            2f, 3f, 4f,
            5f, 6f, 7f,
            8f, 9f, 10f
        };
        int[] indices = { 0, 1, 2 };

        Path glb = writeGlb(tempDir.resolve("coords.glb"), positions, indices, null, null);

        GlbLoader loader = new GlbLoader();
        Triangle tri = loader.load(glb).mesh().triangles().get(0);

        assertEquals(2.0, tri.v1().x(), 1e-5);
        assertEquals(3.0, tri.v1().y(), 1e-5);
        assertEquals(4.0, tri.v1().z(), 1e-5);
        assertEquals(5.0, tri.v2().x(), 1e-5);
        assertEquals(6.0, tri.v2().y(), 1e-5);
        assertEquals(7.0, tri.v2().z(), 1e-5);
    }

    @Test
    void rejectsGltfExtension() {
        Path gltf = tempDir.resolve("model.gltf");
        GlbLoader loader = new GlbLoader();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> loader.load(gltf));
        assertTrue(e.getMessage().contains(".gltf"));
        assertTrue(e.getMessage().contains("Convert to .glb"));
    }

    @Test
    void rejectsNonGlbExtension() {
        Path txt = tempDir.resolve("model.txt");
        GlbLoader loader = new GlbLoader();

        assertThrows(IllegalArgumentException.class, () -> loader.load(txt));
    }

    // ---- Phase 2: Color tests ----

    @Test
    void extractsVertexColors() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };
        float[] colors = {
            1f, 0f, 0f,  // red
            0f, 1f, 0f,  // green
            0f, 0f, 1f   // blue
        };

        Path glb = writeGlb(tempDir.resolve("colors.glb"), positions, indices, colors, null);

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent(), "should have color map for vertex-colored GLB");
        Map<Triangle, ColorRgb> map = loaded.colorMap().get();
        assertEquals(1, map.size());

        // Average of (1,0,0), (0,1,0), (0,0,1) = (0.333, 0.333, 0.333)
        ColorRgb color = map.values().iterator().next();
        assertEquals(1f / 3f, color.r(), 0.01f);
        assertEquals(1f / 3f, color.g(), 0.01f);
        assertEquals(1f / 3f, color.b(), 0.01f);
    }

    @Test
    void extractsMaterialBaseColorFactor() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };
        float[] baseColorFactor = { 0.8f, 0.2f, 0.5f, 1.0f };

        Path glb = writeGlb(tempDir.resolve("material.glb"), positions, indices, null, baseColorFactor);

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent());
        ColorRgb color = loaded.colorMap().get().values().iterator().next();
        assertEquals(0.8f, color.r(), 0.01f);
        assertEquals(0.2f, color.g(), 0.01f);
        assertEquals(0.5f, color.b(), 0.01f);
    }

    @Test
    void vertexColorsTakePriorityOverMaterial() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };
        float[] vertexColors = { 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f }; // all red
        float[] baseColorFactor = { 0f, 0f, 1f, 1f }; // blue

        Path glb = writeGlb(tempDir.resolve("priority.glb"), positions, indices, vertexColors, baseColorFactor);

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent());
        ColorRgb color = loaded.colorMap().get().values().iterator().next();
        // Should be red (vertex color) not blue (material)
        assertEquals(1f, color.r(), 0.01f);
        assertEquals(0f, color.g(), 0.01f);
        assertEquals(0f, color.b(), 0.01f);
    }

    // ---- Phase 3: Texture sampling tests ----

    @Test
    void extractsTextureColor() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };
        // All UVs point to center of a solid red 1x1 texture
        float[] texCoords = { 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f };
        byte[] png = createSolidPng(1, 1, 0xFF0000); // sRGB red

        Path glb = writeGlbWithTexture(
            tempDir.resolve("tex_red.glb"), positions, indices, texCoords, png, null
        );

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent(), "textured GLB should have color map");
        ColorRgb color = loaded.colorMap().get().values().iterator().next();
        // sRGB (255,0,0) → linear (1.0, 0.0, 0.0)
        assertEquals(1f, color.r(), 0.01f);
        assertEquals(0f, color.g(), 0.01f);
        assertEquals(0f, color.b(), 0.01f);
    }

    @Test
    void textureProducesTwoDistinctColors() throws IOException {
        // Two triangles sharing 4 vertices, each mapped to a different half of a 2x1 texture
        float[] positions = {
            0f, 0f, 0f,  1f, 0f, 0f,  0f, 1f, 0f,  // tri 0
            1f, 0f, 0f,  1f, 1f, 0f,  0f, 1f, 0f    // tri 1
        };
        int[] indices = { 0, 1, 2, 3, 4, 5 };
        // tri 0 UVs → left half (u ≈ 0.17), tri 1 UVs → right half (u ≈ 0.83)
        float[] texCoords = {
            0.0f, 0.5f,  0.25f, 0.5f,  0.25f, 0.5f, // avg u=0.167 → pixel 0 (red)
            0.75f, 0.5f, 1.0f, 0.5f,   0.75f, 0.5f   // avg u=0.833 → pixel 1 (blue)
        };
        byte[] png = createTwoColorPng(0xFF0000, 0x0000FF); // left=red, right=blue

        Path glb = writeGlbWithTexture(
            tempDir.resolve("tex_bicolor.glb"), positions, indices, texCoords, png, null
        );

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent());
        Map<Triangle, ColorRgb> map = loaded.colorMap().get();
        assertEquals(2, map.size());

        Triangle tri0 = loaded.mesh().triangles().get(0);
        Triangle tri1 = loaded.mesh().triangles().get(1);
        ColorRgb c0 = map.get(tri0);
        ColorRgb c1 = map.get(tri1);

        // tri0 → red, tri1 → blue
        assertEquals(1f, c0.r(), 0.01f);
        assertEquals(0f, c0.b(), 0.01f);
        assertEquals(0f, c1.r(), 0.01f);
        assertEquals(1f, c1.b(), 0.01f);
    }

    @Test
    void textureMultipliedByBaseColorFactor() throws IOException {
        float[] positions = {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
        int[] indices = { 0, 1, 2 };
        float[] texCoords = { 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f };
        byte[] png = createSolidPng(1, 1, 0xFFFFFF); // sRGB white → linear (1,1,1)
        float[] factor = { 0.5f, 0.3f, 0.1f, 1.0f };

        Path glb = writeGlbWithTexture(
            tempDir.resolve("tex_factor.glb"), positions, indices, texCoords, png, factor
        );

        GlbLoader loader = new GlbLoader();
        LoadedModel loaded = loader.load(glb);

        assertTrue(loaded.colorMap().isPresent());
        ColorRgb color = loaded.colorMap().get().values().iterator().next();
        // white texture × factor = factor
        assertEquals(0.5f, color.r(), 0.01f);
        assertEquals(0.3f, color.g(), 0.01f);
        assertEquals(0.1f, color.b(), 0.01f);
    }

    // ---- GLB binary file builder for tests ----

    /**
     * Writes a minimal valid GLB file with the given geometry and optional color data.
     *
     * @param path            output path
     * @param positions       flat float array: [x0,y0,z0, x1,y1,z1, ...]
     * @param indices         triangle index array: [i0,i1,i2, ...]
     * @param vertexColors    optional flat float array (RGB per vertex): [r0,g0,b0, r1,g1,b1, ...]
     * @param baseColorFactor optional RGBA material color: [r, g, b, a]
     * @return path to the written GLB file
     */
    private Path writeGlb(
        Path path,
        float[] positions,
        int[] indices,
        float[] vertexColors,
        float[] baseColorFactor
    ) throws IOException {
        int vertexCount = positions.length / 3;
        int indexCount = indices.length;
        int positionsBytes = positions.length * 4;
        int indicesBytes = indexCount * 2; // unsigned short
        int indicesPadded = (indicesBytes + 3) & ~3; // pad to 4
        int colorsBytes = vertexColors != null ? vertexColors.length * 4 : 0;

        int totalBinSize = positionsBytes + indicesPadded + colorsBytes;

        // Build BIN chunk data
        ByteBuffer bin = ByteBuffer.allocate(totalBinSize).order(ByteOrder.LITTLE_ENDIAN);
        for (float p : positions) bin.putFloat(p);
        for (int idx : indices) bin.putShort((short) idx);
        // pad indices to 4-byte boundary
        while (bin.position() < positionsBytes + indicesPadded) bin.put((byte) 0);
        if (vertexColors != null) {
            for (float c : vertexColors) bin.putFloat(c);
        }
        bin.flip();

        // Build JSON
        StringBuilder json = new StringBuilder();
        json.append("{\"asset\":{\"version\":\"2.0\"},\"scene\":0,");
        json.append("\"scenes\":[{\"nodes\":[0]}],");
        json.append("\"nodes\":[{\"mesh\":0}],");

        // Mesh primitive
        json.append("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0");
        int nextAccessor = 2; // 0=positions, 1=indices
        if (vertexColors != null) {
            json.append(",\"COLOR_0\":").append(nextAccessor);
            nextAccessor++;
        }
        json.append("},\"indices\":1");
        if (baseColorFactor != null) {
            json.append(",\"material\":0");
        }
        json.append("}]}],");

        // Accessors
        json.append("\"accessors\":[");
        // 0: positions
        json.append("{\"bufferView\":0,\"componentType\":5126,\"count\":").append(vertexCount)
            .append(",\"type\":\"VEC3\"},");
        // 1: indices
        json.append("{\"bufferView\":1,\"componentType\":5123,\"count\":").append(indexCount)
            .append(",\"type\":\"SCALAR\"}");
        if (vertexColors != null) {
            json.append(",{\"bufferView\":2,\"componentType\":5126,\"count\":").append(vertexCount)
                .append(",\"type\":\"VEC3\"}");
        }
        json.append("],");

        // Buffer views
        json.append("\"bufferViews\":[");
        json.append("{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(positionsBytes).append("},");
        json.append("{\"buffer\":0,\"byteOffset\":").append(positionsBytes)
            .append(",\"byteLength\":").append(indicesBytes).append("}");
        if (vertexColors != null) {
            json.append(",{\"buffer\":0,\"byteOffset\":").append(positionsBytes + indicesPadded)
                .append(",\"byteLength\":").append(colorsBytes).append("}");
        }
        json.append("],");

        // Materials
        if (baseColorFactor != null) {
            json.append("\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[")
                .append(baseColorFactor[0]).append(",").append(baseColorFactor[1]).append(",")
                .append(baseColorFactor[2]).append(",").append(baseColorFactor[3])
                .append("]}}],");
        }

        // Buffer
        json.append("\"buffers\":[{\"byteLength\":").append(totalBinSize).append("}]}");

        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        // Pad JSON to 4-byte boundary with spaces
        int jsonPadded = (jsonBytes.length + 3) & ~3;
        byte[] jsonChunkData = new byte[jsonPadded];
        System.arraycopy(jsonBytes, 0, jsonChunkData, 0, jsonBytes.length);
        for (int i = jsonBytes.length; i < jsonPadded; i++) jsonChunkData[i] = 0x20; // space

        // Build GLB
        int totalLength = 12 + 8 + jsonPadded + 8 + totalBinSize;
        ByteBuffer glb = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);

        // Header
        glb.putInt(0x46546C67); // magic "glTF"
        glb.putInt(2);          // version
        glb.putInt(totalLength);

        // JSON chunk
        glb.putInt(jsonPadded);
        glb.putInt(0x4E4F534A); // "JSON"
        glb.put(jsonChunkData);

        // BIN chunk
        glb.putInt(totalBinSize);
        glb.putInt(0x004E4942); // "BIN\0"
        glb.put(bin);

        glb.flip();
        Files.write(path, glb.array());
        return path;
    }

    /**
     * Writes a GLB file with a baseColorTexture mapped via TEXCOORD_0.
     */
    private Path writeGlbWithTexture(
        Path path,
        float[] positions,
        int[] indices,
        float[] texCoords,
        byte[] textureImageBytes,
        float[] baseColorFactor
    ) throws IOException {
        int vertexCount = positions.length / 3;
        int indexCount = indices.length;
        int positionsBytes = positions.length * 4;
        int indicesBytes = indexCount * 2;
        int indicesPadded = (indicesBytes + 3) & ~3;
        int texCoordsBytes = texCoords.length * 4;
        int imageBytesPadded = (textureImageBytes.length + 3) & ~3;

        int totalBinSize = positionsBytes + indicesPadded + texCoordsBytes + imageBytesPadded;

        ByteBuffer bin = ByteBuffer.allocate(totalBinSize).order(ByteOrder.LITTLE_ENDIAN);
        for (float p : positions) bin.putFloat(p);
        for (int idx : indices) bin.putShort((short) idx);
        while (bin.position() < positionsBytes + indicesPadded) bin.put((byte) 0);
        int texCoordsOffset = bin.position();
        for (float t : texCoords) bin.putFloat(t);
        int imageOffset = bin.position();
        bin.put(textureImageBytes);
        while (bin.position() < totalBinSize) bin.put((byte) 0);
        bin.flip();

        StringBuilder json = new StringBuilder();
        json.append("{\"asset\":{\"version\":\"2.0\"},\"scene\":0,");
        json.append("\"scenes\":[{\"nodes\":[0]}],");
        json.append("\"nodes\":[{\"mesh\":0}],");

        // Mesh: POSITION=0, TEXCOORD_0=2, indices=1, material=0
        json.append("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"TEXCOORD_0\":2},\"indices\":1,\"material\":0}]}],");

        // Accessors: 0=positions, 1=indices, 2=texCoords
        json.append("\"accessors\":[");
        json.append("{\"bufferView\":0,\"componentType\":5126,\"count\":").append(vertexCount).append(",\"type\":\"VEC3\"},");
        json.append("{\"bufferView\":1,\"componentType\":5123,\"count\":").append(indexCount).append(",\"type\":\"SCALAR\"},");
        json.append("{\"bufferView\":2,\"componentType\":5126,\"count\":").append(vertexCount).append(",\"type\":\"VEC2\"}");
        json.append("],");

        // BufferViews: 0=positions, 1=indices, 2=texCoords, 3=image
        json.append("\"bufferViews\":[");
        json.append("{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(positionsBytes).append("},");
        json.append("{\"buffer\":0,\"byteOffset\":").append(positionsBytes).append(",\"byteLength\":").append(indicesBytes).append("},");
        json.append("{\"buffer\":0,\"byteOffset\":").append(texCoordsOffset).append(",\"byteLength\":").append(texCoordsBytes).append("},");
        json.append("{\"buffer\":0,\"byteOffset\":").append(imageOffset).append(",\"byteLength\":").append(textureImageBytes.length).append("}");
        json.append("],");

        // Material with baseColorTexture
        json.append("\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}");
        if (baseColorFactor != null) {
            json.append(",\"baseColorFactor\":[")
                .append(baseColorFactor[0]).append(",").append(baseColorFactor[1]).append(",")
                .append(baseColorFactor[2]).append(",").append(baseColorFactor[3]).append("]");
        }
        json.append("}}],");

        // Texture, image, sampler
        json.append("\"textures\":[{\"source\":0,\"sampler\":0}],");
        json.append("\"images\":[{\"bufferView\":3,\"mimeType\":\"image/png\"}],");
        json.append("\"samplers\":[{}],");

        // Buffer
        json.append("\"buffers\":[{\"byteLength\":").append(totalBinSize).append("}]}");

        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        int jsonPadded = (jsonBytes.length + 3) & ~3;
        byte[] jsonChunkData = new byte[jsonPadded];
        System.arraycopy(jsonBytes, 0, jsonChunkData, 0, jsonBytes.length);
        for (int i = jsonBytes.length; i < jsonPadded; i++) jsonChunkData[i] = 0x20;

        int totalLength = 12 + 8 + jsonPadded + 8 + totalBinSize;
        ByteBuffer glb = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67);
        glb.putInt(2);
        glb.putInt(totalLength);
        glb.putInt(jsonPadded);
        glb.putInt(0x4E4F534A);
        glb.put(jsonChunkData);
        glb.putInt(totalBinSize);
        glb.putInt(0x004E4942);
        glb.put(bin);
        glb.flip();
        Files.write(path, glb.array());
        return path;
    }

    private byte[] createSolidPng(int width, int height, int rgb) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private byte[] createTwoColorPng(int rgbLeft, int rgbRight) throws IOException {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, rgbLeft);
        img.setRGB(1, 0, rgbRight);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
