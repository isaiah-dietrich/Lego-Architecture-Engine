package com.lego.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for loading the canonical LEGO brick catalog.
 * Supports resolution from both repo root and legomodel/ subdirectory styles.
 * Fails fast with clear errors if the catalog file is missing.
 */
public final class CatalogLoader {

    /**
     * Resolves the canonical catalog path from the current working directory.
     * Tries direct path first, then nested path.
     *
     * @return resolved Path to the catalog file
     * @throws IllegalStateException if catalog cannot be resolved in either location
     */
    public static Path resolveCatalogPath() {
        return resolveCatalogPath(Paths.get("").toAbsolutePath().normalize());
    }

    /**
     * Resolves the canonical catalog path from the given base directory.
     * Tries direct path first, then nested path.
     *
     * @param baseDir base directory to resolve catalog from
     * @return resolved Path to the catalog file
     * @throws IllegalStateException if catalog cannot be resolved in either location
     */
    public static Path resolveCatalogPath(Path baseDir) {
        // Try direct path first: data/catalog/top_1000_catalog_v1.csv
        Path directPath = baseDir.resolve(CatalogConfig.CATALOG_RELATIVE_PATH);
        if (Files.exists(directPath)) {
            return directPath;
        }

        // Try nested path: legomodel/data/catalog/top_1000_catalog_v1.csv
        Path nestedPath = baseDir.resolve(CatalogConfig.CATALOG_NESTED_PATH);
        if (Files.exists(nestedPath)) {
            return nestedPath;
        }

        throw new IllegalStateException(
            "Catalog file not found. Expected one of:\n" +
            "  - " + directPath.toAbsolutePath() + "\n" +
            "  - " + nestedPath.toAbsolutePath() + "\n\n" +
            "Please ensure top_1000_catalog_v1.csv is in data/catalog/ directory."
        );
    }

    /**
     * Loads and validates the canonical catalog file.
     * Fails fast with clear error if file is missing.
     *
     * @return resolved Path to the catalog file (guaranteed to exist)
     * @throws IllegalStateException if catalog cannot be found or accessed
     */
    public static Path loadCatalog() {
        return loadCatalog(Paths.get("").toAbsolutePath().normalize());
    }

    /**
     * Loads and validates the canonical catalog file from the given base directory.
     * Fails fast with clear error if file is missing.
     *
     * @param baseDir base directory to resolve catalog from
     * @return resolved Path to the catalog file (guaranteed to exist)
     * @throws IllegalStateException if catalog cannot be found or accessed
     */
    public static Path loadCatalog(Path baseDir) {
        Path catalogPath = resolveCatalogPath(baseDir);
        
        if (!Files.isReadable(catalogPath)) {
            throw new IllegalStateException(
                "Catalog file exists but is not readable: " + catalogPath.toAbsolutePath()
            );
        }

        System.out.println("Catalog loaded from: " + catalogPath.toAbsolutePath());
        return catalogPath;
    }

    private CatalogLoader() {
        // Utility class, no instantiation
    }
}
