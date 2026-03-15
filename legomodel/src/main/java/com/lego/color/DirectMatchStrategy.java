package com.lego.color;

import java.util.HashMap;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;

/**
 * Default color strategy: maps each brick's linear RGB directly to the nearest
 * LDraw palette color using ΔE76 (Euclidean distance in CIE L*a*b*).
 *
 * This is the baseline algorithm — no preprocessing, no hue weighting,
 * just the closest perceptual match in the opaque palette.
 */
public final class DirectMatchStrategy implements ColorStrategy {

    @Override
    /** Returns the strategy name ("direct"). */
    public String name() {
        return "direct";
    }

    @Override
    /** Returns a human-readable description of this strategy. */
    public String description() {
        return "Nearest palette match using ΔE76 (CIE L*a*b* Euclidean distance)";
    }

    @Override
    /** Maps each brick's sampled RGB to the nearest palette entry using CIE76 delta-E. */
    public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette) {
        Map<Brick, Integer> result = new HashMap<>();
        for (Map.Entry<Brick, ColorRgb> entry : brickColors.entrySet()) {
            result.put(entry.getKey(), palette.nearestLDrawColor(entry.getValue()));
        }
        return result;
    }
}
