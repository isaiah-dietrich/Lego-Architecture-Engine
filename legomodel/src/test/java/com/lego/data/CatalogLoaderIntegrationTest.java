package com.lego.data;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for CatalogLoader using the real working directory.
 * These tests verify backward compatibility of no-arg methods.
 */
class CatalogLoaderIntegrationTest {

    @Test
    void testResolveCatalogPath_NoArg_UsesActualWorkingDirectory() {
        // Act: Resolve catalog from actual working directory
        Path resolved = CatalogLoader.resolveCatalogPath();

        // Assert: Path is resolved and points to real catalog
        assertTrue(resolved.toString().contains("top_1000_catalog_v1.csv"),
            "Resolved path should point to top_1000_catalog_v1.csv");
        assertTrue(Files.exists(resolved),
            "Resolved catalog path should exist in actual project");
    }

    @Test
    void testLoadCatalog_NoArg_UsesActualWorkingDirectory() {
        // Act: Load catalog from actual working directory
        Path loaded = CatalogLoader.loadCatalog();

        // Assert: Catalog is loaded and readable
        assertTrue(Files.exists(loaded), "Loaded catalog should exist");
        assertTrue(Files.isReadable(loaded), "Loaded catalog should be readable");
        assertTrue(loaded.toString().contains("top_1000_catalog_v1.csv"),
            "Loaded catalog should point to top_1000_catalog_v1.csv");
    }
}
