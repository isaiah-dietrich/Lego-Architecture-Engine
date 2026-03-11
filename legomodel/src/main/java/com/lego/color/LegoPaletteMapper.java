package com.lego.color;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.lego.model.ColorRgb;

/**
 * Maps linear RGB colors to the nearest official LDraw color code.
 *
 * <p>Uses the Rebrickable color table at {@code data/raw/rebrickable/colors.csv}.
 * Only opaque entries ({@code is_trans=FALSE}) participate in matching. Distance
 * is computed in CIE L*a*b* (CIELAB) using the ΔE76 formula, which models
 * perceptual color difference better than Euclidean RGB distance.
 *
 * <p>The {@code id} field from the CSV is used directly as the LDraw color code
 * in {@code .ldr} output, restricted to the standard range {@code [0, 511]}.
 */
public final class LegoPaletteMapper {

    private static final int MAX_STANDARD_LDRAW_CODE = 511;

    /**
     * Name prefixes that indicate specialty surface finishes.
     * These colors represent metallic/pearl/chrome/etc. effects rather than
     * standard matte colors, and are excluded from nearest-color matching.
     */
    private static final Set<String> EFFECT_PREFIXES = Set.of(
        "pearl", "chrome", "metallic", "speckle", "milky",
        "satin", "glitter", "glow in dark", "flat silver",
        "copper"
    );

    /** A single entry from the palette. */
    public record PaletteEntry(int ldrawCode, String name, double labL, double labA, double labB, boolean isTrans) {}

    private final List<PaletteEntry> opaqueEntries;
    private final List<PaletteEntry> allEntries;

    private LegoPaletteMapper(List<PaletteEntry> entries) {
        this.allEntries = List.copyOf(entries);
        this.opaqueEntries = entries.stream()
            .filter(e -> !e.isTrans())
            .filter(e -> !isEffectColor(e.name()))
            .toList();
    }

    /**
     * Returns true if the color name indicates a specialty surface finish.
     */
    static boolean isEffectColor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return EFFECT_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    /**
     * Loads the palette from the Rebrickable CSV at the default project location.
     */
    public static LegoPaletteMapper loadDefault() throws IOException {
        Path csvPath = Path.of("data", "raw", "rebrickable", "colors.csv");
        return load(csvPath);
    }

