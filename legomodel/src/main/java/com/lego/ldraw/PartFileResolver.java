package com.lego.ldraw;

import java.nio.file.Path;

/**
 * Resolves LDraw part/subpart references to concrete local files.
 */
public interface PartFileResolver {

    /**
     * Resolves a part reference name (e.g. "3039.dat", "s/3039s01.dat").
     */
    Path resolve(String reference);
}
