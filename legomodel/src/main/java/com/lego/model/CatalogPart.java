package com.lego.model;

/**
 * Represents a curated LEGO part from the catalog.
 * Immutable record with validation.
 */
public record CatalogPart(
    String partId,
    String name,
    int categoryId,
    String categoryName,
    int studX,
    int studY,
    String heightUnitsRaw,
    String material,
    boolean active
) {
    /**
     * Constructs a validated CatalogPart.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public CatalogPart {
        if (partId == null || partId.isBlank()) {
            throw new IllegalArgumentException("partId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("categoryName must not be blank");
        }
        if (studX <= 0) {
            throw new IllegalArgumentException("studX must be > 0, got: " + studX);
        }
        if (studY <= 0) {
            throw new IllegalArgumentException("studY must be > 0, got: " + studY);
        }
        if (heightUnitsRaw == null || heightUnitsRaw.isBlank()) {
            throw new IllegalArgumentException("heightUnitsRaw must not be blank");
        }
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("material must not be blank");
        }
    }

    /**
     * Creates a CatalogPart from CSV row data with field-level validation.
     *
     * @param partId part identifier
     * @param name part name
     * @param categoryId category numeric ID
     * @param categoryName category human-readable name
     * @param studX stud count in X dimension
     * @param studY stud count in Y dimension
     * @param heightUnitsRaw height as string (e.g., "1", "1/3", "2/3")
     * @param material material type
     * @param active whether part is active in catalog
     * @return validated CatalogPart instance
     * @throws IllegalArgumentException if any validation fails
     */
    public static CatalogPart of(
        String partId,
        String name,
        int categoryId,
        String categoryName,
        int studX,
        int studY,
        String heightUnitsRaw,
        String material,
        boolean active
    ) {
        return new CatalogPart(
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
