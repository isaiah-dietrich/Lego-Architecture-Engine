package com.lego.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.Brick;

class BrickObjExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void testExportSingleBrickWritesExpectedGeometryCounts() throws IOException {
        Path output = tempDir.resolve("single.obj");
        List<Brick> bricks = List.of(new Brick(0, 0, 0, 1, 1, 1));

        BrickObjExporter.export(bricks, output);

        String text = Files.readString(output);
        assertTrue(text.contains("# brick_count 1"));
        assertEquals(8, countLinesStartingWith(text, "v "));
        assertEquals(12, countLinesStartingWith(text, "f "));
    }

    @Test
    void testExportMultipleBricksHasSeparateObjects() throws IOException {
        Path output = tempDir.resolve("multi.obj");
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 1, 1, 1),
            new Brick(1, 0, 0, 2, 1, 1)
        );

        BrickObjExporter.export(bricks, output);

        String text = Files.readString(output);
        assertTrue(text.contains("o brick_0"));
        assertTrue(text.contains("o brick_1"));
        assertEquals(16, countLinesStartingWith(text, "v "));
        assertEquals(24, countLinesStartingWith(text, "f "));
    }

    @Test
    void testNullBricksThrows() {
        Path output = tempDir.resolve("out.obj");
        assertThrows(IllegalArgumentException.class, () -> BrickObjExporter.export(null, output));
    }

    @Test
    void testNullOutputPathThrows() {
        List<Brick> bricks = List.of(new Brick(0, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> BrickObjExporter.export(bricks, null));
    }

    @Test
    void testNullBrickElementThrows() {
        Path output = tempDir.resolve("out.obj");
        List<Brick> bricks = java.util.Arrays.asList(
            new Brick(0, 0, 0, 1, 1, 1),
            null,
            new Brick(2, 0, 0, 1, 1, 1)
        );
        
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> BrickObjExporter.export(bricks, output)
        );
        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void testFaceIndicesValidForSingleBrick() throws IOException {
        Path output = tempDir.resolve("single.obj");
        List<Brick> bricks = List.of(new Brick(0, 0, 0, 1, 1, 1));

        BrickObjExporter.export(bricks, output);

        String text = Files.readString(output);
        String[] lines = text.split("\n");
        
        int maxVertexIndex = 0;
        for (String line : lines) {
            if (line.startsWith("f ")) {
                String[] parts = line.substring(2).split(" ");
                for (String part : parts) {
                    int index = Integer.parseInt(part.trim());
                    assertTrue(index >= 1, "Face index must be >= 1");
                    assertTrue(index <= 8, "Face index must be <= 8 for single brick");
                    maxVertexIndex = Math.max(maxVertexIndex, index);
                }
            }
        }
        assertEquals(8, maxVertexIndex);
    }

    @Test
    void testFaceIndicesValidForMultipleBricks() throws IOException {
        Path output = tempDir.resolve("multi.obj");
        List<Brick> bricks = List.of(
            new Brick(0, 0, 0, 1, 1, 1),
            new Brick(1, 0, 0, 2, 1, 1)
        );

        BrickObjExporter.export(bricks, output);

        String text = Files.readString(output);
        String[] lines = text.split("\n");
        
        int maxVertexIndex = 0;
        for (String line : lines) {
            if (line.startsWith("f ")) {
                String[] parts = line.substring(2).split(" ");
                for (String part : parts) {
                    int index = Integer.parseInt(part.trim());
                    assertTrue(index >= 1, "Face index must be >= 1");
                    assertTrue(index <= 16, "Face index must be <= 16 for two bricks");
                    maxVertexIndex = Math.max(maxVertexIndex, index);
                }
            }
        }
        assertEquals(16, maxVertexIndex);
    }

    @Test
    void testVertexCoordinatesMatchBrickBounds() throws IOException {
        Path output = tempDir.resolve("brick_2x1x1.obj");
        // Brick at (1, 2, 3) with dimensions 2x1x1
        Brick brick = new Brick(1, 2, 3, 2, 1, 1);
        List<Brick> bricks = List.of(brick);

        BrickObjExporter.export(bricks, output);

        String text = Files.readString(output);
        String[] lines = text.split("\n");
        
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        
        for (String line : lines) {
            if (line.startsWith("v ")) {
                String[] parts = line.substring(2).split(" ");
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        
        assertEquals(1, minX, 0.001);
        assertEquals(3, maxX, 0.001);  // 1 + 2
        assertEquals(2, minY, 0.001);
        assertEquals(3, maxY, 0.001);  // 2 + 1
        assertEquals(3, minZ, 0.001);
        assertEquals(4, maxZ, 0.001);  // 3 + 1
    }

    private static long countLinesStartingWith(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).count();
    }
}
