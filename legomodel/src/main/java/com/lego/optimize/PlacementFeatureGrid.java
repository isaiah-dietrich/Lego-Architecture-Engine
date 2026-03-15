package com.lego.optimize;

/**
 * Precomputed per-voxel feature data consumed by placement policies.
 *
 * <p>This type is color-space-agnostic — it stores precomputed CIELAB values
 * and a variance map, but performs no color-space conversion itself. Use
 * {@code ColorFeatureGridFactory} (in the color package) to build instances
 * from RGB voxel data.</p>
 */
public final class PlacementFeatureGrid {

    /** ΔE threshold used for region-uniformity scoring. */
    private final double colorDiffThreshold;

    /**
     * Precomputed CIELAB values per voxel: {@code labValues[x][y][z]} is a
     * 3-element array {@code [L, a, b]}, or {@code null} if the voxel has
     * no color data.
     */
    private final double[][][][] labValues;

    /** Per-voxel high-variance flags (force smallest brick when true). */
    private final boolean[][][] highVariance;

    /**
     * @param labValues         precomputed Lab values (nullable entries for empty voxels)
     * @param highVariance      per-voxel variance flags
     * @param colorDiffThreshold ΔE threshold for uniformity mapping
     */
    public PlacementFeatureGrid(double[][][][] labValues,
                                 boolean[][][] highVariance,
                                 double colorDiffThreshold) {
        this.labValues = labValues;
        this.highVariance = highVariance;
        this.colorDiffThreshold = colorDiffThreshold;
    }

    /** Whether the voxel at (x,y,z) is marked high-variance. */
    public boolean isHighVariance(int x, int y, int z) {
        if (x < 0 || x >= highVariance.length
                || y < 0 || y >= highVariance[0].length
                || z < 0 || z >= highVariance[0][0].length) {
            return false;
        }
        return highVariance[x][y][z];
    }

    /** Whether this grid has any color/lab data. */
    public boolean hasColorData() {
        return labValues != null;
    }

    /**
     * Computes color uniformity across the given brick footprint.
     *
     * <p>Finds the maximum pairwise CIE76 ΔE among all voxels in the
     * region and maps it to [0.0, 1.0]: ΔE = 0 → 1.0 (perfectly uniform),
     * ΔE ≥ threshold → 0.0 (maximally varied).</p>
     *
     * @return 1.0 if no color data, volume ≤ 1, or single-color region
     */
    public double computeRegionUniformity(int x, int y, int z,
                                           int studX, int studY, int heightUnits) {
        if (labValues == null) {
            return 1.0;
        }

        int volume = studX * studY * heightUnits;
        if (volume <= 1) {
            return 1.0;
        }

        // Collect Lab values for voxels across the brick volume
        double[][] labs = new double[volume][];
        int count = 0;
        for (int dy = 0; dy < heightUnits; dy++) {
            int cy = y + dy;
            if (cy >= labValues[0].length) continue;
            for (int dx = 0; dx < studX; dx++) {
                for (int dz = 0; dz < studY; dz++) {
                    int cx = x + dx;
                    int cz = z + dz;
                    if (cx < labValues.length && cz < labValues[0][0].length) {
                        double[] lab = labValues[cx][cy][cz];
                        if (lab != null) {
                            labs[count++] = lab;
                        }
                    }
                }
            }
        }

        if (count <= 1) {
            return 1.0;
        }

        // Maximum pairwise ΔE76 (Euclidean distance in Lab)
        double maxDeltaE = 0;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double dl = labs[i][0] - labs[j][0];
                double da = labs[i][1] - labs[j][1];
                double db = labs[i][2] - labs[j][2];
                double de = Math.sqrt(dl * dl + da * da + db * db);
                if (de > maxDeltaE) {
                    maxDeltaE = de;
                }
            }
        }

        return Math.max(0.0, 1.0 - maxDeltaE / colorDiffThreshold);
    }

    /** Dimensions of the grid (X axis). */
    public int width() { return highVariance.length; }
    /** Dimensions of the grid (Y axis). */
    public int height() { return highVariance[0].length; }
    /** Dimensions of the grid (Z axis). */
    public int depth() { return highVariance[0][0].length; }
}
