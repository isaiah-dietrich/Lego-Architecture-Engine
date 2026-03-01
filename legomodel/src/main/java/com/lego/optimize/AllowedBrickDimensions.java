package com.lego.optimize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lego.data.CuratedCatalogLoader;
import com.lego.model.CatalogPart;

/**
 * Provides allowed brick dimensions for deterministic brick placement.
 * 
 * Dimensions are derived from the curated catalog and sorted by:
 * 1. Area (descending) - prefer larger bricks
 * 2. Width (descending) - prefer wider bricks when area is equal
 * 3. Depth (descending) - prefer deeper bricks when width is equal
 * 
 * Filtering rules for Step 2:
 * - Only active parts (active=true)
 * - Only full-height bricks (heightUnitsRaw == "1")
 * - Only standard "Bricks" category (excludes slopes, special parts, plates)
 * - Excludes forbidden orientations (1x2 vertical not allowed)
 */
public final class AllowedBrickDimensions {

    /**
     * Represents a brick dimension with studX and studY.
     */
    public static final class Dimension {
        private final int studX;
        private final int studY;

        public Dimension(int studX, int studY) {
            if (studX <= 0 || studY <= 0) {
                throw new IllegalArgumentException("Dimensions must be positive: " + studX + "x" + studY);
            }
            this.studX = studX;
            this.studY = studY;
        }

        public int studX() {
            return studX;
        }

        public int studY() {
            return studY;
        }

        public int area() {
            return studX * studY;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Dimension)) return false;
            Dimension other = (Dimension) obj;
            return studX == other.studX && studY == other.studY;
        }

        @Override
        public int hashCode() {
            return 31 * studX + studY;
        }

        @Override
        public String toString() {
            return studX + "x" + studY;
        }
    }

    private AllowedBrickDimensions() {
        // Utility class
    }

    /**
     * Loads allowed brick dimensions from the curated catalog.
     * Returns dimensions sorted by placement priority (largest area first).
     * 
     * @return list of allowed dimensions in placement priority order
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid dimensions
     */
    public static List<Dimension> loadFromCatalog() {
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts();
        return extractDimensions(activeParts);
    }

    /**
     * Loads allowed brick dimensions from the curated catalog at the given base directory.
     * Test-friendly overload for dependency injection.
     * 
     * @param baseDir base directory to resolve catalog from
     * @return list of allowed dimensions in placement priority order
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid dimensions
     */
    public static List<Dimension> loadFromCatalog(Path baseDir) {
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts(baseDir);
        return extractDimensions(activeParts);
    }

    /**
     * Extracts and filters dimensions from catalog parts.
     * 
     * Filtering rules:
     * - Only heightUnitsRaw == "1" (full-height bricks)
     * - Only category "Bricks" (excludes slopes, special parts, plates)
     * - Handles orientations: adds 2x1 horizontal but excludes 1x2 vertical
     * 
     * @param parts catalog parts to extract dimensions from
     * @return sorted list of unique dimensions
     */
    private static List<Dimension> extractDimensions(List<CatalogPart> parts) {
        Set<Dimension> uniqueDimensions = new HashSet<>();

        for (CatalogPart part : parts) {
            // Filter: only full-height bricks
            if (!"1".equals(part.heightUnitsRaw())) {
                continue;
            }

            // Filter: only standard "Bricks" category (excludes slopes, special, plates)
            if (!"Bricks".equals(part.categoryName())) {
                continue;
            }

            int studX = part.studX();
            int studY = part.studY();

            // Special handling for 1x2 brick (part 3004):
            // Only add 2x1 horizontal orientation, NOT 1x2 vertical
            if (studX == 1 && studY == 2) {
                uniqueDimensions.add(new Dimension(2, 1));  // Add horizontal only
                continue;
            }

            // For all other bricks, add the dimension as-is
            uniqueDimensions.add(new Dimension(studX, studY));
        }

        if (uniqueDimensions.isEmpty()) {
            throw new IllegalStateException(
                "No valid brick dimensions found in catalog. " +
                "Expected active parts with heightUnitsRaw='1' and category='Bricks'."
            );
        }

        // Sort by priority: area desc, width desc, depth desc
        List<Dimension> sorted = new ArrayList<>(uniqueDimensions);
        sorted.sort(Comparator
            .comparingInt(Dimension::area).reversed()
            .thenComparingInt(Dimension::studX).reversed()
            .thenComparingInt(Dimension::studY).reversed()
        );

        return sorted;
    }
}