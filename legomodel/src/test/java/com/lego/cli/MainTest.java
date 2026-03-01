package com.lego.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
