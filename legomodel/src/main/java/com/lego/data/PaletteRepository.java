package com.lego.data;

import java.io.IOException;

import com.lego.color.LegoPaletteMapper;

/**
 * Abstraction over the source of the LEGO color palette.
 * Decouples consumers from the concrete loading mechanism (CSV path resolution).
 */
public interface PaletteRepository {

    /**
     * Loads and returns the palette mapper.
     */
    LegoPaletteMapper loadPalette() throws IOException;
}
