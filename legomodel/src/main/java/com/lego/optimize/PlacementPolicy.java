package com.lego.optimize;

import java.util.List;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.Dimension;
import com.lego.voxel.VoxelGrid;

/**
 * Strategy for selecting which brick dimension to place at a given voxel position.
 *
 * <p>Implementations decide the trade-off between fewer bricks (larger pieces)
 * and surface accuracy (not overhanging into empty space).</p>
 */
public interface PlacementPolicy {

    /**
     * Selects the brick to place at position (x, y, z).
     *
     * @param surface           filled surface voxel grid
     * @param covered           3D array tracking already-covered voxels
     * @param x                 voxel x coordinate
     * @param y                 voxel y coordinate (height layer)
     * @param z                 voxel z coordinate
     * @param allowedDimensions dimensions in catalog priority order
     * @return the brick to place (never null)
     * @throws IllegalStateException if no dimension fits (missing 1x1 fallback)
     */
    Brick selectBrick(VoxelGrid surface, boolean[][][] covered,
                       int x, int y, int z, List<Dimension> allowedDimensions);

    /** Human-readable name for CLI output. */
    String name();
}
