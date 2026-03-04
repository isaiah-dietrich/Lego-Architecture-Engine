package com.lego.voxel;

import java.util.Objects;

import com.lego.model.Mesh;

/**
 * Facade for voxelization strategy selection.
 *
 * <p>Default behavior remains the legacy ray-parity implementation to preserve
 * backward compatibility while new voxelizers are introduced incrementally.</p>
 */
public final class Voxelizer {

    private Voxelizer() {
        // Utility class, prevent instantiation
    }

    /**
     * Backward-compatible entry point that uses the legacy implementation.
     */
    public static VoxelGrid voxelize(Mesh mesh, int resolution) {
        return voxelize(mesh, resolution, VoxelizationStrategy.LEGACY);
    }

    /**
     * Strategy-aware voxelization entry point.
     *
     * @param mesh input mesh (must be non-null)
     * @param resolution voxel grid resolution (must be >= 2)
     * @param strategy voxelization strategy to use
     * @return voxelized grid
     */
    public static VoxelGrid voxelize(Mesh mesh, int resolution, VoxelizationStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        return switch (strategy) {
            case LEGACY -> LegacyVoxelizer.voxelize(mesh, resolution);
            case TOPOLOGICAL_SURFACE -> TopologicalVoxelizer.voxelizeSurface(mesh, resolution);
        };
    }
}
