package com.lego.optimize;

/**
 * Precomputed per-voxel feature data consumed by placement policies.
 *
 * This type is color-space-agnostic — it stores precomputed CIELAB values
 * and a variance map, but performs no color-space conversion itself. Use
 * ColorFeatureGridFactory (in the color package) to build instances
 * from RGB voxel data.
 */
public final class PlacementFeatureGrid {

    /** ΔE threshold used for region-uniformity scoring. */
    private final double colorDiffThreshold;

    /**
     * Precomputed CIELAB values per voxel: labValues[x][y][z] is a
     * 3-element array [L, a, b], or null if the voxel has
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
     * Finds the maximum pairwise CIE76 ΔE among all voxels in the
     * region and maps it to [0.0, 1.0]: ΔE = 0 → 1.0 (perfectly uniform),
     * ΔE ≥ threshold → 0.0 (maximally varied).
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

        // Collect per-channel min/max across the brick volume in a single pass.
        // The AABB diagonal sqrt((maxL-minL)²+(maxA-minA)²+(maxB-minB)²) is an
        // O(n) upper bound on the true max pairwise ΔE76, which was O(n²).
        // It equals the true diameter when the two most-distant Lab points sit at
        // diagonally-opposite corners of the bounding box, and conservatively
        // overestimates otherwise (biasing toward smaller bricks in edge cases).
        double minL = Double.MAX_VALUE, maxL = -Double.MAX_VALUE;
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE;
        double minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
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
                            if (lab[0] < minL) minL = lab[0];
                            if (lab[0] > maxL) maxL = lab[0];
                            if (lab[1] < minA) minA = lab[1];
                            if (lab[1] > maxA) maxA = lab[1];
                            if (lab[2] < minB) minB = lab[2];
                            if (lab[2] > maxB) maxB = lab[2];
                            count++;
                        }
                    }
                }
            }
        }

        if (count <= 1) {
            return 1.0;
        }

        double dL = maxL - minL;
        double dA = maxA - minA;
        double dB = maxB - minB;
        double maxDeltaE = Math.sqrt(dL * dL + dA * dA + dB * dB);

        return Math.max(0.0, 1.0 - maxDeltaE / colorDiffThreshold);
    }

    /** Dimensions of the grid (X axis). */
    public int width() { return highVariance.length; }
    /** Dimensions of the grid (Y axis). */
    public int height() { return highVariance[0].length; }
    /** Dimensions of the grid (Z axis). */
    public int depth() { return highVariance[0][0].length; }
    /** Returns the configured ΔE threshold used to normalize color uniformity. */
    public double colorDiffThreshold() { return colorDiffThreshold; }
}
