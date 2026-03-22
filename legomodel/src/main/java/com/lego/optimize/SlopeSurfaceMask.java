package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Builds a voxel mask of slope-eligible surface cells before brick placement.
 */
public final class SlopeSurfaceMask {

    /** Non-instantiable utility class. */
    private SlopeSurfaceMask() {}

    /**
     * Extracts slope-eligible voxels from a surface grid using the same
     * normal/spec matching logic as placement.
     *
     * @param surface      source surface voxels
     * @param allowedSpecs allowed brick specs from the catalog
     * @return a new voxel grid with only slope-eligible surface cells filled
     */
    public static VoxelGrid extract(VoxelGrid surface, List<BrickSpec> allowedSpecs) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (allowedSpecs == null) {
            throw new IllegalArgumentException("allowedSpecs must not be null");
        }

        VoxelGrid slopeSurface = new VoxelGrid(surface.width(), surface.height(), surface.depth());

        for (int x = 0; x < surface.width(); x++) {
            for (int y = 0; y < surface.height(); y++) {
                for (int z = 0; z < surface.depth(); z++) {
                    if (!surface.isFilled(x, y, z)) {
                        continue;
                    }
                    Vector3 normal = surface.getNormal(x, y, z);
                    if (isSlopeEligible(normal, allowedSpecs)) {
                        slopeSurface.setFilled(x, y, z, true);
                    }
                }
            }
        }

        return slopeSurface;
    }

    /**
     * Extracts voxels on the surface layer that are actually occupied by placed
     * slope bricks (directional bricks with facing != NONE).
     *
     * This captures the real slope anchors used by the placer, not just
     * slope-eligible candidates from normal matching.
     *
     * @param surface source surface voxels
     * @param bricks placed brick list
     * @return a new voxel grid with only actually-placed slope surface voxels filled
     */
    public static VoxelGrid extractPlaced(VoxelGrid surface, List<Brick> bricks) {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (bricks == null) {
            throw new IllegalArgumentException("bricks must not be null");
        }

        VoxelGrid placedSlopeSurface = new VoxelGrid(surface.width(), surface.height(), surface.depth());

        for (Brick brick : bricks) {
            if (brick == null || brick.facing() == Facing.NONE) {
                continue;
            }

            int y = brick.y();
            if (y < 0 || y >= surface.height()) {
                continue;
            }

            int minX = Math.max(0, brick.x());
            int maxX = Math.min(surface.width(), brick.maxX());
            int minZ = Math.max(0, brick.z());
            int maxZ = Math.min(surface.depth(), brick.maxZ());

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    if (surface.isFilled(x, y, z)) {
                        placedSlopeSurface.setFilled(x, y, z, true);
                    }
                }
            }
        }

        return placedSlopeSurface;
    }

    private static boolean isSlopeEligible(Vector3 normal, List<BrickSpec> allowedSpecs) {
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
