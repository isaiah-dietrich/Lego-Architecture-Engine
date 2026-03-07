package com.lego.voxel;

/**
 * Configuration for topological surface voxelization.
 *
 * <p>Anisotropic voxel sizes are supported to allow non-cubic voxels
 * when normalized meshes have anisotropic scaling applied.</p>
 */
public final class TopologicalVoxelizerConfig {
    private final double voxelSizeX;
    private final double voxelSizeY;
    private final double voxelSizeZ;
    private final double epsilon;

    /**
     * Creates a configuration with isotropic voxel sizes (cubic voxels).
     *
     * @param voxelSize size of each voxel edge
     * @param epsilon   tolerance (reserved for future use)
     */
    public TopologicalVoxelizerConfig(double voxelSize, double epsilon) {
        this(voxelSize, voxelSize, voxelSize, epsilon);
    }

    /**
     * Creates a configuration with anisotropic voxel sizes.
     *
     * @param voxelSizeX X dimension voxel size
     * @param voxelSizeY Y dimension voxel size
     * @param voxelSizeZ Z dimension voxel size
     * @param epsilon    tolerance (reserved for future use)
     */
    public TopologicalVoxelizerConfig(
        double voxelSizeX,
        double voxelSizeY,
        double voxelSizeZ,
        double epsilon
    ) {
        if (voxelSizeX <= 0 || voxelSizeY <= 0 || voxelSizeZ <= 0) {
            throw new IllegalArgumentException("Voxel sizes must be positive");
        }
        if (epsilon < 0) {
            throw new IllegalArgumentException("Epsilon must be non-negative");
        }

        this.voxelSizeX = voxelSizeX;
        this.voxelSizeY = voxelSizeY;
        this.voxelSizeZ = voxelSizeZ;
        this.epsilon = epsilon;
    }

    public double voxelSizeX() { return voxelSizeX; }
    public double voxelSizeY() { return voxelSizeY; }
    public double voxelSizeZ() { return voxelSizeZ; }
    public double epsilon()    { return epsilon; }
}
