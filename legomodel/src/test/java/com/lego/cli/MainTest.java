package com.lego.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.data.CatalogConfig;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void testValidInvocationPrintsRequiredLabels() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        assertTrue(output.contains("Triangles:"));
        assertTrue(output.contains("Resolution:"));
        assertTrue(output.contains("Total voxels:"));
        assertTrue(output.contains("Filled voxels (solid):"));
        assertTrue(output.contains("Surface voxels:"));
        assertTrue(output.contains("Bricks generated:"));
    }

    @Test
    void testValidInvocationWithOutputPathWritesObj() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("bricks.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString() }, out, err);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        String output = outBuffer.toString();
        assertTrue(output.contains("Visual OBJ exported (brick):"));
    }

    @Test
    void testInvalidArgCountPrintsUsageAndFails() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "onlyOneArg" }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("Usage:"));
    }

    @Test
    void testHelpFlagPrintsUsageAndSucceeds() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "--help" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        assertTrue(output.contains("Usage:"));
        assertTrue(output.contains("--help"));
        assertTrue(output.contains("--color-mode"));
        assertTrue(output.contains("--placement-policy"));
        assertEquals("", errBuffer.toString(), "Help should not write to stderr");
    }

    @Test
    void testShortHelpFlagPrintsUsageAndSucceeds() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "-h" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        assertTrue(output.contains("Usage:"));
    }

    @Test
    void testHelpFlagWithOtherArgsStillShowsHelp() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "model.obj", "50", "--help" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        assertTrue(output.contains("Usage:"));
    }

    @Test
    void testNonIntegerResolutionFails() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "model.obj", "abc" }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("resolution must be an integer"));
        assertTrue(error.contains("Usage:"));
    }

    @Test
    void testResolutionLessThanTwoFails() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "model.obj", "1" }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("resolution must be >= 2"));
        assertTrue(error.contains("Usage:"));
    }

    @Test
    void testInvalidObjPathFails() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { "does_not_exist.obj", "4" }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("failed to read OBJ file"));
    }

    @Test
    void testOutputPathWriteFailureShowsCorrectMessage() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);
        
        // Create a read-only directory to trigger write failure
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectory(readOnlyDir);
        readOnlyDir.toFile().setReadOnly();
        Path outObj = readOnlyDir.resolve("subdir/bricks.obj");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString() }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("failed to write output file"),
            "Expected write error message, got: " + error);
    }

    @Test
    void testExportModeVoxelSurfaceWritesObj() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("voxels_surface.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString(), "voxel-surface" }, out, err);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        String output = outBuffer.toString();
        assertTrue(output.contains("Visual OBJ exported (voxel-surface):"));
        String content = Files.readString(outObj);
        assertTrue(content.contains("# LEGO Architecture Engine voxel export"));
    }

    @Test
    void testExportModeVoxelSolidWritesObj() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("voxels_solid.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString(), "voxel-solid" }, out, err);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        String output = outBuffer.toString();
        assertTrue(output.contains("Visual OBJ exported (voxel-solid):"));
        String content = Files.readString(outObj);
        assertTrue(content.contains("# LEGO Architecture Engine voxel export"));
    }

    @Test
    void testExportModeBrickWritesObjWithCorrectMessage() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("bricks_explicit.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString(), "brick" }, out, err);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        String output = outBuffer.toString();
        assertTrue(output.contains("Visual OBJ exported (brick):"));
        String content = Files.readString(outObj);
        assertTrue(content.contains("# LEGO Architecture Engine brick export"));
    }

    @Test
    void testInvalidExportModeFails() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("out.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString(), "invalid-mode" }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("export mode must be 'brick', 'voxel-surface', 'voxel-solid', or 'ldraw'"));
        assertTrue(error.contains("Usage:"));
    }

    @Test
    void testExportModeLDrawWritesLdr() throws IOException {
        Path objPath = tempDir.resolve("cube.obj");
        Path outLdr = tempDir.resolve("model.ldr");
        createCubeObj(objPath);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "12", outLdr.toString(), "ldraw" }, out, err);

        assertEquals(0, exitCode, "Expected success. Errors: " + errBuffer);
        assertTrue(Files.exists(outLdr));
        String content = Files.readString(outLdr);
        assertTrue(content.contains("0 LEGO Architecture Engine LDraw export"));
        assertTrue(content.lines().anyMatch(line -> line.startsWith("1 ")), "Expected at least one part placement line");
    }

    @Test
    void testInvalidVoxelizerModeFails() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("out.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(
            new String[] { objPath.toString(), "4", outObj.toString(), "brick", "invalid-voxelizer" },
            out,
            err
        );

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("voxelizer mode must be 'legacy' or 'topological'"));
        assertTrue(error.contains("Usage:"));
    }

    @Test
    void testTopologicalVoxelizerModeIsNowImplemented() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("out.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        // Topological voxelizer is now implemented
        int exitCode = Main.run(
            new String[] { objPath.toString(), "4", outObj.toString(), "brick", "topological" },
            out,
            err
        );

        // Should succeed (no longer returns error for "not implemented")
        assertEquals(0, exitCode, "Topological mode should succeed. Errors: " + errBuffer.toString());
    }

    @Test
    void testBackwardCompatibilityDefaultsToBrickMode() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("default_bricks.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        // No 4th argument - should default to brick mode
        int exitCode = Main.run(new String[] { objPath.toString(), "4", outObj.toString() }, out, err);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        String output = outBuffer.toString();
        assertTrue(output.contains("Visual OBJ exported (brick):"));
        String content = Files.readString(outObj);
        assertTrue(content.contains("# LEGO Architecture Engine brick export"));
    }

    @Test
    void testExplicitLegacyVoxelizerModeWorks() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("legacy_mode.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(
            new String[] { objPath.toString(), "4", outObj.toString(), "brick", "legacy" },
            out,
            err
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(outObj));
        assertTrue(outBuffer.toString().contains("Visual OBJ exported (brick):"));
    }

    @Test
    void testBlockTypeSummaryAppearsInOutput() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        
        // Verify that "Block types used:" appears in output
        assertTrue(output.contains("Block types used:"), 
            "Expected 'Block types used:' in output:\n" + output);
        
        // Verify that block type entries with partId and count appear
        assertTrue(output.contains("x"),
            "Expected block type entries with count in output:\n" + output);
        
        // Verify ordering: "Block types used:" comes after "Bricks generated:"
        int bricksIdx = output.indexOf("Bricks generated:");
        int blockTypesIdx = output.indexOf("Block types used:");
        assertTrue(bricksIdx >= 0 && blockTypesIdx > bricksIdx,
            "Expected 'Block types used:' to appear after 'Bricks generated:' in output:\n" + output);
    }

    @Test
    void testBlockTypeSummaryCountsAreCorrect() throws IOException {
        Path objPath = tempDir.resolve("cube.obj");
        // Create a simple cube with 8 vertices forming 12 triangles (2 per face)
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            v 1 1 0
            v 0 0 1
            v 1 0 1
            v 0 1 1
            v 1 1 1
            f 1 2 3
            f 2 4 3
            f 1 2 6
            f 1 6 5
            f 1 3 7
            f 1 7 5
            f 4 2 6
            f 4 6 8
            f 4 3 7
            f 4 7 8
            f 5 6 8
            f 5 8 7
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        
        // Check that the output contains "Block types used:"
        assertTrue(output.contains("Block types used:"), 
            "Expected 'Block types used:' in output:\n" + output);
        
        // Parse the output to verify block type counts
        String[] lines = output.split("\n");
        boolean foundBlockTypesSection = false;
        int totalBlocksFromSummary = 0;
        
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("Block types used:")) {
                foundBlockTypesSection = true;
                // Parse subsequent lines with format: "  partId  name   xCount"
                for (int j = i + 1; j < lines.length; j++) {
                    String line = lines[j].trim();
                    if (line.isEmpty() || !line.contains("x")) {
                        break;
                    }
                    // Extract count from "x123" at end of line
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("x(\\d+)\\s*$").matcher(line);
                    if (m.find()) {
                        totalBlocksFromSummary += Integer.parseInt(m.group(1));
                    }
                }
                break;
            }
        }
        
        assertTrue(foundBlockTypesSection, "Did not find 'Block types used:' section in output");
        
        // Extract the brick count from "Bricks generated: N"
        int generatedBrickCount = 0;
        for (String line : lines) {
            if (line.contains("Bricks generated:")) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        generatedBrickCount = Integer.parseInt(parts[1].trim().split("\\s+")[0]);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                break;
            }
        }
        
        // Verify that the sum of all block type counts equals the brick count
        assertEquals(generatedBrickCount, totalBlocksFromSummary,
            "Sum of block type counts should equal total brick count. Output:\n" + output);
    }

    // ========== CLI-LEVEL CONFIDENCE TEST ==========

    @Test
    void testCliWithCatalogDrivenDimensions_ProducesCorrectOutput() throws IOException {
        // Create temporary catalog with limited dimensions (no 2x4, no 2x2)
        createLimitedCatalog(tempDir);

        Path objPath = tempDir.resolve("triangle.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        // Run CLI with temporary catalog base dir (test-only overload)
        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err, tempDir);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        String errors = errBuffer.toString();

        assertFalse(errors.contains("No valid brick dimensions found"),
            "Limited catalog with 2x1 and 1x1 should be valid");
        assertTrue(output.contains("Triangles:"));
        assertTrue(output.contains("Resolution:"));
        assertTrue(output.contains("Surface voxels:"));
        assertTrue(output.contains("Bricks generated:"));
    }

    @Test
    void testCliWithFullCatalog_UsesLargeBricksWhenAvailable() throws IOException {
        // Create temporary catalog with full dimensions including 2x4 and 2x2
        createFullCatalog(tempDir);

        Path objPath = tempDir.resolve("triangle.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        // Run CLI with full catalog (test-only overload)
        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err, tempDir);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        String errors = errBuffer.toString();

        assertFalse(errors.contains("No valid brick dimensions found"),
            "Full catalog with 2x4, 2x2, 2x1, 1x1 should be valid");
        assertTrue(output.contains("Bricks generated:"));
        assertTrue(output.contains("Surface voxels:"));
    }

    @Test
    void testCliCatalogLimitedVsFull_ProducesConcreteBrickCountDifference() throws IOException {
        Path objPath = tempDir.resolve("cube.obj");
        createCubeObj(objPath);

        Path limitedBase = tempDir.resolve("limitedCatalog");
        Path fullBase = tempDir.resolve("fullCatalog");
        createLimitedCatalog(limitedBase);
        createFullCatalog(fullBase);

        ByteArrayOutputStream limitedOutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream limitedErrBuffer = new ByteArrayOutputStream();
        int limitedExit = Main.run(
            new String[] { objPath.toString(), "12" },
            new PrintStream(limitedOutBuffer),
            new PrintStream(limitedErrBuffer),
            limitedBase
        );

        ByteArrayOutputStream fullOutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream fullErrBuffer = new ByteArrayOutputStream();
        int fullExit = Main.run(
            new String[] { objPath.toString(), "12" },
            new PrintStream(fullOutBuffer),
            new PrintStream(fullErrBuffer),
            fullBase
        );

        assertEquals(0, limitedExit);
        assertEquals(0, fullExit);
        assertTrue(limitedErrBuffer.toString().isBlank());
        assertTrue(fullErrBuffer.toString().isBlank());

        int limitedCount = extractBrickCount(limitedOutBuffer.toString());
        int fullCount = extractBrickCount(fullOutBuffer.toString());

        assertTrue(limitedCount > 0, "Limited catalog should produce bricks");
        assertTrue(fullCount > 0, "Full catalog should produce bricks");
        assertTrue(fullCount < limitedCount,
            "Expected full catalog to use fewer bricks via larger parts; limited=" +
            limitedCount + ", full=" + fullCount);
    }

    @Test
    void testAnalyzeSteppingWritesMetricsFiles() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("triangle_out.obj");
        Path analysisDir = tempDir.resolve("analysis");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {
                objPath.toString(),
                "10",
                outObj.toString(),
                "brick",
                "legacy",
                "--analyze-stepping",
                "--analysis-dir",
                analysisDir.toString()
            },
            new PrintStream(outBuffer),
            new PrintStream(errBuffer)
        );

        assertEquals(0, exitCode, "Expected analysis mode to succeed. Errors: " + errBuffer);
        assertTrue(Files.exists(analysisDir.resolve("stepping_metrics.json")));
        assertTrue(Files.exists(analysisDir.resolve("stepping_layers.csv")));
    }

    @Test
    void testAnalyzeSteppingSweepWritesComparativeFiles() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path outObj = tempDir.resolve("triangle_sweep.obj");
        Path analysisDir = tempDir.resolve("analysis_sweep");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = Main.run(
            new String[] {
                objPath.toString(),
                "10",
                outObj.toString(),
                "brick",
                "legacy",
                "--analyze-stepping",
                "--analysis-dir=" + analysisDir,
                "--sweep=10,20"
            },
            new PrintStream(outBuffer),
            new PrintStream(errBuffer)
        );

        assertEquals(0, exitCode, "Expected sweep analysis mode to succeed. Errors: " + errBuffer);
        assertTrue(Files.exists(analysisDir.resolve("stepping_sweep.json")));
        assertTrue(Files.exists(analysisDir.resolve("stepping_sweep.csv")));
        assertTrue(Files.exists(analysisDir.resolve("resolution_10").resolve("stepping_metrics.json")));
        assertTrue(Files.exists(analysisDir.resolve("resolution_20").resolve("stepping_layers.csv")));
    }

    // ========== COLOR MODE CLI TESTS ==========

    @Test
    void testColorModeGlbColorWithObjInputFails() throws IOException {
        Path objPath = tempDir.resolve("model.obj");
        Path outLdr = tempDir.resolve("model.ldr");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] {
            objPath.toString(), "4", outLdr.toString(), "ldraw",
            "--color-mode=glb-color"
        }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("glb-color"),
            "Should mention glb-color in error. Got: " + error);
        assertTrue(error.contains(".obj"),
            "Should mention .obj in error. Got: " + error);
    }

    @Test
    void testInvalidColorModeFails() throws IOException {
        Path objPath = tempDir.resolve("model.obj");
        Path outLdr = tempDir.resolve("model.ldr");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] {
            objPath.toString(), "4", outLdr.toString(), "ldraw",
            "--color-mode=invalid"
        }, out, err);

        assertEquals(1, exitCode);
        String error = errBuffer.toString();
        assertTrue(error.contains("Invalid --color-mode"),
            "Should show color mode error. Got: " + error);
    }

    @Test
    void testColorFallbackOptionIsParsedWithoutError() throws IOException {
        Path objPath = tempDir.resolve("model.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] {
            objPath.toString(), "4",
            "--color-fallback=15"
        }, out, err);

        assertEquals(0, exitCode,
            "color-fallback with default color mode should not cause errors. Err: " + errBuffer);
    }

    @Test
    void testColorModeNoneIsAccepted() throws IOException {
        Path objPath = tempDir.resolve("model.obj");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] {
            objPath.toString(), "4",
            "--color-mode=none"
        }, out, err);

        assertEquals(0, exitCode,
            "color-mode=none should be accepted. Err: " + errBuffer);
    }

    private void createLimitedCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);

        // Limited catalog: only 2x1 and 1x1 (no large bricks)
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1/3,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1/3,Plastic,true\n";

        Path catalogFile = catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE);
        Files.writeString(catalogFile, content);
    }

    private void createFullCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);

        // Full catalog with various dimensions
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1/3,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1/3,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1/3,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1/3,Plastic,true\n";

        Path catalogFile = catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE);
        Files.writeString(catalogFile, content);
    }

    private void createCubeObj(Path objPath) throws IOException {
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            v 0 0 1
            v 1 0 1
            v 1 1 1
            v 0 1 1

            f 1 2 3
            f 1 3 4

            f 5 7 6
            f 5 8 7

            f 1 5 6
            f 1 6 2

            f 4 3 7
            f 4 7 8

            f 1 4 8
            f 1 8 5

            f 2 6 7
            f 2 7 3
            """);
    }

    /**
     * Extracts brick count from CLI output.
     * Parses the "Bricks generated: N" line.
     *
     * @param output the CLI stdout output
     * @return the brick count, or 0 if not found
     */
    private int extractBrickCount(String output) {
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("Bricks generated:")) {
                try {
                    String numStr = line.replace("Bricks generated:", "").trim().split("\\s+")[0];
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    @Test
    void testColorListFlagWithLDrawExport() throws IOException {
        Path objPath = tempDir.resolve("triangle.obj");
        Path ldrPath = tempDir.resolve("output.ldr");
        Files.writeString(objPath, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """);

        createFullCatalog(tempDir);
        createTestPalette(tempDir);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer);
        PrintStream err = new PrintStream(errBuffer);

        int exitCode = Main.run(new String[] {
            objPath.toString(), "4", ldrPath.toString(), "ldraw",
            "--color-list"
        }, out, err, tempDir);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        assertTrue(output.contains("Color list"), 
            "Should output color list. Got: " + output);
        assertTrue(output.contains("bricks)"), 
            "Should show brick count per color. Got: " + output);
    }

    private void createTestPalette(Path baseDir) throws IOException {
        Path paletteDir = baseDir.resolve("raw/rebrickable");
        Files.createDirectories(paletteDir);
        String csv = "id,name,rgb,is_trans\n" +
            "0,Black,05131D,FALSE\n" +
            "1,Blue,0055BF,FALSE\n" +
            "4,Red,C91A09,FALSE\n" +
            "14,Yellow,F2CD37,FALSE\n" +
            "15,White,FFFFFF,FALSE\n" +
            "16,Main Colour,000000,FALSE\n";
        Files.writeString(paletteDir.resolve("colors.csv"), csv);
    }

}
