package com.lego.voxel;

/**
 * Supported voxelization strategies.
 */
public enum VoxelizationStrategy {
    LEGACY("legacy"),
    TOPOLOGICAL_SURFACE("topological");

    private final String cliValue;

    VoxelizationStrategy(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }

    public static VoxelizationStrategy fromCliValue(String value) {
        for (VoxelizationStrategy strategy : values()) {
            if (strategy.cliValue.equalsIgnoreCase(value)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException(
            "voxelizer mode must be 'legacy' or 'topological'."
        );
    }
}
