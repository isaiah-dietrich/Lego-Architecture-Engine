package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

/**
 * Optional extension for placement policies that solve globally.
 */
public interface BatchPlacementPolicy extends PlacementPolicy {

    /**
     * Computes a full brick set for the given target surface.
     */
    List<Brick> placeAll(VoxelGrid surface, List<BrickSpec> allowedSpecs);
}
