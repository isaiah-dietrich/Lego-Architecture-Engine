package com.lego.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for Cleaning (Prompt B behavior only).
 */
class CleaningTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseDimensions_Success_1x1() {
        Cleaning.ParsedDimensions parsed = Cleaning.parseDimensions("Brick 1x1");
        assertNotNull(parsed);
        assertEquals(1, parsed.studX());
        assertEquals(1, parsed.studY());
        assertEquals("1", parsed.heightUnits());
    }

    @Test
    void testParseDimensions_Success_1x2() {
        Cleaning.ParsedDimensions parsed = Cleaning.parseDimensions("Plate 1 x 2");
        assertNotNull(parsed);
        assertEquals(1, parsed.studX());
        assertEquals(2, parsed.studY());
        assertEquals("1", parsed.heightUnits());
    }

    @Test
    void testParseDimensions_Success_2x3xFiveSixths() {
        Cleaning.ParsedDimensions parsed = Cleaning.parseDimensions("Slope 2 x 3 x 5/6");
        assertNotNull(parsed);
        assertEquals(2, parsed.studX());
        assertEquals(3, parsed.studY());
        assertEquals("5/6", parsed.heightUnits());
    }

    @Test
    void testParseDimensions_Failure_NoDimensions() {
        assertNull(Cleaning.parseDimensions("Sticker Sheet"));
        assertNull(Cleaning.parseDimensions("Minifig Head"));
        assertNull(Cleaning.parseDimensions("Technic Axle"));
    }

    @Test
    void testParseDimensions_Failure_MalformedFraction() {
        assertNull(Cleaning.parseDimensions("Brick 1 x 2 x 5//6"));
        assertNull(Cleaning.parseDimensions("Brick 1 x 2 x 0/6"));
        assertNull(Cleaning.parseDimensions("Brick 1 x 2 x 5/0"));
    }

    @Test
    void testParseDimensions_Failure_ZeroDimensions() {
        assertNull(Cleaning.parseDimensions("Brick 0 x 2"));
        assertNull(Cleaning.parseDimensions("Brick 2 x 0"));
        assertNull(Cleaning.parseDimensions("Brick 0x0"));
    }

    @Test
    void testBuildCatalog_OutputSchemaHeader() throws IOException {
        Path inputPath = tempDir.resolve("test_input.csv");
        Path catalogPath = tempDir.resolve("parts_catalog_v1.csv");
        Path rejectedPath = tempDir.resolve("rejected_rows.csv");

        String csvContent = """
            part_num,name,part_cat_id,part_material
            3001,Brick 1x1,1,Plastic
            3002,Plate 1 x 2,1,Plastic
            3003,Slope 2 x 3 x 5/6,2,Plastic
            9001,Sticker Sheet,58,Paper
            """;

        Files.writeString(inputPath, csvContent, StandardCharsets.UTF_8);

        Cleaning cleaning = new Cleaning();
        cleaning.buildCatalog(inputPath.toFile(), catalogPath.toFile(), rejectedPath.toFile());

        assertTrue(Files.exists(catalogPath));
        List<String> lines = Files.readAllLines(catalogPath, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());
        assertEquals(
            "part_id,name,category,stud_x,stud_y,height_units,material,active",
            lines.get(0)
        );
    }

    @Test
    void testBuildCatalog_RowCountsInvariant() throws IOException {
        Path inputPath = tempDir.resolve("count_input.csv");
        Path catalogPath = tempDir.resolve("parts_catalog_v1.csv");
        Path rejectedPath = tempDir.resolve("rejected_rows.csv");

        String csvContent = """
            part_num,name,part_cat_id,part_material
            3001,Brick 1x1,1,Plastic
            3002,Plate 1 x 2,1,Plastic
            3003,Slope 2 x 3 x 5/6,2,Plastic
            9001,Sticker Sheet,58,Paper
            9002,Minifig Head,27,Plastic
            9003,Brick 1 x 0,1,Plastic
            """;

        Files.writeString(inputPath, csvContent, StandardCharsets.UTF_8);

        Cleaning cleaning = new Cleaning();
        cleaning.buildCatalog(inputPath.toFile(), catalogPath.toFile(), rejectedPath.toFile());

        long inputRows = Files.lines(inputPath).skip(1).count();
        long parsedRows = Files.lines(catalogPath).skip(1).count();
        long rejectedRows = Files.lines(rejectedPath).skip(1).count();

        assertEquals(inputRows, parsedRows + rejectedRows);
        assertEquals(3, parsedRows);
        assertEquals(3, rejectedRows);
    }

    @Test
    void testBuildCatalog_ParsedColumnsValues() throws IOException {
        Path inputPath = tempDir.resolve("value_input.csv");
        Path catalogPath = tempDir.resolve("parts_catalog_v1.csv");
        Path rejectedPath = tempDir.resolve("rejected_rows.csv");

        String csvContent = """
            part_num,name,part_cat_id,part_material
            101,Brick 1x1,10,Plastic
            102,Plate 1 x 2,11,Plastic
            103,Slope 2 x 3 x 5/6,12,Rubber
            """;

        Files.writeString(inputPath, csvContent, StandardCharsets.UTF_8);

        Cleaning cleaning = new Cleaning();
        cleaning.buildCatalog(inputPath.toFile(), catalogPath.toFile(), rejectedPath.toFile());

        List<String> lines = Files.readAllLines(catalogPath, StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).contains("101,Brick 1x1,10,1,1,1,Plastic,true"));
        assertTrue(lines.get(2).contains("102,Plate 1 x 2,11,1,2,1,Plastic,true"));
        assertTrue(lines.get(3).contains("103,Slope 2 x 3 x 5/6,12,2,3,5/6,Rubber,true"));
    }

    @Test
    void testResolvePreferredPathSupportsBothStyles() throws IOException {
        Path cwd = tempDir.resolve("workspace");
        Path dataDir = cwd.resolve("data");
        Path nestedDataDir = cwd.resolve("legomodel/data");
        Files.createDirectories(dataDir);
        Files.createDirectories(nestedDataDir);

        Path directFile = dataDir.resolve("parts_dimension_filtered.csv");
        Files.writeString(directFile, "part_num,name,part_cat_id,part_material\n", StandardCharsets.UTF_8);

        Path resolvedDirect = Cleaning.resolvePreferredPath(
            cwd,
            "data/parts_dimension_filtered.csv",
            "legomodel/data/parts_dimension_filtered.csv"
        );
        assertEquals(directFile, resolvedDirect);

        Files.delete(directFile);
        Path nestedFile = nestedDataDir.resolve("parts_dimension_filtered.csv");
        Files.writeString(nestedFile, "part_num,name,part_cat_id,part_material\n", StandardCharsets.UTF_8);

        Path resolvedNested = Cleaning.resolvePreferredPath(
            cwd,
            "data/parts_dimension_filtered.csv",
            "legomodel/data/parts_dimension_filtered.csv"
        );
        assertEquals(nestedFile, resolvedNested);
    }

    @Test
    void testResolveOutputPathsFromInput() {
        Path input = Path.of("/tmp/data/parts_dimension_filtered.csv");
        Path catalogPath = Cleaning.resolveCatalogOutputPath(input);
        Path rejectedPath = Cleaning.resolveRejectedOutputPath(input);

        assertNotNull(catalogPath);
        assertNotNull(rejectedPath);
        assertEquals("parts_catalog_v1.csv", catalogPath.getFileName().toString());
        assertEquals("rejected_rows.csv", rejectedPath.getFileName().toString());
    }
}
