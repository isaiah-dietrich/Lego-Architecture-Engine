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
        assertTrue(output.contains("Visual OBJ exported:"));
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
}
