package com.lego.color;

import com.lego.model.ColorRgb;
import com.lego.optimize.PlacementFeatureGrid;

/**
 * Builds a PlacementFeatureGrid from a per-voxel RGB color grid.
 *
 * This factory lives in the color package because it performs
 * color-space conversion (linear RGB → CIELAB). The resulting grid is
 * color-space-agnostic and can be consumed by the optimize package
 * without any color imports.
 */
public final class ColorFeatureGridFactory {

    /** ΔE threshold: above this, colors are considered perceptually different. */
    private static final double COLOR_DIFF_THRESHOLD = 12.0;

    /**
     * Minimum 6-connected neighbors with ΔE above threshold for a voxel
     * to be considered high-variance.
     */
    private static final int VARIANCE_NEIGHBOR_THRESHOLD = 1;

    /** Non-instantiable factory class. */
    private ColorFeatureGridFactory() {}

    /**
     * Creates a placement feature grid from voxel colors.
     *
     * @param voxelColors per-voxel color grid (nullable entries for empty voxels)
     * @return precomputed feature grid with Lab values and variance map
     */
    public static PlacementFeatureGrid create(ColorRgb[][][] voxelColors) {
        return create(voxelColors, null);
    }

    /**
     * Creates a placement feature grid from voxel colors with optional palette-aware
     * variance detection.
     *
     * If a palette is provided, the high-variance map is computed from nearest
     * LEGO palette codes rather than raw Lab deltas. This avoids forcing tiny
     * bricks for shading/noise that ultimately maps to the same LEGO color.
     *
     * @param voxelColors per-voxel color grid (nullable entries for empty voxels)
     * @param palette     optional palette for variance quantization; null keeps
     *                    raw-Lab variance behavior
     * @return precomputed feature grid with Lab values and variance map
     */
    public static PlacementFeatureGrid create(ColorRgb[][][] voxelColors,
                                              LegoPaletteMapper palette) {
        int w = voxelColors.length;
        int h = voxelColors[0].length;
        int d = voxelColors[0][0].length;

        // Convert RGB → Lab
        double[][][][] labValues = new double[w][h][d][];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    ColorRgb c = voxelColors[x][y][z];
                    if (c != null) {
                        labValues[x][y][z] = LegoPaletteMapper.linearRgbToLab(
                            c.r(), c.g(), c.b());
                    }
                }
            }
        }

        // Compute variance map: palette-aware when possible, raw-Lab otherwise.
        boolean[][][] highVariance = (palette != null)
            ? computeVarianceMapByPalette(labValues, w, h, d, palette)
            : computeVarianceMapByLab(labValues, w, h, d);

        return new PlacementFeatureGrid(labValues, highVariance, COLOR_DIFF_THRESHOLD);
    }

    /** Computes a per-voxel boolean map: true where neighboring Lab values exceed the variance threshold. */
    private static boolean[][][] computeVarianceMapByLab(double[][][][] labValues,
                                                          int w, int h, int d) {
        boolean[][][] map = new boolean[w][h][d];
        int[][] deltas = {{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,-1,0},{0,1,0}};

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    double[] lab = labValues[x][y][z];
                    if (lab == null) continue;

                    int changes = 0;
                    for (int[] delta : deltas) {
                        int nx = x + delta[0];
                        int ny = y + delta[1];
                        int nz = z + delta[2];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && nz >= 0 && nz < d) {
                            double[] nlab = labValues[nx][ny][nz];
                            if (nlab != null) {
                                double dl = lab[0] - nlab[0];
                                double da = lab[1] - nlab[1];
                                double db = lab[2] - nlab[2];
                                double de = Math.sqrt(dl*dl + da*da + db*db);
                                if (de > COLOR_DIFF_THRESHOLD) {
                                    changes++;
                                }
                            }
                        }
                    }
                    map[x][y][z] = changes >= VARIANCE_NEIGHBOR_THRESHOLD;
                }
            }
        }
        return map;
    }

    /**
     * Computes a per-voxel boolean map using nearest LEGO palette-code changes.
     *
     * Neighbor changes only count when adjacent voxels quantize to different
     * palette entries.
     */
    private static boolean[][][] computeVarianceMapByPalette(double[][][][] labValues,
                                                              int w, int h, int d,
                                                              LegoPaletteMapper palette) {
        boolean[][][] map = new boolean[w][h][d];
        int[][][] codeGrid = new int[w][h][d];
        int[][] deltas = {{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,-1,0},{0,1,0}};

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    codeGrid[x][y][z] = -1;
                    double[] lab = labValues[x][y][z];
                    if (lab != null) {
                        codeGrid[x][y][z] = palette.nearestEntry(lab[0], lab[1], lab[2]).ldrawCode();
                    }
                }
            }
        }

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    int code = codeGrid[x][y][z];
                    if (code < 0) continue;

                    int changes = 0;
                    for (int[] delta : deltas) {
                        int nx = x + delta[0];
                        int ny = y + delta[1];
                        int nz = z + delta[2];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && nz >= 0 && nz < d) {
                            int neighborCode = codeGrid[nx][ny][nz];
                            if (neighborCode >= 0 && neighborCode != code) {
                                changes++;
                            }
                        }
                    }
                    map[x][y][z] = changes >= VARIANCE_NEIGHBOR_THRESHOLD;
                }
            }
        }
        return map;
    }
}
