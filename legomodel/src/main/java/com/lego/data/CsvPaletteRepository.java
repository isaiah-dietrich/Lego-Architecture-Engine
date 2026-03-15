package com.lego.data;

import java.io.IOException;
import java.nio.file.Path;

import com.lego.color.LegoPaletteMapper;

/**
 * CSV-backed implementation of {@link PaletteRepository}.
 * Delegates to the existing {@link LegoPaletteMapper} static load methods.
 */
public final class CsvPaletteRepository implements PaletteRepository {

    private final Path csvPath;

    /**
     * Creates a repository that loads palette from an explicit CSV path.
     *
     * @param csvPath path to the palette CSV file
     */
    public CsvPaletteRepository(Path csvPath) {
        this.csvPath = csvPath;
    }

    /** Creates a repository using the default palette CSV resolution. */
    public CsvPaletteRepository() {
        this(null);
    }

    @Override
    public LegoPaletteMapper loadPalette() throws IOException {
        if (csvPath != null) {
            return LegoPaletteMapper.load(csvPath);
        }
        return LegoPaletteMapper.loadDefault();
    }
}
