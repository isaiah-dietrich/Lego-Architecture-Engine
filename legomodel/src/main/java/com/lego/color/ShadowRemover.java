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
     * Moderate value to avoid compressing genuinely bright colors (White)
     * so far that they map to gray palette entries.
     */
    static final double REGION_HIGHLIGHT_COMPRESS = 0.3;

    /**
     * L* floor below which shadow lifting is progressively reduced.
     * Values below this are likely intentionally dark features (eyes, nose)
     * rather than shadow-affected colored surfaces.
     */
    static final double DARK_FEATURE_FLOOR = 20.0;

    /**
     * How aggressively to compress a/b warm shifts in shadow regions.
     * 0.0 = no compression, 1.0 = snap to median. Default is moderate.
     */
    static final double CHROMINANCE_COMPRESS_STRENGTH = 0.7;

    /**
     * Minimum chroma ratio (voxel chroma / median chroma) below which
     * chrominance normalization is skipped. Voxels with much lower chroma
     * than the model median are genuinely different features (dark neutral
     * eyes, black nose) rather than warm-shifted shadows. Compressing them
     * toward the warm median would destroy their identity.
     *
     * Body shadows typically have 60-80% of the median chroma. Eye/nose
     * features have 20-40%. Threshold 0.5 cleanly separates these.
     */
    static final double CHROMA_RATIO_FLOOR = 0.5;

    /**
     * Maximum hue angle difference (radians) from the model's dominant hue
    // ---- Lightness statistics ----

    /**
     * Robust statistics for lightness distribution: median and IQR.
     */
    public record LightnessStats(double median, double q1, double q3, double iqr) {
        public double shadowThreshold() { return median - 0.5 * iqr; }
        public double highlightThreshold() { return median + 0.5 * iqr; }
    }

    /**
     * Statistics for chrominance channels: medians for normalization.
     */
    public record ChrominanceStats(double medianA, double medianB) {}

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
     * Computes median a* and b* from a list of Lab arrays.
     * Returns null if fewer than 4 values.
     */
    public static ChrominanceStats computeChrominanceStats(List<double[]> labValues) {
        if (labValues.size() < 4) return null;

        List<Double> aVals = new ArrayList<>(labValues.size());
        List<Double> bVals = new ArrayList<>(labValues.size());
        for (double[] lab : labValues) {
            aVals.add(lab[1]);
            bVals.add(lab[2]);
        }
        aVals.sort(Double::compareTo);
        bVals.sort(Double::compareTo);

        return new ChrominanceStats(
                percentile(aVals, 50),
                percentile(bVals, 50));
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

    // ---- Chrominance normalization ----

    /**
     * Compresses chrominance toward the global median for voxels in shadow regions.
     *
     * Baked lighting doesn't just darken shadows -- it shifts their hue warm
     * (increased a, increased b). This produces spurious red/pink palette
     * matches (Sand Red, Dark Pink, Sand Purple) on surfaces that should be
     * a uniform golden/tan.
     *
     * For voxels whose original (pre-lift) lightness was below the shadow
     * threshold, compress their chrominance toward the model median proportionally
     * to how deep in shadow they were. Non-shadow voxels are untouched.
     * Very dark voxels (below {@link #DARK_FEATURE_FLOOR}) get reduced
     * compression to preserve genuinely dark features.
     *
     * @param lab              Lab array, modified in place (a and b channels)
     * @param originalL        the voxel's L before shadow lifting
     * @param lightnessStats   lightness statistics for the model
     * @param chromStats       chrominance statistics (median a, median b)
     */
    public static void normalizeChrominance(double[] lab, double originalL,
                                            LightnessStats lightnessStats,
                                            ChrominanceStats chromStats) {
        if (lightnessStats == null || chromStats == null) return;
        if (lightnessStats.iqr() <= 0) return;

        double shadowThresh = lightnessStats.shadowThreshold();
        if (originalL >= shadowThresh) return; // not in shadow — leave alone

        // How deep in shadow: 0 at threshold, 1 at well below threshold
        double depth = (shadowThresh - originalL) / (lightnessStats.iqr() + 1e-9);
        double t = Math.min(1.0, depth) * CHROMINANCE_COMPRESS_STRENGTH;

        // Reduce compression for dark values to preserve genuinely dark features.
        // Below L*=10: no compression. L*=10–25: gradual ramp. Above L*=25: full.
        if (originalL < 10.0) return;
        if (originalL < 25.0) {
            t *= (originalL - 10.0) / 15.0;
        }

        // Skip compression if the voxel's hue is far from the median hue.
        // Chrominance normalization is designed to correct warm shifts in shadows
        // of a uniform-color surface.  If a voxel's hue angle differs by >90°
        // from the median, it is a genuinely different color (e.g. blue on a
        // warm model) and compressing toward the median would destroy it.
        double medianHue = Math.atan2(chromStats.medianB(), chromStats.medianA());
        double voxelHue  = Math.atan2(lab[2], lab[1]);
        double hueDiff   = Math.abs(medianHue - voxelHue);
        if (hueDiff > Math.PI) hueDiff = 2 * Math.PI - hueDiff;
        if (hueDiff > Math.PI / 2) return;

        // Skip compression if the voxel's chroma is much lower than the
        // median chroma. Low-chroma voxels in shadow regions are genuinely
        // different features (dark neutral eyes, black nose), not warm-shifted
        // shadows. Compressing them toward the warm median would make them
        // match body colors instead of their correct dark palette entries.
        double voxelChroma = Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
        double medianChroma = Math.sqrt(
                chromStats.medianA() * chromStats.medianA()
                + chromStats.medianB() * chromStats.medianB());
        if (medianChroma > 1.0 && voxelChroma / medianChroma < CHROMA_RATIO_FLOOR) return;

        // Compress a*/b* toward median
        lab[1] = lab[1] + (chromStats.medianA() - lab[1]) * t;
        lab[2] = lab[2] + (chromStats.medianB() - lab[2]) * t;
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

        if (chroma < 3.0) return;

        if (chroma < MIN_CHROMA) {
            double scale = MIN_CHROMA / chroma;
            lab[1] = a * scale;
            lab[2] = b * scale;
        }
    }
}
