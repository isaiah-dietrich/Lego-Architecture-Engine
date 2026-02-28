package com.lego.data;

/**
 * Configuration constants for the LEGO brick catalog.
 * Defines the canonical source file path for all downstream logic.
 */
public final class CatalogConfig {

    /**
     * Canonical relative path to the catalog file.
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
     * Expected CSV header names in the catalog file (no exact order enforced, but these columns must exist).
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

    private CatalogConfig() {
        // Utility class, no instantiation
    }
}
