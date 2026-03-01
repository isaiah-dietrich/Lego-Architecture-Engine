package com.lego.optimize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.optimize.AllowedBrickDimensions.Dimension;

/**
 * Tests for catalog-driven brick dimensions.
 */
class AllowedBrickDimensionsTest {

    private static final String VALID_CATALOG_HEADER = 
        "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n";

    @TempDir
    Path tempDir;

    @Test
    void testLoadFromCatalog_ParsesDimensions() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should have 4 dimensions: 2x4, 2x2, 2x1 (from 1x2), 1x1
        assertEquals(4, dimensions.size());
    }

    @Test
    void testLoadFromCatalog_SortsByAreaDescending() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +  // area=1
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +  // area=4
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n"   // area=8
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should be sorted by area descending: 2x4 (8), 2x2 (4), 1x1 (1)
        assertEquals(2, dimensions.get(0).studX());
        assertEquals(4, dimensions.get(0).studY());
        assertEquals(8, dimensions.get(0).area());

        assertEquals(2, dimensions.get(1).studX());
        assertEquals(2, dimensions.get(1).studY());
        assertEquals(4, dimensions.get(1).area());

        assertEquals(1, dimensions.get(2).studX());
        assertEquals(1, dimensions.get(2).studY());
        assertEquals(1, dimensions.get(2).area());
    }

    @Test
    void testLoadFromCatalog_FiltersHeight() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +        // height=1, included
            "3020,Plate 2x4,14,Plates,2,4,1/3,Plastic,true\n"        // height=1/3, excluded (also wrong category)
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should only have the brick (height=1), not the plate (height=1/3)
        assertEquals(1, dimensions.size());
        assertEquals(2, dimensions.get(0).studX());
        assertEquals(4, dimensions.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_FiltersCategory() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +         // Bricks, included
            "3039,Slope 45° 2x2,3,Bricks Sloped,2,2,1,Plastic,true\n" + // Sloped, excluded
            "3070b,Tile 1x1,19,Tiles,1,1,1,Plastic,true\n"           // Tiles, excluded
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should only have standard "Bricks", not slopes or tiles
        assertEquals(1, dimensions.size());
        assertEquals(2, dimensions.get(0).studX());
        assertEquals(4, dimensions.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_Converts1x2To2x1() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,true\n"
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should convert 1x2 to 2x1 horizontal (forbidden vertical orientation)
        assertEquals(1, dimensions.size());
        assertEquals(2, dimensions.get(0).studX());
        assertEquals(1, dimensions.get(0).studY());
    }

    @Test
    void testLoadFromCatalog_NoDuplicates() throws IOException {
        createCatalogFile(VALID_CATALOG_HEADER +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n"  // Duplicate
        );

        List<Dimension> dimensions = AllowedBrickDimensions.loadFromCatalog(tempDir);

        // Should deduplicate
        assertEquals(1, dimensions.size());
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
    void testDimension_CalculatesArea() {
        Dimension dim = new Dimension(2, 4);
        assertEquals(8, dim.area());
    }

    @Test
    void testDimension_ValidatesPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Dimension(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new Dimension(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new Dimension(-1, 4));
    }

    @Test
    void testDimension_Equality() {
        Dimension dim1 = new Dimension(2, 4);
        Dimension dim2 = new Dimension(2, 4);
        Dimension dim3 = new Dimension(4, 2);

        assertEquals(dim1, dim2);
        assertFalse(dim1.equals(dim3));
    }

    @Test
    void testDimension_ToString() {
        Dimension dim = new Dimension(2, 4);
        assertEquals("2x4", dim.toString());
    }

    private void createCatalogFile(String content) throws IOException {
        Path catalogDir = tempDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(catalogFile, content);
    }
}
