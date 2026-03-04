package com.lego.voxel;

import com.lego.model.Mesh;

/**
 * Placeholder for upcoming topological surface voxelization implementation.
 *
 * <p>This class exists to preserve migration history and provide a stable
 * strategy boundary before algorithm rollout.</p>
 */
public final class TopologicalVoxelizer {

    private TopologicalVoxelizer() {
        // Utility class
    }

    /**
     * Planned topological surface voxelizer entry point.
     *
     * @throws UnsupportedOperationException until implementation lands
     */
    public static VoxelGrid voxelizeSurface(Mesh mesh, int resolution) {
        throw new UnsupportedOperationException(
            "Topological voxelizer is not implemented yet. Use voxelizer mode 'legacy'."
        );
    }
}
