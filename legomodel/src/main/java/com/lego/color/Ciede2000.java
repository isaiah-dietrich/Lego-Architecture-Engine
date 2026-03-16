package com.lego.color;

import java.util.List;

import com.lego.color.LegoPaletteMapper.PaletteEntry;

/**
 * CIEDE2000 perceptual color difference calculation.
 *
 * Implements the modern perceptual color difference metric that properly
 * weights lightness, chroma, and hue differences. It dramatically reduces
 * cross-hue mismatches compared to DE76 (e.g., dark brown shadows being
 * matched to Dark Red or Magenta).
 *
 * Reference: Sharma, Wu, Dalal (2005), "The CIEDE2000 Color-Difference
 * Formula: Implementation Notes, Supplementary Test Data, and Mathematical
 * Observations", Color Research and Application, 30(1), 21-30.
 */
public final class Ciede2000 {

    private Ciede2000() {}

    /**
     * Standard CIEDE2000 color difference (kL = 1.0).
     *
     * @return CIEDE2000 DE value (lower = more similar)
     */
    public static double deltaE(double l1, double a1, double b1,
                                double l2, double a2, double b2) {
        return deltaE(l1, a1, b1, l2, a2, b2, 1.0);
    }

    /**
     * CIEDE2000 with a custom lightness weight (kL).
     *
     * Higher kL de-weights lightness differences, making hue and chroma
     * more important in the match. This is useful for textured models with
     * baked lighting where dark shadows should still match same-hue palette
     * entries rather than wrong-hue entries at similar darkness.
     *
     * @param kL lightness parametric factor (1.0 = standard, 2.0 = half lightness weight)
     * @return CIEDE2000 DE value (lower = more similar)
     */
    public static double deltaE(double l1, double a1, double b1,
                                double l2, double a2, double b2,
                                double kL) {
        // Step 1: Calculate C' and h'
        double c1 = Math.sqrt(a1 * a1 + b1 * b1);
        double c2 = Math.sqrt(a2 * a2 + b2 * b2);
        double cBar = (c1 + c2) / 2.0;

        double cBar7 = Math.pow(cBar, 7);
        double g = 0.5 * (1 - Math.sqrt(cBar7 / (cBar7 + 6103515625.0))); // 25^7

        double a1p = a1 * (1 + g);
        double a2p = a2 * (1 + g);

        double c1p = Math.sqrt(a1p * a1p + b1 * b1);
        double c2p = Math.sqrt(a2p * a2p + b2 * b2);

        double h1p = Math.toDegrees(Math.atan2(b1, a1p));
        if (h1p < 0) h1p += 360;
        double h2p = Math.toDegrees(Math.atan2(b2, a2p));
        if (h2p < 0) h2p += 360;

        // Step 2: Calculate DL', DC', DH'
        double dLp = l2 - l1;
        double dCp = c2p - c1p;

        double dhp;
        if (c1p * c2p == 0) {
            dhp = 0;
        } else if (Math.abs(h2p - h1p) <= 180) {
            dhp = h2p - h1p;
        } else if (h2p - h1p > 180) {
            dhp = h2p - h1p - 360;
        } else {
            dhp = h2p - h1p + 360;
        }

        double dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp / 2));

        // Step 3: Calculate CIEDE2000 weighting functions
        double lBarP = (l1 + l2) / 2.0;
        double cBarP = (c1p + c2p) / 2.0;

        double hBarP;
        if (c1p * c2p == 0) {
            hBarP = h1p + h2p;
        } else if (Math.abs(h1p - h2p) <= 180) {
            hBarP = (h1p + h2p) / 2.0;
        } else if (h1p + h2p < 360) {
            hBarP = (h1p + h2p + 360) / 2.0;
        } else {
            hBarP = (h1p + h2p - 360) / 2.0;
        }

        double t = 1
            - 0.17 * Math.cos(Math.toRadians(hBarP - 30))
            + 0.24 * Math.cos(Math.toRadians(2 * hBarP))
            + 0.32 * Math.cos(Math.toRadians(3 * hBarP + 6))
            - 0.20 * Math.cos(Math.toRadians(4 * hBarP - 63));

        double lBarPm50sq = (lBarP - 50) * (lBarP - 50);
        double sl = 1 + 0.015 * lBarPm50sq / Math.sqrt(20 + lBarPm50sq);
        double sc = 1 + 0.045 * cBarP;
        double sh = 1 + 0.015 * cBarP * t;

        double cBarP7 = Math.pow(cBarP, 7);
        double rt = -2 * Math.sqrt(cBarP7 / (cBarP7 + 6103515625.0))
            * Math.sin(Math.toRadians(60 * Math.exp(-Math.pow((hBarP - 275) / 25.0, 2))));

        double dlTerm = dLp / (kL * sl);
        double dcTerm = dCp / sc;
        double dhTerm = dHp / sh;

        return Math.sqrt(dlTerm * dlTerm + dcTerm * dcTerm + dhTerm * dhTerm + rt * dcTerm * dhTerm);
    }

    /**
     * Finds the nearest opaque palette entry using CIEDE2000 with a custom
     * lightness weight (kL).
     *
     * @return the LDraw color code of the nearest palette entry
     */
    public static int nearestPaletteEntry(double l, double a, double b,
                                          List<PaletteEntry> entries, double kL) {
        PaletteEntry best = null;
        double bestDist = Double.MAX_VALUE;
        for (PaletteEntry entry : entries) {
            double dist = deltaE(l, a, b,
                entry.labL(), entry.labA(), entry.labB(), kL);
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }
        if (best == null) {
            throw new IllegalStateException("No opaque palette entries available");
        }
        return best.ldrawCode();
    }
}
