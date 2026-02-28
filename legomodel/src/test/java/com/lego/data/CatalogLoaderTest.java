package com.lego.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for CatalogLoader.
 */
class CatalogLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveCatalogPath_DirectPath() throws IOException {
        // Arrange: Create catalog in direct path style
        Path catalogDir = tempDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("top_1000_catalog_v1.csv");
        Files.writeString(catalogFile, "part_id,name\n", StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CatalogLoader.resolveCatalogPath(tempDir);
        
        // Assert: Resolved path points to direct-style location
        assertEquals(catalogFile, resolved);
        assertTrue(Files.exists(resolved));
    }

    @Test
    void testResolveCatalogPath_NestedPath() throws IOException {
        // Arrange: Create catalog in nested path style (no direct path)
        Path nestedDir = tempDir.resolve("legomodel/data/catalog");
        Files.createDirectories(nestedDir);
        Path catalogFile = nestedDir.resolve("top_1000_catalog_v1.csv");
        Files.writeString(catalogFile, "part_id,name\n", StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CatalogLoader.resolveCatalogPath(tempDir);

        // Assert: Resolved path points to nested-style location
        assertEquals(catalogFile, resolved);
        assertTrue(Files.exists(resolved));
    }

    @Test
    void testResolveCatalogPath_PrefersDirectPath() throws IOException {
        // Arrange: Create catalog in BOTH direct and nested paths
        Path directDir = tempDir.resolve("data/catalog");
        Files.createDirectories(directDir);
        Path directFile = directDir.resolve("top_1000_catalog_v1.csv");
        Files.writeString(directFile, "direct\n", StandardCharsets.UTF_8);

        Path nestedDir = tempDir.resolve("legomodel/data/catalog");
        Files.createDirectories(nestedDir);
        Path nestedFile = nestedDir.resolve("top_1000_catalog_v1.csv");
        Files.writeString(nestedFile, "nested\n", StandardCharsets.UTF_8);

        // Act: Resolve catalog from temp directory
        Path resolved = CatalogLoader.resolveCatalogPath(tempDir);

        // Assert: Direct path is preferred
        assertEquals(directFile, resolved);
    }

    @Test
    void testResolveCatalogPath_MissingFile_ThrowsWithClearMessage() {
        // Arrange: Empty temp directory (no catalog file)

        // Act & Assert: Expect exception with both paths and helpful message
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> CatalogLoader.resolveCatalogPath(tempDir)
        );

        String message = exception.getMessage();
        assertTrue(message.contains("Catalog file not found"), "Error should mention missing catalog");
        assertTrue(message.contains("data/catalog/top_1000_catalog_v1.csv"), 
            "Error should show direct path");
        assertTrue(message.contains("legomodel/data/catalog/top_1000_catalog_v1.csv"), 
            "Error should show nested path");
        assertTrue(message.contains("Please ensure"),
            "Error should provide actionable guidance");
    }

    @Test
    void testLoadCatalog_Success() throws IOException {
        // Arrange: Create readable catalog file
        Path catalogDir = tempDir.resolve("data/catalog");
        Files.createDirectories(catalogDir);
        Path catalogFile = catalogDir.resolve("top_1000_catalog_v1.csv");
        Files.writeString(catalogFile, "part_id,name\n", StandardCharsets.UTF_8);

        // Act: Load catalog
        Path loaded = CatalogLoader.loadCatalog(tempDir);

        // Assert: Returns readable catalog path
        assertEquals(catalogFile, loaded);
        assertTrue(Files.isReadable(loaded));
    }

    @Test
    void testLoadCatalog_MissingFile_ThrowsWithClearMessage() {
        // Arrange: Empty temp directory

        // Act & Assert: Expect exception mentioning missing catalog
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> CatalogLoader.loadCatalog(tempDir)
        );

        assertTrue(exception.getMessage().contains("Catalog file not found"));
    }

    @Test
    void testLoadCatalog_UnreadableFile_ThrowsWithClearMessage() throws IOException {
        // Note: Setting file permissions is OS-dependent and may not work reliably on all systems.
        // On macOS/Linux, we can use PosixFilePermissions. On Windows, this test is less reliable.
        // Skip this test if we cannot reliably make a file unreadable.
        
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            // Arrange: Create catalog file and remove read permissions
            Path catalogDir = tempDir.resolve("data/catalog");
            Files.createDirectories(catalogDir);
            Path catalogFile = catalogDir.resolve("top_1000_catalog_v1.csv");
            Files.writeString(catalogFile, "part_id,name\n", StandardCharsets.UTF_8);
            
            // Remove read permissions (POSIX systems only)
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(catalogFile, perms);

            // Act & Assert: Expect exception mentioning unreadable file
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CatalogLoader.loadCatalog(tempDir)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("not readable"), 
                "Error should mention file is not readable");
            
            // Cleanup: Restore permissions so JUnit can delete temp directory
            perms.add(PosixFilePermission.OWNER_READ);
            Files.setPosixFilePermissions(catalogFile, perms);
        }
    }

    @Test
    void testCatalogConfig_HeadersMatch() {
        // Assert: Verify catalog headers are the expected columns
        String[] expected = {
            "part_id",
            "name", 
            "category",
            "stud_x",
            "stud_y",
            "height_units",
            "active"
        };
        
        assertEquals(expected.length, CatalogConfig.CATALOG_HEADERS.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], CatalogConfig.CATALOG_HEADERS[i]);
        }
    }

    @Test
    void testCatalogConfig_PathsNotEmpty() {
        // Assert: Paths are defined and non-empty
        assertTrue(!CatalogConfig.CATALOG_RELATIVE_PATH.isEmpty(), "Direct path must be defined");
        assertTrue(!CatalogConfig.CATALOG_NESTED_PATH.isEmpty(), "Nested path must be defined");
        assertTrue(CatalogConfig.CATALOG_RELATIVE_PATH.contains("top_1000_catalog_v1.csv"),
            "Path must reference the canonical filename");
        assertTrue(CatalogConfig.CATALOG_NESTED_PATH.contains("top_1000_catalog_v1.csv"),
            "Nested path must reference the canonical filename");
    }
}
