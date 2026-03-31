package com.lego.ldraw;

import java.nio.file.Path;

/**
 * Locates a valid local LDraw library root.
 */
public interface PartLibraryLocator {

    /**
     * Resolves the local LDraw library root path.
     *
     * @param overridePath explicit user-provided path or null
     * @return validated library root
     */
    Path locate(Path overridePath);
}
