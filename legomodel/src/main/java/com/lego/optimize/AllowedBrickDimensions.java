package com.lego.optimize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lego.data.CuratedCatalogLoader;
import com.lego.model.CatalogPart;

/**
 * Provides allowed brick specifications for deterministic brick placement.
 * 
 * Specs are derived from the curated catalog and sorted by:
 * 1. Area (descending) - prefer larger bricks
 * 2. Width (descending) - prefer wider bricks when area is equal
 * 3. Depth (descending) - prefer deeper bricks when width is equal
 * 
 * Filtering rules:
 * - Only active parts (active=true)
 * - Only allowed categories (currently "Bricks")
 * - Excludes forbidden orientations (1x2 vertical not allowed)
 */
public final class AllowedBrickDimensions {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("bricks");

    /**
     * Represents a brick specification with dimensions, height, category, and part ID.
     */
    public static final class BrickSpec {
        private final int studX;
        private final int studY;
        private final int heightUnits;
        private final String category;
        private final String partId;

        public BrickSpec(int studX, int studY, int heightUnits, String category, String partId) {
            if (studX <= 0 || studY <= 0) {
                throw new IllegalArgumentException("Dimensions must be positive: " + studX + "x" + studY);
            }
            if (heightUnits <= 0) {
                throw new IllegalArgumentException("heightUnits must be positive: " + heightUnits);
            }
            if (partId == null || partId.isBlank()) {
                throw new IllegalArgumentException("partId must not be blank");
            }
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category must not be blank");
            }
            this.studX = studX;
            this.studY = studY;
            this.heightUnits = heightUnits;
            this.category = category;
            this.partId = partId;
        }

        public int studX() {
            return studX;
        }

        public int studY() {
            return studY;
        }

        public int heightUnits() {
            return heightUnits;
        }

        public String category() {
            return category;
        }

        public String partId() {
            return partId;
        }

        public int area() {
            return studX * studY;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BrickSpec)) return false;
            BrickSpec other = (BrickSpec) obj;
            return studX == other.studX && studY == other.studY
                && heightUnits == other.heightUnits
                && category.equals(other.category)
                && partId.equals(other.partId);
        }

        @Override
        public int hashCode() {
            int result = 31 * studX + studY;
            result = 31 * result + heightUnits;
            result = 31 * result + category.hashCode();
            result = 31 * result + partId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return studX + "x" + studY + "x" + heightUnits + " (" + partId + ")";
        }
    }

    private AllowedBrickDimensions() {
        // Utility class
    }

    /**
     * Loads allowed brick specs from the curated catalog.
     * Returns specs sorted by placement priority (largest area first).
     * 
     * @return list of allowed brick specs in placement priority order
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid specs
     */
    public static List<BrickSpec> loadFromCatalog() {
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts();
        return extractSpecs(activeParts);
    }

    /**
     * Loads allowed brick specs from the curated catalog at the given base directory.
     * Test-friendly overload for dependency injection.
     * 
     * @param baseDir base directory to resolve catalog from
     * @return list of allowed brick specs in placement priority order
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid specs
     */
    public static List<BrickSpec> loadFromCatalog(Path baseDir) {
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts(baseDir);
        return extractSpecs(activeParts);
    }

    /**
     * Extracts and filters brick specs from catalog parts.
     * 
     * Filtering rules:
     * - Only allowed categories (case-insensitive, trimmed)
     * - Handles orientations: adds 2x1 horizontal but excludes 1x2 vertical
     * - Deduplicates by (studX, studY) footprint, keeping the first part encountered
     * 
     * @param parts catalog parts to extract specs from
     * @return sorted list of unique brick specs
     */
    private static List<BrickSpec> extractSpecs(List<CatalogPart> parts) {
        // Deduplicate by footprint key (studX, studY), keeping first part per footprint
        Map<String, BrickSpec> uniqueSpecs = new HashMap<>();

        for (CatalogPart part : parts) {
            // Filter: only allowed categories (case-insensitive, trimmed)
            String category = part.categoryName().trim();
            if (!ALLOWED_CATEGORIES.contains(category.toLowerCase())) {
                continue;
            }

            int studX = part.studX();
            int studY = part.studY();
            int heightUnits = parseHeightUnits(part.heightUnitsRaw().trim());

            // Special handling for 1x2 brick (part 3004):
            // Only add 2x1 horizontal orientation, NOT 1x2 vertical
            if (studX == 1 && studY == 2) {
                String key = "2x1";
                uniqueSpecs.putIfAbsent(key,
                    new BrickSpec(2, 1, heightUnits, category, part.partId()));
                continue;
            }

            // For all other bricks, add the spec as-is
            String key = studX + "x" + studY;
            uniqueSpecs.putIfAbsent(key,
                new BrickSpec(studX, studY, heightUnits, category, part.partId()));
        }

        if (uniqueSpecs.isEmpty()) {
            throw new IllegalStateException(
                "No valid brick specs found in catalog. " +
                "Expected active parts with allowed categories: " + ALLOWED_CATEGORIES + "."
            );
        }

        // Sort by priority: area desc, width desc, depth desc
        List<BrickSpec> sorted = new ArrayList<>(uniqueSpecs.values());
        sorted.sort(Comparator
            .comparingInt(BrickSpec::area).reversed()
            .thenComparingInt(BrickSpec::studX).reversed()
            .thenComparingInt(BrickSpec::studY).reversed()
        );

        return sorted;
    }

    /**
     * Parses a height string into integer height units.
     * Standard bricks ("1" or "1.0") = 3 units.
     * Plates ("1/3") = 1 unit.
     *
     * @param height the height string from catalog
     * @return height in LDraw-relative units (bricks=3, plates=1)
     */
    static int parseHeightUnits(String height) {
        if ("1".equals(height) || "1.0".equals(height)) {
            return 3;
        }
        if ("1/3".equals(height)) {
            return 1;
        }
        if ("2/3".equals(height)) {
            return 2;
        }
        // Default to full brick height for unrecognized values
        return 3;
    }
}