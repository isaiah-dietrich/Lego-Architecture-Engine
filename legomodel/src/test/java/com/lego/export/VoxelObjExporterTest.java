package com.lego.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.voxel.VoxelGrid;

class VoxelObjExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void testNullGridThrows() {
        Path output = tempDir.resolve("out.obj");
        assertThrows(IllegalArgumentException.class, () -> VoxelObjExporter.export(null, output));
    }

    @Test
    void testNullPathThrows() {
        VoxelGrid grid = new VoxelGrid(1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> VoxelObjExporter.export(grid, null));
    }

    @Test
    void testSingleFilledVoxelProducesExpectedGeometry() throws IOException {
        Path output = tempDir.resolve("single.obj");
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        grid.setFilled(1, 1, 1, true);

        VoxelObjExporter.export(grid, output);

        String text = Files.readString(output);
        assertTrue(text.contains("# voxel_count 1"));
        assertEquals(8, countLinesStartingWith(text, "v "));
        assertEquals(12, countLinesStartingWith(text, "f "));
        assertTrue(text.contains("o voxel_0"));
    }

    @Test
    void testTwoFilledVoxelsProduceTwoObjects() throws IOException {
        Path output = tempDir.resolve("two.obj");
        VoxelGrid grid = new VoxelGrid(4, 4, 4);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(2, 2, 2, true);

        VoxelObjExporter.export(grid, output);

        String text = Files.readString(output);
        assertTrue(text.contains("# voxel_count 2"));
        assertEquals(16, countLinesStartingWith(text, "v "));
        assertEquals(24, countLinesStartingWith(text, "f "));
        assertTrue(text.contains("o voxel_0"));
        assertTrue(text.contains("o voxel_1"));
    }

    @Test
    void testEmptyGridProducesOnlyHeader() throws IOException {
        Path output = tempDir.resolve("empty.obj");
        VoxelGrid grid = new VoxelGrid(2, 2, 2);

        VoxelObjExporter.export(grid, output);

        String text = Files.readString(output);
        assertTrue(text.contains("# voxel_count 0"));
        assertEquals(0, countLinesStartingWith(text, "v "));
        assertEquals(0, countLinesStartingWith(text, "f "));
    }

    @Test
    void testVoxelCoordinatesMatchUnitCube() throws IOException {
        Path output = tempDir.resolve("coords.obj");
        VoxelGrid grid = new VoxelGrid(5, 5, 5);
        grid.setFilled(2, 3, 4, true); // Voxel at (2, 3, 4)

        VoxelObjExporter.export(grid, output);

        String text = Files.readString(output);
        String[] lines = text.split("\n");

        // Expected bounds: x=[2,3], y=[3,4], z=[4,5]
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

        assertEquals(2.0, minX, 0.001);
        assertEquals(3.0, maxX, 0.001);
        assertEquals(3.0, minY, 0.001);
        assertEquals(4.0, maxY, 0.001);
        assertEquals(4.0, minZ, 0.001);
        assertEquals(5.0, maxZ, 0.001);
    }

    @Test
    void testFaceIndicesValid() throws IOException {
        Path output = tempDir.resolve("indices.obj");
        VoxelGrid grid = new VoxelGrid(2, 2, 2);
        grid.setFilled(0, 0, 0, true);

        VoxelObjExporter.export(grid, output);

        String text = Files.readString(output);
        String[] lines = text.split("\n");

        int maxVertexIndex = 0;
        for (String line : lines) {
            if (line.startsWith("f ")) {
                String[] parts = line.substring(2).split(" ");
                for (String part : parts) {
                    int index = Integer.parseInt(part.trim());
                    assertTrue(index >= 1, "Face index must be >= 1");
                    assertTrue(index <= 8, "Face index must be <= 8 for single voxel");
                    maxVertexIndex = Math.max(maxVertexIndex, index);
                }
            }
        }
        assertEquals(8, maxVertexIndex);
    }

    @Test
    void testParentDirectoryCreation() throws IOException {
        Path output = tempDir.resolve("subdir/nested/output.obj");
        VoxelGrid grid = new VoxelGrid(1, 1, 1);
        grid.setFilled(0, 0, 0, true);

        VoxelObjExporter.export(grid, output);

        assertTrue(Files.exists(output));
        assertTrue(Files.exists(output.getParent()));
    }

    private static int countLinesStartingWith(String text, String prefix) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }
}
