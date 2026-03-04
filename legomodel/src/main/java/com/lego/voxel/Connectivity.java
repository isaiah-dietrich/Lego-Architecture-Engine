package com.lego.voxel;

/**
 * Defines the connectivity model for topological surface voxelization.
 *
 * <p>Surface voxels are determined by segment-triangle intersection:
 * voxels whose connectivity segments intersect the mesh surface are marked
 * as surface voxels.</p>
 *
 * <p>Performance note: SIX is more selective and typically produces thinner
 * shells; TWENTY_SIX is more aggressive and captures more surface detail.</p>
 */
public enum Connectivity {
    /**
     * 6-connectivity: 6 segments connecting voxel center to face centers.
     * Axes: ±X, ±Y, ±Z from center to midpoint of each face.
     * Segments: (0,0,0) → (±0.5, 0, 0), (0, ±0.5, 0), (0, 0, ±0.5)
     */
    SIX("six"),

    /**
     * 26-connectivity: 26 segments connecting voxel center to all neighbors.
     * Includes face, edge, and corner neighbors.
     * More comprehensive surface coverage than 6-connectivity.
     */
    TWENTY_SIX("twenty_six");

    private final String cliValue;

    Connectivity(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }

    public static Connectivity fromCliValue(String value) {
        for (Connectivity c : values()) {
            if (c.cliValue.equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException(
            "connectivity must be 'six' or 'twenty_six', got: " + value
        );
    }
}
