package com.lego.color;

import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;

/**
 * Strategy interface for mapping sampled brick RGB colors to LDraw color codes.
 *
 * Each implementation encapsulates a complete algorithm for converting raw
 * linear RGB colors (from texture sampling) into integer LDraw palette codes.
 * Implementations may include preprocessing (shadow removal, saturation boost),
 * different distance metrics (ΔE76, CIEDE2000), or domain-specific heuristics.
 *
 * Usage: select a strategy by name via ColorStrategyRegistry, then call
 * #apply with the sampled brick colors and a loaded palette. The returned
 * map can be passed directly to ColorSmoother and the LDraw exporter.
 *
 * To add a new algorithm:
 * 
 *   - Create a class implementing ColorStrategy
 *   - Register it in ColorStrategyRegistry#createDefault()
 *   - Run with --color-algorithm=your-name
 * 
 */
public interface ColorStrategy {

    /**
     * Returns a short human-readable name for this strategy (e.g. "direct", "ciede2000").
     * Used in CLI output and logging.
     */
    String name();

    /**
     * Returns a brief description of what this algorithm does.
     * Used in --help and --color-algorithm=list output.
     */
    String description();

    /**
     * Maps each brick's sampled linear RGB color to an LDraw color code.
     *
     * @param brickColors map from brick to its sampled linear RGB color
     * @param palette     the loaded LEGO palette mapper
     * @return map from brick to LDraw color code (integer)
     */
    Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette);
}
