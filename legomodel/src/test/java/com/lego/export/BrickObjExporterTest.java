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

    private static long countLinesStartingWith(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).count();
    }
}
