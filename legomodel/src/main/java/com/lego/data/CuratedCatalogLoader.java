package com.lego.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.lego.model.CatalogPart;

/**
 * Loads curated LEGO parts from the catalog CSV file.
 * Supports filtering by active status and provides deterministic ordering.
 */
public final class CuratedCatalogLoader {

    private CuratedCatalogLoader() {
        // Utility class, no instantiation
    }

    /**
     * Loads all active parts (active=true) from the curated catalog.
     * Returns parts in the order they appear in the CSV file.
     *
     * @return list of active CatalogPart instances
     * @throws IllegalStateException if catalog file cannot be found or read
     * @throws IllegalArgumentException if CSV structure or data is invalid
     */
    public static List<CatalogPart> loadActiveParts() {
        return loadParts(Paths.get("").toAbsolutePath().normalize(), true);
    }

    /**
     * Loads all active parts (active=true) from the curated catalog.
     * Returns parts in the order they appear in the CSV file.
     *
     * @param baseDir base directory to resolve catalog from
     * @return list of active CatalogPart instances
     * @throws IllegalStateException if catalog file cannot be found or read
     * @throws IllegalArgumentException if CSV structure or data is invalid
     */
    public static List<CatalogPart> loadActiveParts(Path baseDir) {
        return loadParts(baseDir, true);
    }

    /**
     * Loads all parts from the curated catalog regardless of active status.
     * Returns parts in the order they appear in the CSV file.
     *
     * @return list of all CatalogPart instances
     * @throws IllegalStateException if catalog file cannot be found or read
     * @throws IllegalArgumentException if CSV structure or data is invalid
     */
    public static List<CatalogPart> loadAllParts() {
        return loadParts(Paths.get("").toAbsolutePath().normalize(), false);
    }

    /**
     * Loads all parts from the curated catalog regardless of active status.
     * Returns parts in the order they appear in the CSV file.
     *
     * @param baseDir base directory to resolve catalog from
     * @return list of all CatalogPart instances
     * @throws IllegalStateException if catalog file cannot be found or read
     * @throws IllegalArgumentException if CSV structure or data is invalid
     */
    public static List<CatalogPart> loadAllParts(Path baseDir) {
        return loadParts(baseDir, false);
    }

    /**
     * Resolves the curated catalog path from the current working directory.
     *
     * @return resolved Path to the catalog file
     * @throws IllegalStateException if catalog cannot be found
     */
    static Path resolveCatalogPath() {
        return resolveCatalogPath(Paths.get("").toAbsolutePath().normalize());
    }

    /**
     * Resolves the curated catalog path from the given base directory.
     *
     * @param baseDir base directory to resolve catalog from
     * @return resolved Path to the catalog file
     * @throws IllegalStateException if catalog cannot be found
     */
    static Path resolveCatalogPath(Path baseDir) {
        // Try direct path first
        Path directPath = baseDir.resolve(CatalogConfig.CURATED_CATALOG_RELATIVE_PATH);
        if (Files.exists(directPath)) {
            return directPath;
        }

        // Try nested path
        Path nestedPath = baseDir.resolve(CatalogConfig.CURATED_CATALOG_NESTED_PATH);
        if (Files.exists(nestedPath)) {
            return nestedPath;
        }

        throw new IllegalStateException(
            "Curated catalog file not found. Expected one of:\n" +
            "  - " + directPath.toAbsolutePath() + "\n" +
            "  - " + nestedPath.toAbsolutePath() + "\n\n" +
            "Please ensure " + CatalogConfig.CURATED_CATALOG_FILE + " is in data/catalog/ directory."
        );
    }

    private static List<CatalogPart> loadParts(Path baseDir, boolean activeOnly) {
        Path catalogPath = resolveCatalogPath(baseDir);

        if (!Files.isReadable(catalogPath)) {
            throw new IllegalStateException(
                "Catalog file exists but is not readable: " + catalogPath.toAbsolutePath()
            );
        }

        try (Reader reader = Files.newBufferedReader(catalogPath, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                 .setHeader()
                 .setSkipHeaderRecord(true)
                 .setTrim(true)
                 .build())) {

            validateHeaders(new HashSet<>(parser.getHeaderNames()), catalogPath);

            List<CatalogPart> parts = new ArrayList<>();
            for (CSVRecord record : parser) {
                try {
                    CatalogPart part = parseRecord(record);
                    if (!activeOnly || part.active()) {
                        parts.add(part);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Invalid data in catalog at row " + (record.getRecordNumber() + 1) +
                        " (part_id: " + record.get("part_id") + "): " + e.getMessage(),
                        e
                    );
                }
            }

            return parts;

        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to read catalog file: " + catalogPath.toAbsolutePath(),
                e
            );
        }
    }

    private static void validateHeaders(Set<String> actualHeaders, Path catalogPath) {
        Set<String> missing = new HashSet<>();
        for (String required : CatalogConfig.CURATED_CATALOG_HEADERS) {
            if (!actualHeaders.contains(required)) {
                missing.add(required);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Catalog file missing required headers: " + missing + "\n" +
                "File: " + catalogPath.toAbsolutePath() + "\n" +
                "Found headers: " + actualHeaders + "\n" +
                "Required headers: " + Set.of(CatalogConfig.CURATED_CATALOG_HEADERS)
            );
        }
    }

    /**
     * Parses the 'active' field with strict validation.
     * Rejects ambiguous values like "tru", "yes", "1" - only "true" and "false" are accepted.
     * Fails fast with clear error on invalid input.
     *
     * @param activeValue the value from the 'active' column
     * @param rowNumber the CSV record number (1-based) for error messages
     * @return true if "true", false if "false"
     * @throws IllegalArgumentException if value is not exactly "true" or "false"
     */
    private static boolean parseActiveField(String activeValue, long rowNumber) {
        if ("true".equalsIgnoreCase(activeValue)) {
            return true;
        } else if ("false".equalsIgnoreCase(activeValue)) {
            return false;
        } else {
            throw new IllegalArgumentException(
                "Invalid 'active' field in row " + (rowNumber + 1) + ": " +
                "expected 'true' or 'false', got '" + activeValue + "'. " +
                "Typos like 'tru', 'yes', '1' are not accepted (fail fast)."
            );
        }
    }

    private static CatalogPart parseRecord(CSVRecord record) {
        String partId = record.get("part_id");
        String name = record.get("name");
        int categoryId = Integer.parseInt(record.get("category"));
        String categoryName = record.get("category_name");
        int studX = Integer.parseInt(record.get("stud_x"));
        int studY = Integer.parseInt(record.get("stud_y"));
        String heightUnitsRaw = record.get("height_units");
        String material = record.get("material");
        boolean active = parseActiveField(record.get("active"), record.getRecordNumber());

        return CatalogPart.of(
            partId,
            name,
            categoryId,
            categoryName,
            studX,
            studY,
            heightUnitsRaw,
            material,
            active
        );
    }
}
