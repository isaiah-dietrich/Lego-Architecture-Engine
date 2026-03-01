package com.lego.data;

/**
 * Configuration constants for LEGO brick catalogs.
 * Provides paths and headers for both canonical and curated catalog sources.
 */
public final class CatalogConfig {

    // ========== TOP-1000 CATALOG ==========
    // Legacy constants maintained for backward compatibility with CatalogLoader

    /**
     * Canonical relative path to the top-1000 catalog file.
     * Supports both directory styles:
     * - From repo root: data/catalog/top_1000_catalog_v1.csv
     * - From legomodel/: data/catalog/top_1000_catalog_v1.csv
     */
    public static final String CATALOG_RELATIVE_PATH = "data/catalog/top_1000_catalog_v1.csv";

    /**
     * Alternative nested path for legomodel/ subdirectory environment.
     */
    public static final String CATALOG_NESTED_PATH = "legomodel/data/catalog/top_1000_catalog_v1.csv";

    /**
     * Expected CSV header names in the top-1000 catalog file.
     * (no exact order enforced, but these columns must exist)
     */
    public static final String[] CATALOG_HEADERS = {
        "part_id",
        "name",
        "category",
        "stud_x",
        "stud_y",
        "height_units",
        "active"
    };

    // ========== CURATED CATALOG ==========
    // New explicit constants for CuratedCatalogLoader

    /**
     * Filename of the curated catalog file.
     */
    public static final String CURATED_CATALOG_FILE = "curated_40_catalog_v1.csv";

    /**
     * Relative path to the curated catalog file.
     */
    public static final String CURATED_CATALOG_RELATIVE_PATH = "data/catalog/" + CURATED_CATALOG_FILE;

    /**
     * Nested path to the curated catalog file (legomodel/ subdirectory style).
     */
    public static final String CURATED_CATALOG_NESTED_PATH = "legomodel/data/catalog/" + CURATED_CATALOG_FILE;

    /**
     * Expected CSV header names in the curated catalog file.
     * Includes additional fields not in top-1000: category_name, material.
     * These columns must exist and will be validated.
     */
    public static final String[] CURATED_CATALOG_HEADERS = {
        "part_id",
        "name",
        "category",
        "category_name",
        "stud_x",
        "stud_y",
        "height_units",
        "material",
        "active"
    };

    private CatalogConfig() {
        // Utility class, no instantiation
    }
}
