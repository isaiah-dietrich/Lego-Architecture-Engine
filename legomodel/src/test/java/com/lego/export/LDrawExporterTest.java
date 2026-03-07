package com.lego.export;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.data.CatalogConfig;
import com.lego.model.Brick;

class LDrawExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportWithColorCodesWritesPerBrickColors() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("colored.ldr");

        Brick b1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick b2 = new Brick(1, 0, 0, 1, 1, 1);

        Map<Brick, Integer> colorCodes = new HashMap<>();
        colorCodes.put(b1, 4);  // red
        colorCodes.put(b2, 1);  // blue

        LDrawExporter.export(List.of(b1, b2), ldr, tempDir, colorCodes);

        String content = Files.readString(ldr);
        List<String> partLines = content.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertEquals(2, partLines.size());
        assertTrue(partLines.get(0).startsWith("1 4 "),
            "First brick should have color 4 (red). Got: " + partLines.get(0));
        assertTrue(partLines.get(1).startsWith("1 1 "),
            "Second brick should have color 1 (blue). Got: " + partLines.get(1));
    }

    @Test
    void exportWithoutColorCodesUsesDefaultColor16() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("nocolor.ldr");

        Brick brick = new Brick(0, 0, 0, 1, 1, 1);

        LDrawExporter.export(List.of(brick), ldr, tempDir, null);

        String content = Files.readString(ldr);
        List<String> partLines = content.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertEquals(1, partLines.size());
        assertTrue(partLines.get(0).startsWith("1 16 "),
            "Without color codes, should use default color 16. Got: " + partLines.get(0));
    }

    @Test
    void exportWithPartialColorCodesUsesDefaultForMissing() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("partial.ldr");

        Brick b1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick b2 = new Brick(1, 0, 0, 1, 1, 1);

        Map<Brick, Integer> colorCodes = new HashMap<>();
        colorCodes.put(b1, 14);  // yellow — only b1 has a color

        LDrawExporter.export(List.of(b1, b2), ldr, tempDir, colorCodes);

        String content = Files.readString(ldr);
        List<String> partLines = content.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertEquals(2, partLines.size());
        assertTrue(partLines.get(0).startsWith("1 14 "),
            "First brick should have color 14. Got: " + partLines.get(0));
        assertTrue(partLines.get(1).startsWith("1 16 "),
            "Second brick (no color) should use default 16. Got: " + partLines.get(1));
    }

    @Test
    void exportHeaderContainsBrickCount() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("header.ldr");

        Brick b1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick b2 = new Brick(1, 0, 0, 1, 1, 1);
        Brick b3 = new Brick(2, 0, 0, 1, 1, 1);

        LDrawExporter.export(List.of(b1, b2, b3), ldr, tempDir, null);

        String content = Files.readString(ldr);
        assertTrue(content.contains("0 Bricks: 3"),
            "Header should contain brick count. Content:\n" + content);
    }

    @Test
    void exportColorCodesSpanFullRange() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("fullrange.ldr");

        Brick b1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick b2 = new Brick(1, 0, 0, 1, 1, 1);

        Map<Brick, Integer> colorCodes = new HashMap<>();
        colorCodes.put(b1, 0);    // black (lowest LDraw code)
        colorCodes.put(b2, 272);  // dark blue (higher code)

        LDrawExporter.export(List.of(b1, b2), ldr, tempDir, colorCodes);

        String content = Files.readString(ldr);
        List<String> partLines = content.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertTrue(partLines.get(0).startsWith("1 0 "),
            "Should handle color code 0. Got: " + partLines.get(0));
        assertTrue(partLines.get(1).startsWith("1 272 "),
            "Should handle high color code 272. Got: " + partLines.get(1));
    }

    private void createCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n";
        Files.writeString(catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE), content);
    }
}
