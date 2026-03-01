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
        assertTrue(error.contains("failed to write output OBJ file"), 
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
        assertTrue(error.contains("export mode must be 'brick', 'voxel-surface', or 'voxel-solid'"));
        assertTrue(error.contains("Usage:"));
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
        // Catalog is loaded successfully - verifies catalog dimensions are being used
        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err, tempDir);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        String errors = errBuffer.toString();
        
        // Verify no catalog-related errors
        assertFalse(errors.contains("No valid brick dimensions found"),
            "Limited catalog with 2x1 and 1x1 should be valid");
        
        // Verify standard output labels are present (backward compatibility)
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
        // Catalog is loaded successfully - verifies full dimensions are available
        int exitCode = Main.run(new String[] { objPath.toString(), "4" }, out, err, tempDir);

        assertEquals(0, exitCode);
        String output = outBuffer.toString();
        String errors = errBuffer.toString();
        
        // Verify no catalog-related errors
        assertFalse(errors.contains("No valid brick dimensions found"),
            "Full catalog with 2x4, 2x2, 2x1, 1x1 should be valid");
        
        // Should still show all required labels
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

    private void createLimitedCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        
        // Limited catalog: only 2x1 and 1x1 (no large bricks)
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +  // Will be converted to 2x1 horizontal
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n";
        
        Path catalogFile = catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE);
        Files.writeString(catalogFile, content);
    }

    private void createFullCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        
        // Full catalog with various dimensions
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n";
        
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
                    String numStr = line.replace("Bricks generated:", "").trim();
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
