package com.lego.optimize;

import java.util.List;

import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Canonical pre-placement target derived from surface voxels and normals.
 */
public final class PlacementTargetGrid {

    private static final double MIN_SLOPE_INCLINATION_DEG = 20.0;

    private final VoxelGrid surface;
    private final PlacementFeatureGrid featureGrid;
    private final boolean[][][] requiredMask;
    private final boolean[][][] slopeEligibleMask;
    private final int requiredCount;

    private PlacementTargetGrid(VoxelGrid surface,
                                PlacementFeatureGrid featureGrid,
                                boolean[][][] requiredMask,
                                boolean[][][] slopeEligibleMask,
                                int requiredCount) {
        this.surface = surface;
        this.featureGrid = featureGrid;
        this.requiredMask = requiredMask;
        this.slopeEligibleMask = slopeEligibleMask;
        this.requiredCount = requiredCount;
    }

    public static PlacementTargetGrid fromSurface(VoxelGrid surface,
                                                  List<BrickSpec> allowedSpecs,
                                                  PlacementFeatureGrid featureGrid) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        boolean[][][] required = new boolean[surface.width()][surface.height()][surface.depth()];
        boolean[][][] slopeEligible = new boolean[surface.width()][surface.height()][surface.depth()];
        int requiredCount = 0;

        for (int x = 0; x < surface.width(); x++) {
            for (int y = 0; y < surface.height(); y++) {
                for (int z = 0; z < surface.depth(); z++) {
                    if (!surface.isFilled(x, y, z)) {
                        continue;
                    }
                    required[x][y][z] = true;
                    requiredCount++;
                    slopeEligible[x][y][z] = isSlopeEligible(surface.getNormal(x, y, z), allowedSpecs);
                }
            }
        }
        return new PlacementTargetGrid(surface, featureGrid, required, slopeEligible, requiredCount);
    }

    public int width() { return surface.width(); }
    public int height() { return surface.height(); }
    public int depth() { return surface.depth(); }
    public int requiredCount() { return requiredCount; }

    public boolean isRequired(int x, int y, int z) {
        return inBounds(x, y, z) && requiredMask[x][y][z];
    }

    public boolean isSlopeEligible(int x, int y, int z) {
        return inBounds(x, y, z) && slopeEligibleMask[x][y][z];
    }

    public Vector3 normalAt(int x, int y, int z) {
        return surface.getNormal(x, y, z);
    }

    public double colorErrorForRegion(int x, int y, int z, int studX, int studY, int heightUnits) {
        if (featureGrid == null) {
            return 0.0;
        }
        double uniformity = featureGrid.computeRegionUniformity(x, y, z, studX, studY, heightUnits);
        return 1.0 - uniformity;
    }

    public VoxelGrid surface() {
        return surface;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width()
            && y >= 0 && y < height()
            && z >= 0 && z < depth();
    }

    private static boolean isSlopeEligible(Vector3 normal, List<BrickSpec> allowedSpecs) {
        if (normal == null || normal.length() < 1e-6) {
            return false;
        }
        double horizontalLen = Math.sqrt(normal.x() * normal.x() + normal.z() * normal.z());
        if (normal.y() < 0 && horizontalLen < 1e-3) {
            return false;
        }
        double cosAngle = Math.abs(normal.y());
        double inclination = Math.toDegrees(Math.acos(Math.min(1.0, cosAngle)));
        if (inclination < MIN_SLOPE_INCLINATION_DEG) {
            return false;
        }
        for (BrickSpec spec : allowedSpecs) {
            if (!spec.isSlope()) {
                continue;
            }
            if (SurfaceMatcher.match(normal, spec).eligible()) {
                return true;
            }
        }
        return false;
    }
}
