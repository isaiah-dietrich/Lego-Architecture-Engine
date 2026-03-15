package com.lego.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementFeatureGridTest {

    private static final double THRESHOLD = 25.0;

    private PlacementFeatureGrid gridWithLab(double[][][][] lab, boolean[][][] hv) {
        return new PlacementFeatureGrid(lab, hv, THRESHOLD);
    }

    @Test
    void isHighVarianceOutOfBoundsReturnsFalse() {
        boolean[][][] hv = new boolean[2][2][2];
        hv[0][0][0] = true;
        PlacementFeatureGrid grid = gridWithLab(new double[2][2][2][], hv);

        assertFalse(grid.isHighVariance(-1, 0, 0));
        assertFalse(grid.isHighVariance(0, -1, 0));
        assertFalse(grid.isHighVariance(0, 0, -1));
        assertFalse(grid.isHighVariance(2, 0, 0));
        assertFalse(grid.isHighVariance(0, 2, 0));
        assertFalse(grid.isHighVariance(0, 0, 2));
    }

    @Test
    void isHighVarianceReturnsTrue() {
        boolean[][][] hv = new boolean[2][2][2];
        hv[1][1][1] = true;
        PlacementFeatureGrid grid = gridWithLab(new double[2][2][2][], hv);

        assertTrue(grid.isHighVariance(1, 1, 1));
        assertFalse(grid.isHighVariance(0, 0, 0));
    }

    @Test
    void computeRegionUniformityIdenticalColorsReturnsOne() {
        double[][][][] lab = new double[3][3][3][];
        // All voxels have the same Lab: L=50, a=0, b=0
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++)
                    lab[x][y][z] = new double[]{50.0, 0.0, 0.0};

        PlacementFeatureGrid grid = gridWithLab(lab, new boolean[3][3][3]);

        // 2×2×2 brick at origin → all identical colors → uniformity = 1.0
        assertEquals(1.0, grid.computeRegionUniformity(0, 0, 0, 2, 2, 2), 1e-9);
    }

    @Test
    void computeRegionUniformityDifferentColorsLessThanOne() {
        double[][][][] lab = new double[2][2][2][];
        lab[0][0][0] = new double[]{0.0, 0.0, 0.0};   // black in Lab
        lab[1][0][0] = new double[]{100.0, 0.0, 0.0};  // white in Lab
        lab[0][1][0] = new double[]{50.0, 0.0, 0.0};
        lab[1][1][0] = new double[]{50.0, 0.0, 0.0};
        lab[0][0][1] = new double[]{50.0, 0.0, 0.0};
        lab[1][0][1] = new double[]{50.0, 0.0, 0.0};
        lab[0][1][1] = new double[]{50.0, 0.0, 0.0};
        lab[1][1][1] = new double[]{50.0, 0.0, 0.0};

        PlacementFeatureGrid grid = gridWithLab(lab, new boolean[2][2][2]);

        double uniformity = grid.computeRegionUniformity(0, 0, 0, 2, 2, 2);
        // Max ΔE = 100, threshold = 25 → 1 - 100/25 = -3.0 clamped to 0.0
        assertEquals(0.0, uniformity, 1e-9);
    }

    @Test
    void computeRegionUniformitySingleVoxelReturnsOne() {
        double[][][][] lab = new double[1][1][1][];
        lab[0][0][0] = new double[]{50.0, 20.0, -10.0};

        PlacementFeatureGrid grid = gridWithLab(lab, new boolean[1][1][1]);

        assertEquals(1.0, grid.computeRegionUniformity(0, 0, 0, 1, 1, 1), 1e-9);
    }

    @Test
    void computeRegionUniformityNullLabReturnsOne() {
        PlacementFeatureGrid grid = new PlacementFeatureGrid(null, new boolean[2][2][2], THRESHOLD);
        assertEquals(1.0, grid.computeRegionUniformity(0, 0, 0, 2, 2, 2), 1e-9);
    }

    @Test
    void hasColorDataFalseWhenLabNull() {
        PlacementFeatureGrid grid = new PlacementFeatureGrid(null, new boolean[1][1][1], THRESHOLD);
        assertFalse(grid.hasColorData());
    }

    @Test
    void hasColorDataTrueWhenLabPresent() {
        PlacementFeatureGrid grid = gridWithLab(new double[1][1][1][], new boolean[1][1][1]);
        assertTrue(grid.hasColorData());
    }

    @Test
    void dimensionsMatchInput() {
        PlacementFeatureGrid grid = gridWithLab(new double[3][4][5][], new boolean[3][4][5]);
        assertEquals(3, grid.width());
        assertEquals(4, grid.height());
        assertEquals(5, grid.depth());
    }
}
