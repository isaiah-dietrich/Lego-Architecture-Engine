package com.lego.color;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone shadow-removal utility for GLB-sourced brick colors.
 *
 * GLB model textures contain baked lighting — shadows, ambient occlusion,
 * specular highlights. These are correct for rendering but corrupt palette
 * matching. This class provides the statistical tools to detect and remove
 * that lighting contamination from L*a*b* color data.
 *
 * Three core operations:
 *
 *   1. Lightness statistics — IQR-based robust statistics that characterize
 *      the model's lighting range without being skewed by outliers.
 *   2. Shadow lifting / highlight compression — smooth ramp that compresses
 *      the dark and bright tails toward the statistical center, with
 *      configurable strength and dark-feature preservation.
 *   3. Chroma stabilization — prevents near-gray shadow colors from landing
 *      on wrong-hue palette entries by boosting their chroma above a minimum
 *      threshold.
 *
 * All operations are pure functions (or near-pure for the in-place chroma
 * stabilization). Thread-safe, no state.
 */
public final class ShadowRemover {

    private ShadowRemover() {} // utility class

    // ---- Constants ----

    /**
     * Minimum chroma (sqrt(a² + b²)) below which a color is considered
     * "desaturated" and eligible for chroma stabilization.
     */
    static final double MIN_CHROMA = 8.0;

    /**
     * Default shadow lift strength for the standard (UVLab) pipeline.
     * 0.0 = no lifting, 1.0 = clamp to median.
     */
    static final double DEFAULT_SHADOW_LIFT = 0.6;

    /**
     * Default highlight compression strength for the standard pipeline.
     */
    static final double DEFAULT_HIGHLIGHT_COMPRESS = 0.3;

    /**
     * Aggressive shadow lift strength for the region pipeline.
     * Higher than the default because region coloring specifically aims
     * to eliminate baked-lighting variation.
     */
    static final double REGION_SHADOW_LIFT = 0.85;

    /**
     * Highlight compression strength for the region pipeline.
     */
    static final double REGION_HIGHLIGHT_COMPRESS = 0.5;

    /**
     * L* floor below which shadow lifting is progressively reduced.
     * Values below this are likely intentionally dark features (eyes, nose)
     * rather than shadow-affected colored surfaces.
     */
    static final double DARK_FEATURE_FLOOR = 20.0;

    // ---- Lightness statistics ----

    /**
     * Robust statistics for lightness distribution: median and IQR.
     */
    public record LightnessStats(double median, double q1, double q3, double iqr) {
        public double shadowThreshold() { return median - 0.5 * iqr; }
        public double highlightThreshold() { return median + 0.5 * iqr; }
    }

    /**
     * Computes median and IQR of the lightness values.
     * Returns null if fewer than 4 values (not enough for meaningful statistics).
     */
    public static LightnessStats computeLightnessStats(List<Double> values) {
        if (values.size() < 4) return null;

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);

        double median = percentile(sorted, 50);
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;

        return new LightnessStats(median, q1, q3, iqr);
    }

    /**
     * Computes a percentile using linear interpolation.
     * @param sorted sorted list of values
     * @param p percentile (0-100)
     */
    private static double percentile(List<Double> sorted, double p) {
        double index = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(index);
        int hi = Math.min(lo + 1, sorted.size() - 1);
        double frac = index - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }

    // ---- Shadow lifting ----

    /**
     * Normalizes a lightness value using the default (UVLab) strengths.
     */
    public static double normalizeLightness(double l, LightnessStats stats) {
        return normalizeLightness(l, stats, DEFAULT_SHADOW_LIFT, DEFAULT_HIGHLIGHT_COMPRESS);
    }

    /**
     * Normalizes a lightness value using the aggressive region strengths.
     */
    public static double normalizeLightnessForRegion(double l, LightnessStats stats) {
        return normalizeLightness(l, stats, REGION_SHADOW_LIFT, REGION_HIGHLIGHT_COMPRESS);
    }

    /**
     * Normalizes a lightness value by lifting shadows and compressing highlights.
     *
     * Uses a smooth ramp: values below the shadow threshold are lifted
     * proportionally toward the median, while values near the median are
     * untouched. Very dark values (L* below {@link #DARK_FEATURE_FLOOR})
     * receive progressively less lifting to preserve intentionally dark
     * features like eyes and noses.
     *
     * @param l                L* value to normalize
     * @param stats            lightness statistics for the model
     * @param shadowStrength   how aggressively to lift shadows (0–1)
     * @param highlightStrength how aggressively to compress highlights (0–1)
     */
    public static double normalizeLightness(double l, LightnessStats stats,
                                            double shadowStrength, double highlightStrength) {
        if (stats.iqr() <= 0) return l;

        double shadowThresh = stats.shadowThreshold();
        double highlightThresh = stats.highlightThreshold();

        if (l < shadowThresh) {
            double deficit = shadowThresh - l;
            double effectiveStrength = shadowStrength;
            if (l < DARK_FEATURE_FLOOR) {
                effectiveStrength *= (l / DARK_FEATURE_FLOOR);
            }
            l = shadowThresh - deficit * (1.0 - effectiveStrength);
        } else if (l > highlightThresh) {
            double excess = l - highlightThresh;
            l = highlightThresh + excess * (1.0 - highlightStrength);
        }

        return Math.max(0, Math.min(100, l));
    }

    // ---- Chroma stabilization ----

    /**
     * Stabilizes near-gray colors by boosting their chroma to the minimum threshold.
     *
     * Very desaturated colors (chroma below {@link #MIN_CHROMA}) sit near the
     * neutral axis where tiny a/b differences can cause large hue shifts in
     * palette matching. This method clamps their chroma to MIN_CHROMA while
     * preserving hue angle. Truly neutral colors (chroma below 1.0) are left
     * unchanged since they should match gray/black/white palette entries.
     *
     * @param lab L*a*b* array, modified in place
     */
    public static void stabilizeChroma(double[] lab) {
        double a = lab[1];
        double b = lab[2];
        double chroma = Math.sqrt(a * a + b * b);

        if (chroma < 1.0) return;

        if (chroma < MIN_CHROMA) {
            double scale = MIN_CHROMA / chroma;
            lab[1] = a * scale;
            lab[2] = b * scale;
        }
    }
}
