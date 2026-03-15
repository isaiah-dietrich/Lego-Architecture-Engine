package com.lego.data;

import java.nio.file.Path;
import java.util.List;

import com.lego.model.CatalogPart;

/**
 * CSV-backed implementation of CatalogPartRepository.
 * Delegates to the existing CuratedCatalogLoader.
 */
public final class CsvCatalogPartRepository implements CatalogPartRepository {

    private final Path baseDir;

    /**
     * Creates a repository that resolves the catalog from the given base directory.
     *
     * @param baseDir base directory, or null for default resolution
     */
    public CsvCatalogPartRepository(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** Creates a repository using default catalog path resolution. */
    public CsvCatalogPartRepository() {
        this(null);
    }

    @Override
    /** Loads and returns all active catalog parts from the CSV file. */
    public List<CatalogPart> findActiveParts() {
        if (baseDir != null) {
            return CuratedCatalogLoader.loadActiveParts(baseDir);
        }
        return CuratedCatalogLoader.loadActiveParts();
    }
}