    /**
     * Loads the palette from a specific CSV path.
     */
    public static LegoPaletteMapper load(Path csvPath) throws IOException {
        List<PaletteEntry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Format: id,name,rgb,is_trans
                String[] parts = line.split(",", 4);
                if (parts.length < 4) continue;

                int id = Integer.parseInt(parts[0].trim());
                String name = parts[1].trim();
                String hex = parts[2].trim();
                boolean isTrans = "TRUE".equalsIgnoreCase(parts[3].trim());

                if (id < 0 || id > MAX_STANDARD_LDRAW_CODE) {
                    continue;
                }

                // Parse sRGB hex → linear → L*a*b*
                float sR = Integer.parseInt(hex.substring(0, 2), 16) / 255f;
                float sG = Integer.parseInt(hex.substring(2, 4), 16) / 255f;
                float sB = Integer.parseInt(hex.substring(4, 6), 16) / 255f;

                double linR = srgbToLinear(sR);
                double linG = srgbToLinear(sG);
                double linB = srgbToLinear(sB);

                double[] lab = linearRgbToLab(linR, linG, linB);
                entries.add(new PaletteEntry(id, name, lab[0], lab[1], lab[2], isTrans));
            }
        }
        return new LegoPaletteMapper(entries);
    }

    /**
     * Finds the nearest opaque LDraw color for the given linear RGB input.
     *
     * @param color linear RGB input color from GLB
     * @return the LDraw color code (the {@code id} field from the CSV)
     */
    public int nearestLDrawColor(ColorRgb color) {
        double[] lab = linearRgbToLab(color.r(), color.g(), color.b());
        return nearestEntry(lab[0], lab[1], lab[2]).ldrawCode();
    }

    /**
     * Finds the nearest opaque palette entry for the given L*a*b* color.
     */
    PaletteEntry nearestEntry(double l, double a, double b) {
        PaletteEntry best = null;
        double bestDist = Double.MAX_VALUE;

        for (PaletteEntry entry : opaqueEntries) {
            double dist = deltaE(l, a, b, entry.labL(), entry.labA(), entry.labB());
            if (dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }

        if (best == null) {
            throw new IllegalStateException("No opaque palette entries loaded");
        }
        return best;
    }

    /**
     * Returns the number of opaque palette entries.
     */
    public int opaqueEntryCount() {
        return opaqueEntries.size();
    }

    /**
     * Returns the list of opaque, non-effect palette entries used for matching.
     * The returned list is unmodifiable.
     */
    public List<PaletteEntry> opaqueEntries() {
        return opaqueEntries;
    }

    /**
     * Returns the L*a*b* coordinates for a given LDraw color code,
     * or {@code null} if the code is not in the palette.
     */
    public double[] labForCode(int ldrawCode) {
        for (PaletteEntry e : allEntries) {
            if (e.ldrawCode() == ldrawCode) {
                return new double[] { e.labL(), e.labA(), e.labB() };
            }
        }
        return null;
    }

    /**
     * Looks up the color name for a given LDraw color code.
     * Searches all loaded entries (including effect colors).
     *
     * @param ldrawCode the color code to look up
     * @return the color name, or "Unknown" if not found
     */
    public String getColorName(int ldrawCode) {
        return allEntries.stream()
            .filter(e -> e.ldrawCode() == ldrawCode)
            .map(PaletteEntry::name)
            .findFirst()
            .orElse("Unknown");
    }

    // ---- Color space conversion ----

    /** CIE76 ΔE: Euclidean distance in L*a*b*. */
    static double deltaE(double l1, double a1, double b1, double l2, double a2, double b2) {
        double dl = l1 - l2;
        double da = a1 - a2;
        double db = b1 - b2;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * CIEDE2000 color difference formula.
     *
     * <p>This is the modern perceptual color difference metric that properly
     * weights lightness, chroma, and hue differences. It dramatically reduces
     * cross-hue mismatches compared to ΔE76 (e.g., dark brown shadows being
     * matched to Dark Red or Magenta).
     *
     * <p>Reference: Sharma, Wu, Dalal (2005), "The CIEDE2000 Color-Difference
     * Formula: Implementation Notes, Supplementary Test Data, and Mathematical
     * Observations", Color Research & Application, 30(1), 21-30.
     *
     * @return CIEDE2000 ΔE value (lower = more similar)
     */
    static double deltaE2000(double l1, double a1, double b1, double l2, double a2, double b2) {
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

        // Step 2: Calculate ΔL', ΔC', ΔH'
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

        // Parametric weighting factors (all 1.0 for standard CIEDE2000)
        double dlTerm = dLp / sl;
        double dcTerm = dCp / sc;
        double dhTerm = dHp / sh;

        return Math.sqrt(dlTerm * dlTerm + dcTerm * dcTerm + dhTerm * dhTerm + rt * dcTerm * dhTerm);
    }

    /**
     * CIEDE2000 with a custom lightness weight (kL).
     *
     * <p>Higher kL de-weights lightness differences, making hue and chroma
     * more important in the match. This is useful for textured models with
     * baked lighting where dark shadows should still match same-hue palette
     * entries rather than wrong-hue entries at similar darkness.
     *
     * @param kL lightness parametric factor (1.0 = standard, 2.0 = half lightness weight)
     * @return CIEDE2000 ΔE value
     */
    static double deltaE2000(double l1, double a1, double b1,
                             double l2, double a2, double b2,
                             double kL) {
        double c1 = Math.sqrt(a1 * a1 + b1 * b1);
        double c2 = Math.sqrt(a2 * a2 + b2 * b2);
        double cBar = (c1 + c2) / 2.0;

        double cBar7 = Math.pow(cBar, 7);
        double g = 0.5 * (1 - Math.sqrt(cBar7 / (cBar7 + 6103515625.0)));

        double a1p = a1 * (1 + g);
        double a2p = a2 * (1 + g);

        double c1p = Math.sqrt(a1p * a1p + b1 * b1);
        double c2p = Math.sqrt(a2p * a2p + b2 * b2);

        double h1p = Math.toDegrees(Math.atan2(b1, a1p));
        if (h1p < 0) h1p += 360;
        double h2p = Math.toDegrees(Math.atan2(b2, a2p));
        if (h2p < 0) h2p += 360;

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

    /** sRGB gamma-encoded [0,1] → linear [0,1]. */
    static double srgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        }
        return Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Linear RGB [0,1] → CIE L*a*b* (D65 illuminant). */
    static double[] linearRgbToLab(double r, double g, double b) {
        // Linear sRGB → XYZ (D65)
        double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;

        // D65 reference white
        double xn = 0.95047, yn = 1.00000, zn = 1.08883;

        double fx = labF(x / xn);
        double fy = labF(y / yn);
        double fz = labF(z / zn);

        double labL = 116 * fy - 16;
        double labA = 500 * (fx - fy);
        double labB = 200 * (fy - fz);

        return new double[] { labL, labA, labB };
    }

    /** L*a*b* nonlinear transform: f(t) = t^(1/3) if t > δ³, else t/(3δ²) + 4/29. */
    private static double labF(double t) {
        double delta = 6.0 / 29.0;
        if (t > delta * delta * delta) {
            return Math.cbrt(t);
        }
        return t / (3 * delta * delta) + 4.0 / 29.0;
    }
}
