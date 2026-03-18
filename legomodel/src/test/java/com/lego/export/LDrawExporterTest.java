package com.lego.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.data.CatalogConfig;
import com.lego.model.Brick;
import com.lego.model.Facing;

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
            "3005,Brick 1x1,11,Bricks,1,1,1/3,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1/3,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1/3,Plastic,true\n";
        Files.writeString(catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE), content);
    }

    @Test
    void exportStackedBricksHaveNonOverlappingYPositions() throws IOException {
        createCatalog(tempDir);
        Path ldr = tempDir.resolve("stacked.ldr");

        // Three 1×1 bricks stacked on separate brick-height voxel layers
        // In the new architecture, each voxel layer = 1 plate height (8 LDU).
        // A full brick (heightUnits=3) spans 3 voxel layers = 24 LDU.
        // Adjacent bricks must therefore be placed 3 voxel layers apart.
        Brick bottom = new Brick(0, 0, 0, 1, 1, 3, "3005");
        Brick middle = new Brick(0, 3, 0, 1, 1, 3, "3005");
        Brick top    = new Brick(0, 6, 0, 1, 1, 3, "3005");

        LDrawExporter.export(List.of(bottom, middle, top), ldr, tempDir, null);

        String content = Files.readString(ldr);
        List<String> partLines = content.lines()
            .filter(l -> l.startsWith("1 "))
            .toList();

        assertEquals(3, partLines.size());

        // Extract Y positions from LDraw lines (field index 3: "1 color x Y z ...")
        double[] yPositions = new double[3];
        for (int i = 0; i < 3; i++) {
            String[] fields = partLines.get(i).split("\\s+");
            yPositions[i] = Double.parseDouble(fields[3]);
        }

        // Each brick is 24 LDU tall. Consecutive layers should be 24 LDU apart.
        double spacing01 = yPositions[0] - yPositions[1];
        double spacing12 = yPositions[1] - yPositions[2];

        assertEquals(24.0, spacing01, 0.001,
            "Layer 0→1 spacing should be 24 LDU (one brick height). Y values: "
            + yPositions[0] + ", " + yPositions[1]);
        assertEquals(24.0, spacing12, 0.001,
            "Layer 1→2 spacing should be 24 LDU (one brick height). Y values: "
            + yPositions[1] + ", " + yPositions[2]);
    }

    // ========== Slope rotation tests ==========

    /**
     * Creates a catalog that includes slope parts for rotation testing.
     */
    private void createSlopeCatalog(Path baseDir) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        String content = "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active,slope_angle,slope_direction\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1/3,Plastic,true,,\n" +
            "3039,Slope 45° 2x2,3,Bricks Sloped,2,2,1,Plastic,true,45.0,-y\n" +
            "3037,Slope 45° 2x4,3,Bricks Sloped,2,4,1,Plastic,true,45.0,-y\n";
        Files.writeString(catalogDir.resolve(CatalogConfig.CURATED_CATALOG_FILE), content);
    }

    /**
     * Extracts the 3×3 rotation matrix from an LDraw part line.
     * Returns "a b c d e f g h i" as a single string for comparison.
     */
    private String extractRotationMatrix(String partLine) {
        String[] fields = partLine.split("\\s+");
        // Format: 1 color x y z a b c d e f g h i partfile.dat
        // Indices: 0 1     2 3 4 5 6 7 8 9 10 11 12 13 14
        return String.join(" ", fields[5], fields[6], fields[7],
                                fields[8], fields[9], fields[10],
                                fields[11], fields[12], fields[13]);
    }

    @Test
    void slopeNorth_usesIdentityRotation() throws IOException {
        createSlopeCatalog(tempDir);
        Path ldr = tempDir.resolve("slope_north.ldr");

        // NORTH (-Z): identity rotation — LDraw default slope face points -Z
        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH);
        LDrawExporter.export(List.of(slope), ldr, tempDir, null);

        String content = Files.readString(ldr);
        String partLine = content.lines().filter(l -> l.startsWith("1 ")).findFirst().orElseThrow();
        assertEquals("1 0 0 0 1 0 0 0 1", extractRotationMatrix(partLine),
            "NORTH should use identity rotation");
    }

    @Test
    void slopeSouth_usesY180Rotation() throws IOException {
        createSlopeCatalog(tempDir);
        Path ldr = tempDir.resolve("slope_south.ldr");

        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.SOUTH);
        LDrawExporter.export(List.of(slope), ldr, tempDir, null);

        String content = Files.readString(ldr);
        String partLine = content.lines().filter(l -> l.startsWith("1 ")).findFirst().orElseThrow();
        assertEquals("-1 0 0 0 1 0 0 0 -1", extractRotationMatrix(partLine),
            "SOUTH should use Y180 rotation");
    }

    @Test
    void slopeEast_usesY270Rotation() throws IOException {
        createSlopeCatalog(tempDir);
        Path ldr = tempDir.resolve("slope_east.ldr");

        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.EAST);
        LDrawExporter.export(List.of(slope), ldr, tempDir, null);

        String content = Files.readString(ldr);
        String partLine = content.lines().filter(l -> l.startsWith("1 ")).findFirst().orElseThrow();
        assertEquals("0 0 -1 0 1 0 1 0 0", extractRotationMatrix(partLine),
            "EAST should use Y270 rotation (maps -Z to +X)");
    }

    @Test
    void slopeWest_usesY90Rotation() throws IOException {
        createSlopeCatalog(tempDir);
        Path ldr = tempDir.resolve("slope_west.ldr");

        Brick slope = new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.WEST);
        LDrawExporter.export(List.of(slope), ldr, tempDir, null);

        String content = Files.readString(ldr);
        String partLine = content.lines().filter(l -> l.startsWith("1 ")).findFirst().orElseThrow();
        assertEquals("0 0 1 0 1 0 -1 0 0", extractRotationMatrix(partLine),
            "WEST should use Y90 rotation (maps -Z to -X)");
    }
}
