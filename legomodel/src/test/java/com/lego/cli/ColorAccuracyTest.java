package com.lego.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.lego.data.CatalogConfig;

/**
 * Color accuracy regression tests.
 *
 * Each test builds a programmatic GLB model with known baseColorFactor
 * materials, runs it through the full pipeline (voxelize → sample → color →
 * export), and compares the resulting LDR color distribution against a
 * golden baseline.
 *
 * Colors are specified as sRGB hex strings matching the Rebrickable palette,
 * then converted to linear RGB for the GLB baseColorFactor (per glTF spec).
 *
 * The comparison ignores bricks colored with code 16 (uncolored fallback)
 * since boundary/sampling coverage artifacts at low resolution are a
 * geometric concern, not a color-pipeline concern.
 *
 * Run with {@code -DcolorAccuracy.update=true} to print actual distributions
 * instead of asserting — useful after intentional algorithm changes.
 */
class ColorAccuracyTest {

    @TempDir
    Path tempDir;

    /** Maximum allowed deviation per color (fraction of colored bricks). */
    private static final double MAX_DEVIATION = 0.03;

    private static final int RESOLUTION = 12;

    // -- Palette colors (sRGB hex → linear RGB baseColorFactor) --
    private static final float[] BLACK      = srgbHexToLinear("05131D");
    private static final float[] BLUE       = srgbHexToLinear("0055BF");
    private static final float[] GREEN      = srgbHexToLinear("237841");
    private static final float[] RED        = srgbHexToLinear("C91A09");
    private static final float[] YELLOW     = srgbHexToLinear("F2CD37");
    private static final float[] WHITE      = srgbHexToLinear("FFFFFF");
    private static final float[] ORANGE     = srgbHexToLinear("FE8A18");
    private static final float[] DARK_RED   = srgbHexToLinear("720E0F");

    // Darkened yellow for shadow simulation (~70% brightness)
    private static final float[] YELLOW_SHADOW = {
            YELLOW[0] * 0.7f, YELLOW[1] * 0.7f, YELLOW[2] * 0.7f, 1f
    };

    @BeforeEach
    void setup() throws IOException {
        setupCatalog(tempDir);
        setupPalette(tempDir);
    }

    // ===================================================================
    //  Scenario 1: Uniform single-color cubes — 100% one color
    // ===================================================================

