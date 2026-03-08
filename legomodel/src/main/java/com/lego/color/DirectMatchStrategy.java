package com.lego.color;

import java.util.HashMap;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;

/**
 * Default color strategy: maps each brick's linear RGB directly to the nearest
 * LDraw palette color using ΔE76 (Euclidean distance in CIE L*a*b*).
 *
 * <p>This is the baseline algorithm — no preprocessing, no hue weighting,
 * just the closest perceptual match in the opaque palette.
 */
public final class DirectMatchStrategy implements ColorStrategy {

    @Override
    public String name() {
        return "direct";
    }

    @Override
    public String description() {
        return "Nearest palette match using ΔE76 (CIE L*a*b* Euclidean distance)";
    }

    @Override
    public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette) {
        Map<Brick, Integer> result = new HashMap<>();
        for (Map.Entry<Brick, ColorRgb> entry : brickColors.entrySet()) {
            result.put(entry.getKey(), palette.nearestLDrawColor(entry.getValue()));
        }
        return result;
    }
}
