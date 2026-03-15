package com.lego.data;

import java.util.List;

import com.lego.model.CatalogPart;

/**
 * Abstraction over the source of curated catalog parts.
 * Decouples consumers (AllowedBrickDimensions, LDrawExporter) from
 * the concrete loading mechanism (CSV files, databases, etc.).
 */
public interface CatalogPartRepository {

    /**
     * Returns all active catalog parts in catalog order.
     */
    List<CatalogPart> findActiveParts();
}
