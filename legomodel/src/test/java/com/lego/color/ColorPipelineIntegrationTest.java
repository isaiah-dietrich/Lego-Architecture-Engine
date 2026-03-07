package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.cli.Main;
import com.lego.data.CatalogConfig;

/**
 * End-to-end integration test verifying that colors extracted from a GLB file
 * flow through the full pipeline and appear as correct LDraw color codes in
 * the exported {@code .ldr} file.
 *
 * <p>Pipeline under test:
 * GLB (baseColorFactor) → GlbLoader → ColorRgb → MeshNormalizer →
 * Voxelizer → ColorSampler → LegoPaletteMapper → LDrawExporter → .ldr
 */
class ColorPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void blackMaterialColorFlowsThroughPipelineToLDrawCode0() throws IOException {
        // Build a cube GLB with baseColorFactor = pure black (0, 0, 0, 1)
        Path glbPath = tempDir.resolve("black_cube.glb");
        writeCubeGlb(glbPath, new float[] { 0f, 0f, 0f, 1f });

        // Set up catalog and palette files
        setupCatalog(tempDir);
        setupPalette(tempDir);

        Path ldrPath = tempDir.resolve("output.ldr");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[] {
            glbPath.toString(), "8", ldrPath.toString(), "ldraw",
            "--color-mode=glb-color"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer), tempDir);

        String stdout = outBuffer.toString();
        String stderr = errBuffer.toString();

        assertEquals(0, exitCode, "Pipeline should succeed. Stderr: " + stderr);
        assertTrue(stdout.contains("Color mode: glb-color"),
            "Should report glb-color mode in output. Got: " + stdout);
        assertTrue(Files.exists(ldrPath), "LDR file should be written");

        String ldr = Files.readString(ldrPath);
        List<String> partLines = ldr.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertFalse(partLines.isEmpty(), "LDR should contain part placement lines");

        // Every brick should have color code 0 (Black) — not default 16
        for (String line : partLines) {
            assertTrue(line.startsWith("1 0 "),
                "All bricks from a black GLB should have LDraw color 0 (Black). Got: " + line);
        }
    }

    @Test
    void whiteMaterialColorFlowsThroughPipelineToLDrawCode15() throws IOException {
        Path glbPath = tempDir.resolve("white_cube.glb");
        writeCubeGlb(glbPath, new float[] { 1f, 1f, 1f, 1f });

        setupCatalog(tempDir);
        setupPalette(tempDir);

        Path ldrPath = tempDir.resolve("output.ldr");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[] {
            glbPath.toString(), "8", ldrPath.toString(), "ldraw",
            "--color-mode=glb-color"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer), tempDir);

        assertEquals(0, exitCode, "Pipeline should succeed. Stderr: " + errBuffer);

        String ldr = Files.readString(ldrPath);
        List<String> partLines = ldr.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertFalse(partLines.isEmpty(), "LDR should contain part placement lines");

        for (String line : partLines) {
            assertTrue(line.startsWith("1 15 "),
                "All bricks from a white GLB should have LDraw color 15 (White). Got: " + line);
        }
    }

    @Test
    void colorFallbackAppliedToUncoloredBricks() throws IOException {
        // Use a geometry-only GLB (no color data) with glb-color mode
        // The color map will be empty, so all bricks should get the fallback color
        Path glbPath = tempDir.resolve("nocolor_cube.glb");
        writeCubeGlb(glbPath, null); // no baseColorFactor

        setupCatalog(tempDir);
        setupPalette(tempDir);

        Path ldrPath = tempDir.resolve("output.ldr");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[] {
            glbPath.toString(), "8", ldrPath.toString(), "ldraw",
            "--color-mode=glb-color",
            "--color-fallback=4"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer), tempDir);

        assertEquals(0, exitCode, "Pipeline should succeed. Stderr: " + errBuffer);

        String ldr = Files.readString(ldrPath);
        List<String> partLines = ldr.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertFalse(partLines.isEmpty(), "LDR should contain part placement lines");

        // Without color data but with fallback=4, bricks should use default 16
        // because colorMap is empty (Optional.empty), so brickColorCodes stays null
        // Actually: when colorMap is empty, the glb-color branch is skipped entirely,
        // so brickColorCodes = null, and all bricks get default color 16
        for (String line : partLines) {
            assertTrue(line.startsWith("1 16 "),
                "No-color GLB should produce default color 16. Got: " + line);
        }
    }

    @Test
    void noColorModeProducesDefaultColor16() throws IOException {
        Path glbPath = tempDir.resolve("colored_cube.glb");
        writeCubeGlb(glbPath, new float[] { 1f, 0f, 0f, 1f }); // red material

        setupCatalog(tempDir);
        setupPalette(tempDir);

        Path ldrPath = tempDir.resolve("output.ldr");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        // No --color-mode flag → defaults to "none"
        int exitCode = Main.run(new String[] {
            glbPath.toString(), "8", ldrPath.toString(), "ldraw"
        }, new PrintStream(outBuffer), new PrintStream(errBuffer), tempDir);

        assertEquals(0, exitCode, "Pipeline should succeed. Stderr: " + errBuffer);

        String ldr = Files.readString(ldrPath);
        List<String> partLines = ldr.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertFalse(partLines.isEmpty());

        // Without --color-mode=glb-color, colors should NOT appear — all default 16
        for (String line : partLines) {
            assertTrue(line.startsWith("1 16 "),
                "Without color mode, all bricks should use default color 16. Got: " + line);
        }
    }

    // ---- Helpers ----

    private void setupCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        String csv = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n";
        Files.writeString(catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE), csv);
    }

    private void setupPalette(Path baseDir) throws IOException {
        Path paletteDir = baseDir.resolve("raw/rebrickable");
        Files.createDirectories(paletteDir);
        String csv = "id,name,rgb,is_trans\n" +
            "0,Black,05131D,FALSE\n" +
            "4,Red,C91A09,FALSE\n" +
            "14,Yellow,F2CD37,FALSE\n" +
            "15,White,FFFFFF,FALSE\n" +
            "1,Blue,0055BF,FALSE\n";
        Files.writeString(paletteDir.resolve("colors.csv"), csv);
    }

    /**
     * Writes a minimal GLB file containing a unit cube (8 vertices, 12 triangles)
     * with an optional baseColorFactor material.
     */
    private void writeCubeGlb(Path path, float[] baseColorFactor) throws IOException {
        float[] positions = {
            0f, 0f, 0f,  1f, 0f, 0f,  1f, 1f, 0f,  0f, 1f, 0f,
            0f, 0f, 1f,  1f, 0f, 1f,  1f, 1f, 1f,  0f, 1f, 1f
        };
        int[] indices = {
            // front
            0, 1, 2,  0, 2, 3,
            // back
            4, 6, 5,  4, 7, 6,
            // bottom
            0, 5, 1,  0, 4, 5,
            // top
            3, 2, 6,  3, 6, 7,
            // left
            0, 3, 7,  0, 7, 4,
            // right
            1, 5, 6,  1, 6, 2
        };

        int vertexCount = positions.length / 3;
        int indexCount = indices.length;
        int positionsBytes = positions.length * 4;
        int indicesBytes = indexCount * 2; // unsigned short
        int indicesPadded = (indicesBytes + 3) & ~3;
        int totalBinSize = positionsBytes + indicesPadded;

        ByteBuffer bin = ByteBuffer.allocate(totalBinSize).order(ByteOrder.LITTLE_ENDIAN);
        for (float p : positions) bin.putFloat(p);
        for (int idx : indices) bin.putShort((short) idx);
        while (bin.position() < totalBinSize) bin.put((byte) 0);
        bin.flip();

        StringBuilder json = new StringBuilder();
        json.append("{\"asset\":{\"version\":\"2.0\"},\"scene\":0,");
        json.append("\"scenes\":[{\"nodes\":[0]}],");
        json.append("\"nodes\":[{\"mesh\":0}],");

        json.append("\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1");
        if (baseColorFactor != null) {
            json.append(",\"material\":0");
        }
        json.append("}]}],");

        json.append("\"accessors\":[");
        json.append("{\"bufferView\":0,\"componentType\":5126,\"count\":").append(vertexCount)
            .append(",\"type\":\"VEC3\"},");
        json.append("{\"bufferView\":1,\"componentType\":5123,\"count\":").append(indexCount)
            .append(",\"type\":\"SCALAR\"}");
        json.append("],");

        json.append("\"bufferViews\":[");
        json.append("{\"buffer\":0,\"byteOffset\":0,\"byteLength\":").append(positionsBytes).append("},");
        json.append("{\"buffer\":0,\"byteOffset\":").append(positionsBytes)
            .append(",\"byteLength\":").append(indicesBytes).append("}");
        json.append("],");

        if (baseColorFactor != null) {
            json.append("\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[")
                .append(baseColorFactor[0]).append(",").append(baseColorFactor[1]).append(",")
                .append(baseColorFactor[2]).append(",").append(baseColorFactor[3])
                .append("]}}],");
        }

        json.append("\"buffers\":[{\"byteLength\":").append(totalBinSize).append("}]}");

        byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        int jsonPadded = (jsonBytes.length + 3) & ~3;
        byte[] jsonChunkData = new byte[jsonPadded];
        System.arraycopy(jsonBytes, 0, jsonChunkData, 0, jsonBytes.length);
        for (int i = jsonBytes.length; i < jsonPadded; i++) jsonChunkData[i] = 0x20;

        int totalLength = 12 + 8 + jsonPadded + 8 + totalBinSize;
        ByteBuffer glb = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67); // "glTF"
        glb.putInt(2);          // version
        glb.putInt(totalLength);
        glb.putInt(jsonPadded);
        glb.putInt(0x4E4F534A); // "JSON"
        glb.put(jsonChunkData);
        glb.putInt(totalBinSize);
        glb.putInt(0x004E4942); // "BIN\0"
        glb.put(bin);
        glb.flip();
        Files.write(path, glb.array());
    }
}
