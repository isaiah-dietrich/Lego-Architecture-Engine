package com.lego.model;

/**
 * Represents a curated LEGO part from the catalog.
 * Immutable record with validation.
 *
 * @param slopeAngle slope angle in degrees (null for standard rectangular parts)
 * @param slopeDir   slope direction string ("+x", "-x", "+y", "-y"), null for non-slope parts
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
    boolean active,
    Double slopeAngle,
    String slopeDir
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
     * Backward-compatible constructor without slope fields (defaults to null).
     */
    public CatalogPart(
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
        this(partId, name, categoryId, categoryName, studX, studY,
             heightUnitsRaw, material, active, null, null);
    }

    /**
     * Creates a CatalogPart from CSV row data with field-level validation.
     * Backward-compatible factory without slope fields.
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
            partId, name, categoryId, categoryName, studX, studY,
            heightUnitsRaw, material, active, null, null
        );
    }

    /**
     * Creates a CatalogPart with slope metadata.
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
        boolean active,
        Double slopeAngle,
        String slopeDir
    ) {
        return new CatalogPart(
            partId, name, categoryId, categoryName, studX, studY,
            heightUnitsRaw, material, active, slopeAngle, slopeDir
        );
    }
}
