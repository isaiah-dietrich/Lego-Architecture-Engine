package com.lego.color;

import com.lego.model.ColorRgb;
import com.lego.optimize.PlacementFeatureGrid;

/**
 * Builds a {@link PlacementFeatureGrid} from a per-voxel RGB color grid.
 *
 * <p>This factory lives in the {@code color} package because it performs
 * color-space conversion (linear RGB → CIELAB). The resulting grid is
 * color-space-agnostic and can be consumed by the {@code optimize} package
 * without any color imports.</p>
 */
public final class ColorFeatureGridFactory {

    /** ΔE threshold: above this, colors are considered perceptually different. */
    private static final double COLOR_DIFF_THRESHOLD = 25.0;

    /**
     * Minimum 6-connected neighbors with ΔE above threshold for a voxel
     * to be considered high-variance.
     */
    private static final int VARIANCE_NEIGHBOR_THRESHOLD = 2;

    private ColorFeatureGridFactory() {}

    /**
     * Creates a placement feature grid from voxel colors.
     *
     * @param voxelColors per-voxel color grid (nullable entries for empty voxels)
     * @return precomputed feature grid with Lab values and variance map
     */
    public static PlacementFeatureGrid create(ColorRgb[][][] voxelColors) {
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

        // Compute variance map from Lab values
        boolean[][][] highVariance = computeVarianceMap(labValues, w, h, d);

        return new PlacementFeatureGrid(labValues, highVariance, COLOR_DIFF_THRESHOLD);
    }

    private static boolean[][][] computeVarianceMap(double[][][][] labValues,
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
}
