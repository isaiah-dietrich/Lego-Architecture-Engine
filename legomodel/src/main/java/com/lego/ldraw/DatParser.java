package com.lego.ldraw;

/**
 * Parses an LDraw part graph into normalized triangle geometry.
 */
public interface DatParser {

    /**
     * Parses the requested part reference.
     *
     * @param partReference e.g. 3039.dat
     */
    PartGeometry parse(String partReference);
}
