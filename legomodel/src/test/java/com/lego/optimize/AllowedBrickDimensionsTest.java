package com.lego.optimize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Tests for catalog-driven brick dimensions.
 */
class AllowedBrickDimensionsTest {

    private static final String VALID_CATALOG_HEADER = 
        "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n";

    @TempDir
    Path tempDir;

    @Test
    void testLoadFromCatalog_ParsesSpecs() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should have 4 specs: 2x4, 2x2, 2x1 (from 1x2), 1x1
        assertEquals(4, specs.size());
    }

    @Test
    void testLoadFromCatalog_SortsByAreaDescending() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +  // area=1
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +  // area=4
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n"   // area=8
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should be sorted by area descending: 2x4 (8), 2x2 (4), 1x1 (1)
        assertEquals(2, specs.get(0).studX());
        assertEquals(4, specs.get(0).studY());
        assertEquals(8, specs.get(0).area());

        assertEquals(2, specs.get(1).studX());
        assertEquals(2, specs.get(1).studY());
        assertEquals(4, specs.get(1).area());

        assertEquals(1, specs.get(2).studX());
        assertEquals(1, specs.get(2).studY());
        assertEquals(1, specs.get(2).area());
    }

    @Test
    void testLoadFromCatalog_FiltersNonBrickCategory() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +        // Bricks category, included
            "3020,Plate 2x4,14,Plates,2,4,1/3,Plastic,true\n"        // Plates category, excluded
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should only have the brick (Bricks category), not the plate (Plates category)
        assertEquals(1, specs.size());
        assertEquals(2, specs.get(0).studX());
        assertEquals(4, specs.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_FiltersCategory() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +         // Bricks, included
            "3039,Slope 45° 2x2,3,Bricks Sloped,2,2,1,Plastic,true\n" + // Sloped, excluded
            "3070b,Tile 1x1,19,Tiles,1,1,1,Plastic,true\n"           // Tiles, excluded
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should only have standard "Bricks", not slopes or tiles
        assertEquals(1, specs.size());
        assertEquals(2, specs.get(0).studX());
        assertEquals(4, specs.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_OrderingPolicy_2x4Then2x2Then2x1Then1x1() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n"
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        assertEquals(4, specs.size());
        assertEquals(new BrickSpec(2, 4, 3, "Bricks", "3001"), specs.get(0));
        assertEquals(new BrickSpec(2, 2, 3, "Bricks", "3003"), specs.get(1));
        assertEquals(new BrickSpec(2, 1, 3, "Bricks", "3004"), specs.get(2));
        assertEquals(new BrickSpec(1, 1, 3, "Bricks", "3005"), specs.get(3));
    }

    @Test
    void testLoadFromCatalog_OrderingPolicy_ConvertsAndForbids1x2Vertical() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        assertTrue(specs.stream().anyMatch(s -> s.studX() == 2 && s.studY() == 1));
        assertFalse(specs.stream().anyMatch(s -> s.studX() == 1 && s.studY() == 2));
    }

    @Test
    void testLoadFromCatalog_Converts1x2To2x1() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n"
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should convert 1x2 to 2x1 horizontal (forbidden vertical orientation)
        assertEquals(1, specs.size());
        assertEquals(2, specs.get(0).studX());
        assertEquals(1, specs.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_NoDuplicates() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n"  // Duplicate
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should deduplicate
        assertEquals(1, specs.size());
    }

    @Test
    void testLoadFromCatalog_EmptyCatalog_Throws() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER);  // Header only, no data

        assertThrows(IllegalStateException.class,
            () -> AllowedBrickDimensions.loadFromCatalog(tempDir));
    }

    @Test
    void testLoadFromCatalog_NoValidBricks_Throws() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3020,Plate 2x4,14,Plates,2,4,1/3,Plastic,true\n"  // Plate, not Brick category
        );

        assertThrows(IllegalStateException.class,
            () -> AllowedBrickDimensions.loadFromCatalog(tempDir));
    }

    @Test
    void testBrickSpec_CalculatesArea() {
        BrickSpec spec = new BrickSpec(2, 4, 3, "Bricks", "3001");
        assertEquals(8, spec.area());
    }

    @Test
    void testBrickSpec_ValidatesPositive() {
        assertThrows(IllegalArgumentException.class, () -> new BrickSpec(0, 4, 3, "Bricks", "test"));
        assertThrows(IllegalArgumentException.class, () -> new BrickSpec(2, 0, 3, "Bricks", "test"));
        assertThrows(IllegalArgumentException.class, () -> new BrickSpec(-1, 4, 3, "Bricks", "test"));
    }

    @Test
    void testBrickSpec_Equality() {
        BrickSpec spec1 = new BrickSpec(2, 4, 3, "Bricks", "3001");
        BrickSpec spec2 = new BrickSpec(2, 4, 3, "Bricks", "3001");
        BrickSpec spec3 = new BrickSpec(4, 2, 3, "Bricks", "3001");

        assertEquals(spec1, spec2);
        assertFalse(spec1.equals(spec3));
    }

    @Test
    void testBrickSpec_ToString() {
        BrickSpec spec = new BrickSpec(2, 4, 3, "Bricks", "3001");
        assertEquals("2x4x3 (3001)", spec.toString());
    }

    // ========== FILTER FLEXIBILITY TESTS ==========

    @Test
    void testLoadFromCatalog_AcceptsHeight1Point0() throws IOException {
        // Verify that "1.0" height format is accepted (not just "1")
        createCatalogFile(
            "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1.0,Plastic,true\n" +  // 1.0 instead of 1
            "3005,Brick 1x1,11,Bricks,1,1,1.0,Plastic,true\n"    // 1.0 instead of 1
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should find both specs (height parsing is lenient)
        assertEquals(2, specs.size());
        assertTrue(specs.stream().anyMatch(s -> s.studX() == 2 && s.studY() == 4));
        assertTrue(specs.stream().anyMatch(s -> s.studX() == 1 && s.studY() == 1));
    }

    @Test
    void testLoadFromCatalog_CaseInsensitiveCategory() throws IOException {
        // Verify that category "Bricks" is case-insensitive
        createCatalogFile(
            "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11,bricks,2,4,1,Plastic,true\n" +      // lowercase "bricks"
            "3003,Brick 2x2,11,BRICKS,2,2,1,Plastic,true\n" +      // UPPERCASE "BRICKS"
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"        // Mixed case "Bricks"
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should find all three specs (case-insensitive match)
        assertEquals(3, specs.size());
    }

    @Test
    void testLoadFromCatalog_TrimsWhitespace() throws IOException {
        // Verify that leading/trailing whitespace is trimmed from category
        createCatalogFile(
            "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11, Bricks ,2,4,1,Plastic,true\n" +    // Whitespace around "Bricks"
            "3005,Brick 1x1,11,Bricks  ,1,1,1,Plastic,true\n"      // Trailing whitespace
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should find both specs (whitespace is trimmed)
        assertEquals(2, specs.size());
    }

    @Test
    void testLoadFromCatalog_NonBrickCategory_Filtered() throws IOException {
        // Verify that non-Bricks categories are filtered out
        createCatalogFile(
            "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +        // Valid: Bricks category
            "3010,Plate 2x4,12,Plates,2,4,1/3,Plastic,true\n" +      // Invalid: Plates category
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"          // Valid: Bricks category
        );

        List<BrickSpec> specs = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should find only the two with Bricks category
        assertEquals(2, specs.size());
    }

    private void createCatalogFile(String content) throws IOException {
        Path catalogDir = tempDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(catalogFile, content);
    }
}