    @ParameterizedTest(name = "uniformRed_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformRedCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("red.glb", RED), algo,
                Map.of(4, 1.0), "uniform red");
    }

    @ParameterizedTest(name = "uniformBlack_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformBlackCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("black.glb", BLACK), algo,
                Map.of(0, 1.0), "uniform black");
    }

    @ParameterizedTest(name = "uniformWhite_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformWhiteCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("white.glb", WHITE), algo,
                Map.of(15, 1.0), "uniform white");
    }

    @ParameterizedTest(name = "uniformYellow_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformYellowCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("yellow.glb", YELLOW), algo,
                Map.of(14, 1.0), "uniform yellow");
    }

    @ParameterizedTest(name = "uniformBlue_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformBlueCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("blue.glb", BLUE), algo,
                Map.of(1, 1.0), "uniform blue");
    }

    @ParameterizedTest(name = "uniformGreen_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void uniformGreenCube(String algo) throws IOException {
        assertColorDistribution(
                buildSingleCubeGlb("green.glb", GREEN), algo,
                Map.of(2, 1.0), "uniform green");
    }

    // ===================================================================
    //  Scenario 2: Two-color splits
    //  Golden baselines per algorithm (geometry causes ~61/39 or ~44/56
    //  splits at res 12 due to boundary voxel attribution).
    // ===================================================================

    @ParameterizedTest(name = "redBlue_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void twoColorRedBlue(String algo) throws IOException {
        var expected = switch (algo) {
            case "direct"            -> Map.of(4, 0.44, 1, 0.56);
            case "region", "dominant" -> Map.of(4, 0.61, 1, 0.39);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildTwoCubeGlb("rb.glb", RED, BLUE), algo,
                expected, "red-blue split");
    }

    @ParameterizedTest(name = "blackWhite_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void twoColorBlackWhite(String algo) throws IOException {
        // direct: boundary voxels produce LBGray artifact
        // region: over-merges to all-Black
        var expected = switch (algo) {
            case "direct"   -> Map.of(0, 0.44, 71, 0.18, 15, 0.39);
            case "region"   -> Map.of(0, 1.0);
            case "dominant" -> Map.of(0, 0.61, 15, 0.39);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildTwoCubeGlb("bw.glb", BLACK, WHITE), algo,
                expected, "black-white split");
    }

    @ParameterizedTest(name = "yellowGreen_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void twoColorYellowGreen(String algo) throws IOException {
        assertColorDistribution(
                buildTwoCubeGlb("yg.glb", YELLOW, GREEN), algo,
                Map.of(14, 0.61, 2, 0.39), "yellow-green split");
    }

    // ===================================================================
    //  Scenario 3: Three-color stripes
    //  direct: boundary artifacts produce Orange, LBGray, Tan
    //  region/dominant: cleaner but proportions skew toward first cube
    // ===================================================================

    @ParameterizedTest(name = "threeColor_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void threeColorStripes(String algo) throws IOException {
        var expected = switch (algo) {
            case "direct" -> Map.of(
                    4, 0.27, 25, 0.15, 14, 0.12,
                    71, 0.20, 1, 0.22, 19, 0.05);
            case "region", "dominant" -> Map.of(
                    4, 0.42, 14, 0.37, 1, 0.22);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildThreeCubeGlb("ryb.glb", RED, YELLOW, BLUE), algo,
                expected, "three-color stripes");
    }

    // ===================================================================
    //  Scenario 4: High-contrast boundary (DarkRed + White)
    //  direct: LBGray boundary artifact
    //  region: White mapped to LBGray
    //  dominant: clean
    // ===================================================================

    @ParameterizedTest(name = "highContrast_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void highContrastBoundary(String algo) throws IOException {
        var expected = switch (algo) {
            case "direct"   -> Map.of(320, 0.44, 71, 0.18, 15, 0.39);
            case "region"   -> Map.of(320, 0.61, 71, 0.39);
            case "dominant" -> Map.of(320, 0.61, 15, 0.39);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildTwoCubeGlb("hc.glb", DARK_RED, WHITE), algo,
                expected, "high-contrast boundary");
    }

    // ===================================================================
    //  Scenario 5: Similar hues (Red + Orange) — stay separate
    // ===================================================================

    @ParameterizedTest(name = "similarHue_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void similarButDistinctColors(String algo) throws IOException {
        var expected = switch (algo) {
            case "direct"            -> Map.of(4, 0.44, 25, 0.56);
            case "region", "dominant" -> Map.of(4, 0.61, 25, 0.39);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildTwoCubeGlb("ro.glb", RED, ORANGE), algo,
                expected, "similar-hue boundary");
    }

    // ===================================================================
    //  Scenario 6: Shadow simulation — region should merge darkened version
    // ===================================================================

    @Test
    void shadowSimulation_regionMerges() throws IOException {
        assertColorDistribution(
                buildTwoCubeGlb("shadow.glb", YELLOW, YELLOW_SHADOW), "region",
                Map.of(14, 1.0), "shadow-sim region merge");
    }

    // ===================================================================
    //  Scenario 7: Four-color quadrants
    //  direct: many boundary artifacts (Orange, DBGray from color mixing)
    //  region/dominant: Red dominates, Blue → DarkRed via region merging
    // ===================================================================

    @ParameterizedTest(name = "fourColor_{0}")
    @ValueSource(strings = { "direct", "region", "dominant" })
    void fourColorQuadrants(String algo) throws IOException {
        var expected = switch (algo) {
            case "direct" -> Map.of(
                    4, 0.13, 1, 0.26, 25, 0.10,
                    72, 0.19, 14, 0.19, 2, 0.12);
            case "region", "dominant" -> Map.of(
                    4, 0.50, 320, 0.19, 14, 0.19, 2, 0.12);
            default -> throw new IllegalArgumentException(algo);
        };
        assertColorDistribution(
                buildFourCubeGlb("quad.glb", RED, BLUE, YELLOW, GREEN), algo,
                expected, "four-color quadrants");
    }

    // ===================================================================
    //  Core comparison
    // ===================================================================

    private void assertColorDistribution(Path glb, String algorithm,
                                          Map<Integer, Double> expected,
                                          String scenario) throws IOException {
        Path ldr = tempDir.resolve(scenario.replace(' ', '_') + ".ldr");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.run(new String[] {
                glb.toString(),
                String.valueOf(RESOLUTION),
                ldr.toString(), "ldraw", "topological",
                "--color-mode=glb-color",
                "--color-algorithm=" + algorithm,
                "--color-list"
        }, new PrintStream(out), new PrintStream(err), tempDir);

        assertEquals(0, exit, scenario + " pipeline failed: " + err);
        assertTrue(Files.exists(ldr), scenario + ": no LDR file");

        // Parse LDR and compute fractions excluding code 16 (uncolored)
        Map<Integer, Integer> counts = parseLdrColorCounts(Files.readString(ldr));
        int colored = counts.entrySet().stream()
                .filter(e -> e.getKey() != 16)
                .mapToInt(Map.Entry::getValue).sum();
        assertTrue(colored > 0, scenario + ": no colored bricks");

        Map<Integer, Double> actual = new LinkedHashMap<>();
        for (var e : counts.entrySet()) {
            if (e.getKey() != 16) {
                actual.put(e.getKey(), (double) e.getValue() / colored);
            }
        }

        if (Boolean.getBoolean("colorAccuracy.update")) {
            System.out.printf("%n=== %s [%s] ===%n", scenario, algorithm);
            System.out.printf("Colored bricks: %d  (uncolored: %d)%n",
                    colored, counts.getOrDefault(16, 0));
            actual.forEach((code, frac) ->
                    System.out.printf("  Code %3d: %.3f (%d)%n",
                            code, frac, counts.get(code)));
            return;
        }

        StringBuilder failures = new StringBuilder();

        // Expected colors present with correct proportion
        for (var e : expected.entrySet()) {
            double exp = e.getValue(), act = actual.getOrDefault(e.getKey(), 0.0);
            if (Math.abs(act - exp) > MAX_DEVIATION) {
                failures.append(String.format(
                        "  Color %d: expected %.1f%% got %.1f%%%n",
                        e.getKey(), exp * 100, act * 100));
            }
        }
        // Unexpected colors (> 5% of colored bricks)
        for (var e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey()) && e.getValue() > 0.05) {
                failures.append(String.format(
                        "  Unexpected color %d: %.1f%% (%d bricks)%n",
                        e.getKey(), e.getValue() * 100, counts.get(e.getKey())));
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder report = new StringBuilder();
            report.append(scenario).append(" [").append(algorithm).append("]:\n");
            report.append(failures);
            report.append("Actual (").append(colored).append(" colored bricks):\n");
            actual.forEach((code, frac) ->
                    report.append(String.format("  %3d: %.1f%%  ", code, frac * 100)));
            fail(report.toString());
        }
    }

    // ===================================================================
    //  sRGB → linear conversion
    // ===================================================================

    /** Converts sRGB hex (e.g. "C91A09") to linear RGB float[4] with alpha=1. */
    private static float[] srgbHexToLinear(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new float[] {
                srgbChannelToLinear(r / 255f),
                srgbChannelToLinear(g / 255f),
                srgbChannelToLinear(b / 255f),
                1f
        };
    }

    private static float srgbChannelToLinear(float c) {
        return c <= 0.04045f
                ? c / 12.92f
                : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // ===================================================================
    //  LDR parsing
    // ===================================================================

    private static final Pattern PART_LINE = Pattern.compile("^1\\s+(\\d+)\\s+");

    private Map<Integer, Integer> parseLdrColorCounts(String ldr) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (String line : ldr.split("\n")) {
            Matcher m = PART_LINE.matcher(line.trim());
            if (m.find()) {
                counts.merge(Integer.parseInt(m.group(1)), 1, Integer::sum);
            }
        }
        return counts;
    }

    // ===================================================================
    //  Catalog & palette setup
    // ===================================================================

    private void setupCatalog(Path dir) throws IOException {
        Path d = dir.resolve("data/catalog");
        Files.createDirectories(d);
        Files.writeString(d.resolve(CatalogConfig.CURATED_CATALOG_FILE),
                "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
                "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +
                "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
                "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
                "3024,Plate 1x1,14,Plates,1,1,1/3,Plastic,true\n" +
                "3023,Plate 1x2,14,Plates,1,2,1/3,Plastic,true\n" +
                "3022,Plate 2x2,14,Plates,2,2,1/3,Plastic,true\n");
    }

    private void setupPalette(Path dir) throws IOException {
        Path d = dir.resolve("raw/rebrickable");
        Files.createDirectories(d);
        Files.writeString(d.resolve("colors.csv"),
                "id,name,rgb,is_trans\n" +
                "0,Black,05131D,FALSE\n" +
                "1,Blue,0055BF,FALSE\n" +
                "2,Green,237841,FALSE\n" +
                "4,Red,C91A09,FALSE\n" +
                "14,Yellow,F2CD37,FALSE\n" +
                "15,White,FFFFFF,FALSE\n" +
                "19,Tan,E4CD9E,FALSE\n" +
                "25,Orange,FE8A18,FALSE\n" +
                "71,Light Bluish Gray,A0A5A9,FALSE\n" +
                "72,Dark Bluish Gray,6C6E68,FALSE\n" +
                "320,Dark Red,720E0F,FALSE\n");
    }

    // ===================================================================
    //  GLB model builders
    // ===================================================================

    private Path buildSingleCubeGlb(String name, float[] color) throws IOException {
        Path p = tempDir.resolve(name);
        writeCubeGlb(p, List.of(new CubeDef(0f, 0f, 0f, 1f, color)));
        return p;
    }

    private Path buildTwoCubeGlb(String name, float[] c1, float[] c2) throws IOException {
        Path p = tempDir.resolve(name);
        writeCubeGlb(p, List.of(
                new CubeDef(0f, 0f, 0f, 1f, c1),
                new CubeDef(1f, 0f, 0f, 1f, c2)));
        return p;
    }

    private Path buildThreeCubeGlb(String name, float[] c1, float[] c2, float[] c3) throws IOException {
        Path p = tempDir.resolve(name);
        writeCubeGlb(p, List.of(
                new CubeDef(0f, 0f, 0f, 1f, c1),
                new CubeDef(1f, 0f, 0f, 1f, c2),
                new CubeDef(2f, 0f, 0f, 1f, c3)));
        return p;
    }

    private Path buildFourCubeGlb(String name, float[] c1, float[] c2, float[] c3, float[] c4) throws IOException {
        Path p = tempDir.resolve(name);
        writeCubeGlb(p, List.of(
                new CubeDef(0f, 0f, 0f, 1f, c1),
                new CubeDef(1f, 0f, 0f, 1f, c2),
                new CubeDef(0f, 0f, 1f, 1f, c3),
                new CubeDef(1f, 0f, 1f, 1f, c4)));
        return p;
    }

    // ===================================================================
    //  Multi-primitive GLB writer
    // ===================================================================

    private record CubeDef(float ox, float oy, float oz, float size, float[] color) {}

    private void writeCubeGlb(Path path, List<CubeDef> cubes) throws IOException {
        int verticesPerCube = 8;
        int indicesPerCube = 36;
        int posBytesPerCube = verticesPerCube * 3 * 4;
        int idxBytesPerCube = indicesPerCube * 2;
        int idxBytesPadded = (idxBytesPerCube + 3) & ~3;
        int n = cubes.size();
        int totalPosBin = n * posBytesPerCube;
        int totalIdxBin = n * idxBytesPadded;
        int totalBin = totalPosBin + totalIdxBin;

        float[] basePos = {
                0f,0f,0f, 1f,0f,0f, 1f,1f,0f, 0f,1f,0f,
                0f,0f,1f, 1f,0f,1f, 1f,1f,1f, 0f,1f,1f
        };
        int[] baseIdx = {
                0,1,2, 0,2,3,  4,6,5, 4,7,6,
                0,5,1, 0,4,5,  3,2,6, 3,6,7,
                0,3,7, 0,7,4,  1,5,6, 1,6,2
        };

        ByteBuffer bin = ByteBuffer.allocate(totalBin).order(ByteOrder.LITTLE_ENDIAN);
        for (CubeDef c : cubes) {
            for (int i = 0; i < basePos.length; i += 3) {
                bin.putFloat(basePos[i] * c.size + c.ox);
                bin.putFloat(basePos[i+1] * c.size + c.oy);
                bin.putFloat(basePos[i+2] * c.size + c.oz);
            }
        }
        for (int ci = 0; ci < n; ci++) {
            for (int idx : baseIdx) bin.putShort((short) idx);
            int pad = idxBytesPadded - idxBytesPerCube;
            for (int p = 0; p < pad; p++) bin.put((byte) 0);
        }
        bin.flip();

        StringBuilder j = new StringBuilder();
        j.append("{\"asset\":{\"version\":\"2.0\"},\"scene\":0,");
        j.append("\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}],");

        // Primitives
        j.append("\"meshes\":[{\"primitives\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) j.append(",");
            j.append("{\"attributes\":{\"POSITION\":").append(i*2)
             .append("},\"indices\":").append(i*2+1)
             .append(",\"material\":").append(i).append("}");
        }
        j.append("]}],");

        // Accessors
        j.append("\"accessors\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) j.append(",");
            CubeDef c = cubes.get(i);
            j.append("{\"bufferView\":").append(i*2)
             .append(",\"componentType\":5126,\"count\":").append(verticesPerCube)
             .append(",\"type\":\"VEC3\"")
             .append(",\"min\":[").append(c.ox).append(",").append(c.oy).append(",").append(c.oz).append("]")
             .append(",\"max\":[").append(c.ox+c.size).append(",").append(c.oy+c.size).append(",").append(c.oz+c.size).append("]")
             .append("},");
            j.append("{\"bufferView\":").append(i*2+1)
             .append(",\"componentType\":5123,\"count\":").append(indicesPerCube)
             .append(",\"type\":\"SCALAR\"}");
        }
        j.append("],");

        // BufferViews
        j.append("\"bufferViews\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) j.append(",");
            j.append("{\"buffer\":0,\"byteOffset\":").append(i * posBytesPerCube)
             .append(",\"byteLength\":").append(posBytesPerCube).append("},");
            j.append("{\"buffer\":0,\"byteOffset\":").append(totalPosBin + i * idxBytesPadded)
             .append(",\"byteLength\":").append(idxBytesPerCube).append("}");
        }
        j.append("],");

        // Materials
        j.append("\"materials\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) j.append(",");
            float[] c = cubes.get(i).color;
            j.append("{\"pbrMetallicRoughness\":{\"baseColorFactor\":[")
             .append(c[0]).append(",").append(c[1]).append(",")
             .append(c[2]).append(",").append(c[3]).append("]}}");
        }
        j.append("],");

        j.append("\"buffers\":[{\"byteLength\":").append(totalBin).append("}]}");

        byte[] jsonBytes = j.toString().getBytes(StandardCharsets.UTF_8);
        int jsonPad = (jsonBytes.length + 3) & ~3;
        byte[] jsonChunk = new byte[jsonPad];
        System.arraycopy(jsonBytes, 0, jsonChunk, 0, jsonBytes.length);
        for (int i = jsonBytes.length; i < jsonPad; i++) jsonChunk[i] = 0x20;

        int total = 12 + 8 + jsonPad + 8 + totalBin;
        ByteBuffer glb = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67);  // glTF
        glb.putInt(2);
        glb.putInt(total);
        glb.putInt(jsonPad);
        glb.putInt(0x4E4F534A);  // JSON
        glb.put(jsonChunk);
        glb.putInt(totalBin);
        glb.putInt(0x004E4942);  // BIN
        glb.put(bin);
        glb.flip();
        Files.write(path, glb.array());
    }
}
