package com.lego.optimize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lego.data.CatalogPartRepository;
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
 * - Excludes forbidden orientations (1x2 vertical not allowed)
 */
public final class AllowedBrickDimensions {

    /**
     * Slope parts currently supported by placement/export orientation logic.
     *
     * Other sloped/curved parts in the catalog can have different geometric
     * envelopes and local orientation conventions, which can produce visual
     * intersections in downstream LDraw viewers when treated as generic wedges.
     */
    private static final Set<String> SUPPORTED_SLOPE_PART_IDS = Set.of(
        "3037",  // Slope 45 2x4
        "3039",  // Slope 45 2x2
        "3040b", // Slope 45 2x1
        "3298",  // Slope 33 3x2
        "4286"   // Slope 33 3x1
    );

    /**
     * Represents a brick specification with dimensions, height, category, and part ID.
     */
    public static final class BrickSpec {
        private final int studX;
        private final int studY;
        private final int heightUnits;
        private final String category;
        private final String partId;
        private final String name;
        private final Double slopeAngle;

        /** Validates and constructs a brick specification with slope metadata. */
        public BrickSpec(int studX, int studY, int heightUnits, String category, String partId, String name, Double slopeAngle) {
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
            this.name = name != null ? name : partId;
            this.slopeAngle = slopeAngle;
        }

        /** Backward-compatible constructor without slope angle. */
        public BrickSpec(int studX, int studY, int heightUnits, String category, String partId, String name) {
            this(studX, studY, heightUnits, category, partId, name, null);
        }

        /** Convenience constructor without name or slope (defaults to partId). */
        public BrickSpec(int studX, int studY, int heightUnits, String category, String partId) {
            this(studX, studY, heightUnits, category, partId, partId, null);
        }

        /** Returns the stud count in the X direction. */
        public int studX() {
            return studX;
        }

        /** Returns the stud count in the Y direction. */
        public int studY() {
            return studY;
        }

        /** Returns the height in plate units. */
        public int heightUnits() {
            return heightUnits;
        }

        /** Returns the part category (e.g. "brick", "plate"). */
        public String category() {
            return category;
        }

        /** Returns the catalog part identifier. */
        public String partId() {
            return partId;
        }

        /** Returns the human-readable part name. */
        public String name() {
            return name;
        }

        /** Returns the slope angle in degrees, or null for standard rectangular parts. */
        public Double slopeAngle() {
            return slopeAngle;
        }

        /** Returns true if this spec represents a slope/angled part. */
        public boolean isSlope() {
            return slopeAngle != null;
        }

        /** Returns the stud area (studX × studY). */
        public int area() {
            return studX * studY;
        }

        @Override
        /** Two BrickSpecs are equal if they have the same stud dimensions and height. */
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
        /** Hash code based on stud dimensions and height. */
        public int hashCode() {
            int result = 31 * studX + studY;
            result = 31 * result + heightUnits;
            result = 31 * result + category.hashCode();
            result = 31 * result + partId.hashCode();
            return result;
        }

        @Override
        /** Returns a string representation of this brick specification. */
        public String toString() {
            return studX + "x" + studY + "x" + heightUnits + " (" + partId + ")";
        }
    }

    /** Non-instantiable utility class. */
    private AllowedBrickDimensions() {
        // Utility class
    }

    /**
     * Loads allowed brick specs from the given repository.
     *
     * @param repository catalog part data source
     * @return list of allowed brick specs in placement priority order
     * @throws IllegalStateException if repository contains no valid specs
     */
    public static List<BrickSpec> loadFromRepository(CatalogPartRepository repository) {
        return extractSpecs(repository.findActiveParts());
    }

    /**
     * Pure transformer: derives brick specs from pre-loaded catalog parts.
     * No file I/O — callers are responsible for loading parts.
     *
     * @param activeParts active catalog parts
     * @return list of allowed brick specs in placement priority order
     * @throws IllegalStateException if no valid specs can be derived
     */
    public static List<BrickSpec> fromParts(List<CatalogPart> activeParts) {
        return extractSpecs(activeParts);
    }

    /**
     * Loads allowed brick specs from the curated catalog.
     * Returns specs sorted by placement priority (largest area first).
     * 
     * @return list of allowed brick specs in placement priority order
     * @throws IllegalStateException if catalog cannot be loaded or contains no valid specs
     * @deprecated Use #fromParts(List) or #loadFromRepository(CatalogPartRepository).
     */
    @Deprecated
    public static List<BrickSpec> loadFromCatalog() {
        //Smallest brick located at the end of the array
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
     * @deprecated Use #fromParts(List) or #loadFromRepository(CatalogPartRepository).
     */
    @Deprecated
    public static List<BrickSpec> loadFromCatalog(Path baseDir) {
        List<CatalogPart> activeParts = CuratedCatalogLoader.loadActiveParts(baseDir);
        return extractSpecs(activeParts);
    }

    /**
     * Extracts and filters brick specs from catalog parts.
     * 
     * Filtering rules:
     * - Handles orientations: adds 2x1 horizontal but excludes 1x2 vertical
     * - Deduplicates by (studX, studY, heightUnits), keeping the first part encountered
     * 
     * @param parts catalog parts to extract specs from
     * @return sorted list of unique brick specs
     */
    private static List<BrickSpec> extractSpecs(List<CatalogPart> parts) {
        // Deduplicate by footprint key (studX, studY), keeping first part per footprint
        Map<String, BrickSpec> uniqueSpecs = new HashMap<>();

        for (CatalogPart part : parts) {
            String category = part.categoryName().trim();
            int studX = part.studX();
            int studY = part.studY();
            int heightUnits = parseHeightUnits(part.heightUnitsRaw().trim());
            Double slopeAngle = part.slopeAngle();

            // Restrict slope specs to the supported directional subset.
            if (slopeAngle != null && !SUPPORTED_SLOPE_PART_IDS.contains(part.partId())) {
                continue;
            }

            // Special handling for 1x2 brick (part 3004):
            // Only add 2x1 horizontal orientation, NOT 1x2 vertical
            if (studX == 1 && studY == 2 && slopeAngle == null) {
                String key = "2x1x" + heightUnits;
                uniqueSpecs.putIfAbsent(key,
                    new BrickSpec(2, 1, heightUnits, category, part.partId(), part.name(), null));
                continue;
            }

            // For all other parts, add the spec as-is
            String slopeTag = slopeAngle != null ? "/s" + slopeAngle : "";
            String key = studX + "x" + studY + "x" + heightUnits + slopeTag;
            uniqueSpecs.putIfAbsent(key,
                new BrickSpec(studX, studY, heightUnits, category, part.partId(), part.name(), slopeAngle));
        }

        if (uniqueSpecs.isEmpty()) {
            throw new IllegalStateException(
                "No valid brick specs found in catalog. " +
                "Expected active parts with positive dimensions."
            );
        }

        // Sort by priority: area desc, width desc, depth desc
        List<BrickSpec> sorted = new ArrayList<>(uniqueSpecs.values());
        sorted.sort(Comparator
            .comparingInt((BrickSpec s) -> -s.area())
            .thenComparingInt((BrickSpec s) -> -s.heightUnits())
            .thenComparingInt((BrickSpec s) -> -s.studX())
            .thenComparingInt((BrickSpec s) -> -s.studY())
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
