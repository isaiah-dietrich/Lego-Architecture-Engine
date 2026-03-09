package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.color.LegoPaletteMapper.PaletteEntry;
import com.lego.color.UVLabPaletteProjection.LightnessStats;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;

/**
 * Color strategy that assigns each brick the palette color that wins a
 * majority vote across the brick's constituent voxels.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Each voxel in a brick has a raw sampled color (from the dominant
 *       overlapping triangle — no averaging at the voxel level).</li>
 *   <li>All voxel colors are converted to L*a*b* and preprocessed with
 *       UVLab's shadow lifting and chroma stabilization to compensate for
 *       baked lighting in GLB textures.</li>
 *   <li>Each preprocessed voxel color is independently mapped to the nearest
 *       LEGO palette entry using CIEDE2000.</li>
 *   <li>The palette code that appears most often across the brick's voxels
 *       wins and is assigned to the entire brick.</li>
 * </ol>
 *
 * <h2>Why this is better than averaging</h2>
 * <p>The default pipeline averages colors at two levels (triangle→voxel,
 * voxel→brick), producing blended intermediate colors that may match
 * an unintended palette entry. Voting avoids this: each voxel votes for
 * a discrete palette color, and the majority wins. Color boundaries stay
 * sharp, and a single noisy voxel can't drag the average into wrong-hue
 * territory.
 *
 * <h2>Fallback</h2>
 * <p>When called through the standard {@link #apply} interface (which
 * receives pre-averaged brick colors), this strategy falls back to
 * CIEDE2000 nearest-match on the averaged color — identical to UVLab
 * without shadow lifting. For full benefit, use via
 * {@link #applyWithVoxelColors}.
 */
public final class DominantVoteStrategy implements ColorStrategy {

    @Override
    public String name() {
        return "dominant";
    }

    @Override
    public String description() {
        return "Per-voxel palette voting — each voxel votes for a palette color, majority wins per brick";
    }

    /**
     * Fallback: maps pre-averaged brick colors using CIEDE2000.
     * For full dominant-vote behavior, use {@link #applyWithVoxelColors}.
     */
    @Override
    public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette) {
        List<PaletteEntry> entries = palette.opaqueEntries();
        Map<Brick, Integer> result = new HashMap<>(brickColors.size());
        for (Map.Entry<Brick, ColorRgb> entry : brickColors.entrySet()) {
            ColorRgb rgb = entry.getValue();
            double[] lab = LegoPaletteMapper.linearRgbToLab(rgb.r(), rgb.g(), rgb.b());
            result.put(entry.getKey(), nearestCiede2000(lab[0], lab[1], lab[2], entries));
        }
        return result;
    }

    /**
     * Full dominant-vote pathway: each voxel votes for a palette color,
     * and the most-voted palette code wins per brick.
     *
     * @param brickVoxelColors map from brick to its per-voxel sampled colors
     * @param palette          the loaded LEGO palette
     * @return map from brick to LDraw color code
     */
    public Map<Brick, Integer> applyWithVoxelColors(
            Map<Brick, List<ColorRgb>> brickVoxelColors, LegoPaletteMapper palette) {
        List<PaletteEntry> entries = palette.opaqueEntries();
        Map<Brick, Integer> result = new HashMap<>(brickVoxelColors.size());

        // Convert ALL voxel colors to L*a*b* and collect lightness values for statistics
        List<Double> allL = new ArrayList<>();
        Map<Brick, List<double[]>> brickVoxelLab = new HashMap<>(brickVoxelColors.size());

        for (Map.Entry<Brick, List<ColorRgb>> entry : brickVoxelColors.entrySet()) {
            List<double[]> labs = new ArrayList<>(entry.getValue().size());
            for (ColorRgb rgb : entry.getValue()) {
                double[] lab = LegoPaletteMapper.linearRgbToLab(rgb.r(), rgb.g(), rgb.b());
                labs.add(lab);
                allL.add(lab[0]);
            }
            brickVoxelLab.put(entry.getKey(), labs);
        }

        // Compute global lightness stats and apply shadow lifting + chroma stabilization
        LightnessStats stats = UVLabPaletteProjection.computeLightnessStats(allL);
        for (List<double[]> labs : brickVoxelLab.values()) {
            for (double[] lab : labs) {
                if (stats != null) {
                    lab[0] = UVLabPaletteProjection.normalizeLightness(lab[0], stats);
                }
                UVLabPaletteProjection.stabilizeChroma(lab);
            }
        }

        // Per-brick voting: each voxel votes for nearest palette entry, majority wins
        for (Map.Entry<Brick, List<double[]>> entry : brickVoxelLab.entrySet()) {
            Map<Integer, Integer> votes = new HashMap<>();
            for (double[] lab : entry.getValue()) {
                int code = nearestCiede2000(lab[0], lab[1], lab[2], entries);
                votes.merge(code, 1, Integer::sum);
            }

            int bestCode = -1;
            int bestCount = 0;
            for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
                if (vote.getValue() > bestCount) {
                    bestCount = vote.getValue();
                    bestCode = vote.getKey();
                }
            }
            if (bestCode >= 0) {
                result.put(entry.getKey(), bestCode);
            }
        }

        return result;
    }

    private static int nearestCiede2000(double l, double a, double b, List<PaletteEntry> entries) {
        PaletteEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (PaletteEntry entry : entries) {
            double dist = LegoPaletteMapper.deltaE2000(l, a, b,
                entry.labL(), entry.labA(), entry.labB());
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        if (best == null) {
            throw new IllegalStateException("No opaque palette entries available for matching");
        }
        return best.ldrawCode();
    }
}
