package com.lego.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.CatalogPart;

/**
 * Unit tests for CuratedCatalogLoader.
 */
class CuratedCatalogLoaderTest {

    @TempDir
    Path tempDir;

    private static final String VALID_HEADER = 
        "part_id,name,category,category_name,stud_x,stud_y,height_units,material,active\n";

    @Test
    void testLoadActiveParts_FiltersActiveOnly() throws IOException {
        // Arrange: Create catalog with mix of active and inactive parts
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3002,Brick 2x3,11,Bricks,2,3,1,Plastic,false\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n" +
            "3004,Brick 1x2,11,Bricks,1,2,1,Plastic,false\n" +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n"
        );

        // Act: Load only active parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: Only active=true parts are returned
        assertEquals(3, parts.size());
        assertTrue(parts.stream().allMatch(CatalogPart::active));
        assertEquals("3001", parts.get(0).partId());
        assertEquals("3003", parts.get(1).partId());
        assertEquals("3005", parts.get(2).partId());
    }

    @Test
    void testLoadAllParts_ReturnsAllParts() throws IOException {
        // Arrange: Create catalog with mix of active and inactive parts
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3002,Brick 2x3,11,Bricks,2,3,1,Plastic,false\n" +
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,true\n"
        );

        // Act: Load all parts regardless of active status
        List<CatalogPart> parts = CuratedCatalogLoader.loadAllParts(tempDir);

        // Assert: All parts are returned
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).active());
        assertFalse(parts.get(1).active());
        assertTrue(parts.get(2).active());
    }

    @Test
    void testLoadActiveParts_PreservesFileOrder() throws IOException {
        // Arrange: Create catalog with parts in specific order
        createCatalogFile(tempDir, VALID_HEADER +
            "3005,Brick 1x1,11,Bricks,1,1,1,Plastic,true\n" +
            "3020,Plate 2x4,14,Plates,2,4,1/3,Plastic,true\n" +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3023,Plate 1x2,14,Plates,1,2,1/3,Plastic,true\n"
        );

        // Act: Load parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: Order matches CSV file order
        assertEquals(4, parts.size());
        assertEquals("3005", parts.get(0).partId());
        assertEquals("3020", parts.get(1).partId());
        assertEquals("3001", parts.get(2).partId());
        assertEquals("3023", parts.get(3).partId());
    }

    @Test
    void testLoadActiveParts_ParsesAllFields() throws IOException {
        // Arrange: Create catalog with well-defined part
        createCatalogFile(tempDir, VALID_HEADER +
            "3020,Plate 2 x 4,14,Plates,2,4,1/3,Plastic,true\n"
        );

        // Act: Load parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: All fields parsed correctly
        assertEquals(1, parts.size());
        CatalogPart part = parts.get(0);
        assertEquals("3020", part.partId());
        assertEquals("Plate 2 x 4", part.name());
        assertEquals(14, part.categoryId());
        assertEquals("Plates", part.categoryName());
        assertEquals(2, part.studX());
        assertEquals(4, part.studY());
        assertEquals("1/3", part.heightUnitsRaw());
        assertEquals("Plastic", part.material());
        assertTrue(part.active());
    }

    @Test
    void testLoadActiveParts_HandlesVariousHeightFormats() throws IOException {
        // Arrange: Create catalog with different height unit formats
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3020,Plate 2x4,14,Plates,2,4,1/3,Plastic,true\n" +
            "15068,Slope Curved,37,Bricks Curved,2,2,2/3,Plastic,true\n"
        );

        // Act: Load parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: Height units preserved as strings
        assertEquals(3, parts.size());
        assertEquals("1", parts.get(0).heightUnitsRaw());
        assertEquals("1/3", parts.get(1).heightUnitsRaw());
        assertEquals("2/3", parts.get(2).heightUnitsRaw());
    }

    @Test
    void testLoadActiveParts_MissingFile_ThrowsClearError() {
        // Arrange: Empty temp directory (no catalog file)

        // Act & Assert: Expect exception with clear message
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> CuratedCatalogLoader.resolveCatalogPath(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("Curated catalog file not found"));
        assertTrue(message.contains("curated_40_catalog_v1.csv"));
        assertTrue(message.contains("data/catalog/"));
    }

    @Test
    void testLoadActiveParts_MissingHeader_ThrowsClearError() throws IOException {
        // Arrange: Create catalog missing required header
        createCatalogFile(tempDir,
            "part_id,name,category,category_name,stud_x,stud_y,material,active\n" +
            "3001,Brick 2x4,11,Bricks,2,4,Plastic,true\n"
        );

        // Act & Assert: Expect exception mentioning missing header
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("missing required headers"));
        assertTrue(message.contains("height_units"));
    }

    @Test
    void testLoadActiveParts_InvalidNumericField_ThrowsWithRowInfo() throws IOException {
        // Arrange: Create catalog with invalid stud_x value
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3002,Bad Part,11,Bricks,not_a_number,3,1,Plastic,true\n"
        );

        // Act & Assert: Expect exception with row number and field info
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("row 3"));  // Header is row 1, first data row is 2
        assertTrue(message.contains("3002") || message.contains("part_id"));
    }

    @Test
    void testLoadActiveParts_InvalidStudX_ThrowsWithValidation() throws IOException {
        // Arrange: Create catalog with stud_x = 0 (invalid)
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Bad Part,11,Bricks,0,4,1,Plastic,true\n"
        );

        // Act & Assert: Expect validation error
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("row 2"));
        assertTrue(message.contains("studX must be > 0") || message.contains("3001"));
    }

    @Test
    void testLoadActiveParts_InvalidStudY_ThrowsWithValidation() throws IOException {
        // Arrange: Create catalog with stud_y = -1 (invalid)
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Bad Part,11,Bricks,2,-1,1,Plastic,true\n"
        );

        // Act & Assert: Expect validation error
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("row 2"));
        assertTrue(message.contains("studY must be > 0") || message.contains("3001"));
    }

    @Test
    void testResolveCatalogPath_DirectPath() throws IOException {
        // Arrange: Create catalog in direct path style
        Path catalogDir = tempDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(catalogFile, VALID_HEADER, StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CuratedCatalogLoader.resolveCatalogPath(tempDir);

        // Assert: Resolved path points to direct-style location
        assertEquals(catalogFile, resolved);
        assertTrue(Files.exists(resolved));
    }

    @Test
    void testResolveCatalogPath_NestedPath() throws IOException {
        // Arrange: Create catalog in nested path style (no direct path)
        Path nestedDir = tempDir.resolve("legomodel/data/catalog");
        Files.createDirectories(nestedDir);
        Path catalogFile = nestedDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(catalogFile, VALID_HEADER, StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CuratedCatalogLoader.resolveCatalogPath(tempDir);

        // Assert: Resolved path points to nested-style location
        assertEquals(catalogFile, resolved);
        assertTrue(Files.exists(resolved));
    }

    @Test
    void testResolveCatalogPath_PrefersDirectPath() throws IOException {
        // Arrange: Create catalog in BOTH direct and nested paths
        Path directDir = tempDir.resolve("data/catalog");
        Files.createDirectories(directDir);
        Path directFile = directDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(directFile, VALID_HEADER + "direct\n", StandardCharsets.UTF_8);

        Path nestedDir = tempDir.resolve("legomodel/data/catalog");
        Files.createDirectories(nestedDir);
        Path nestedFile = nestedDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(nestedFile, VALID_HEADER + "nested\n", StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CuratedCatalogLoader.resolveCatalogPath(tempDir);

        // Assert: Direct path is preferred
        assertEquals(directFile, resolved);
    }

    @Test
    void testLoadActiveParts_EmptyFile_ReturnsEmptyList() throws IOException {
        // Arrange: Create catalog with header only
        createCatalogFile(tempDir, VALID_HEADER);

        // Act: Load parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: Empty list returned
        assertTrue(parts.isEmpty());
    }

    @Test
    void testLoadActiveParts_NoActiveparts_ReturnsEmptyList() throws IOException {
        // Arrange: Create catalog with only inactive parts
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,false\n" +
            "3002,Brick 2x3,11,Bricks,2,3,1,Plastic,false\n"
        );

        // Act: Load active parts
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: Empty list returned (no active parts)
        assertTrue(parts.isEmpty());
    }

    @Test
    void testStrictActiveValidation_RejectsTypos() throws IOException {
        // Arrange: Create catalog with typo in active field (common misspelling)
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,tru\n"  // Typo: "tru" instead of "true"
        );

        // Act & Assert: Should fail fast with clear error about typo
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        assertTrue(ex.getMessage().contains("Invalid 'active' field"),
            "Expected error about invalid active field, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("tru"),
            "Expected error to mention the actual value 'tru'");
    }

    @Test
    void testStrictActiveValidation_RejectsYes() throws IOException {
        // Arrange: Create catalog with alternative boolean representation
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,yes\n"  // Invalid: "yes" instead of "true"
        );

        // Act & Assert: Should fail fast with clear error
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        assertTrue(ex.getMessage().contains("Invalid 'active' field"),
            "Expected error about invalid active field");
    }

    @Test
    void testStrictActiveValidation_RejectsNumeric() throws IOException {
        // Arrange: Create catalog with numeric boolean representation
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,1\n"  // Invalid: "1" instead of "true"
        );

        // Act & Assert: Should fail fast with clear error
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> CuratedCatalogLoader.loadActiveParts(tempDir)
        );

        assertTrue(ex.getMessage().contains("Invalid 'active' field"),
            "Expected error about invalid active field");
    }

    @Test
    void testStrictActiveValidation_AcceptsTrue() throws IOException {
        // Arrange: Create catalog with valid "true" (case-insensitive)
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,true\n" +
            "3002,Brick 2x3,11,Bricks,2,3,1,Plastic,TRUE\n" +  // Uppercase variant
            "3003,Brick 2x2,11,Bricks,2,2,1,Plastic,True\n"   // Mixed case variant
        );

        // Act: Load active parts (should succeed)
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: All three parts loaded successfully
        assertEquals(3, parts.size());
        assertTrue(parts.stream().allMatch(CatalogPart::active));
    }

    @Test
    void testStrictActiveValidation_AcceptsFalse() throws IOException {
        // Arrange: Create catalog with valid "false" (case-insensitive)
        createCatalogFile(tempDir, VALID_HEADER +
            "3001,Brick 2x4,11,Bricks,2,4,1,Plastic,false\n" +
            "3002,Brick 2x3,11,Bricks,2,3,1,Plastic,FALSE\n"  // Uppercase variant
        );

        // Act: Load active parts (should succeed, returns empty since all are false)
        List<CatalogPart> parts = CuratedCatalogLoader.loadActiveParts(tempDir);

        // Assert: No parts returned (all inactive)
        assertTrue(parts.isEmpty());
    }

    private void createCatalogFile(Path baseDir, String content) throws IOException {
        Path catalogDir = baseDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("curated_40_catalog_v1.csv");
        Files.writeString(catalogFile, content, StandardCharsets.UTF_8);
    }
}
