package com.lego.color;

import java.util.List;
import java.util.Map;

import com.lego.model.Brick;

/**
 * Result of a colorization pass — the color assignments, palette used,
 * and diagnostic counts.
 */
public record ColorizationResult(
    Map<Brick, Integer> brickColorCodes,
    LegoPaletteMapper palette,
    int coloredCount,
    int smoothedCount
) {}
