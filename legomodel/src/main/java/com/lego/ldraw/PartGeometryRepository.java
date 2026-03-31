package com.lego.ldraw;

/**
 * Repository abstraction over resolved part geometry.
 */
public interface PartGeometryRepository {

    PartGeometry load(String partReference);
}
