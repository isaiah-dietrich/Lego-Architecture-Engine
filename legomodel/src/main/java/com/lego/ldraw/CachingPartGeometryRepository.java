package com.lego.ldraw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory geometry repository backed by a DatParser.
 */
public final class CachingPartGeometryRepository implements PartGeometryRepository {

    private final DatParser parser;
    private final Map<String, PartGeometry> cache = new ConcurrentHashMap<>();

    public CachingPartGeometryRepository(DatParser parser) {
        if (parser == null) {
            throw new IllegalArgumentException("parser must not be null");
        }
        this.parser = parser;
    }

    @Override
    public PartGeometry load(String partReference) {
        return cache.computeIfAbsent(partReference, parser::parse);
    }
}
