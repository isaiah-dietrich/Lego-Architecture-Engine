package com.lego.model;

/**
 * Immutable LEGO brick representation in voxel coordinates.
 *
 * A brick occupies a rectangular footprint of studX × studY studs,
 * starting at position (x, y, z) and extending heightUnits voxel layers upward.
 *
 * All fields are validated on construction to ensure a well-formed brick.
 *
 * @param x           voxel x coordinate (>= 0)
 * @param y           voxel y coordinate (>= 0)
 * @param z           voxel z coordinate (>= 0)
 * @param studX       width in studs (> 0)
 * @param studY       depth in studs (> 0)
 * @param heightUnits height in LDraw-relative units (> 0; bricks=3, plates=1)
 * @param partId      catalog part identifier (e.g. "3001"), or "unknown" for test/legacy bricks
 */
public record Brick(int x, int y, int z, int studX, int studY, int heightUnits, String partId) {

    /** Default part ID for bricks created without catalog context. */
    public static final String UNKNOWN_PART_ID = "unknown";

    /**
     * Convenience constructor without partId (uses "unknown").
     */
    public Brick(int x, int y, int z, int studX, int studY, int heightUnits) {
        this(x, y, z, studX, studY, heightUnits, UNKNOWN_PART_ID);
    }

    /**
     * Creates a brick with full validation.
     */
    public Brick {
        if (x < 0) {
            throw new IllegalArgumentException("x must be >= 0, got: " + x);
        }
        if (y < 0) {
            throw new IllegalArgumentException("y must be >= 0, got: " + y);
        }
        if (z < 0) {
            throw new IllegalArgumentException("z must be >= 0, got: " + z);
        }
        if (studX <= 0) {
            throw new IllegalArgumentException("studX must be > 0, got: " + studX);
        }
        if (studY <= 0) {
            throw new IllegalArgumentException("studY must be > 0, got: " + studY);
        }
        if (heightUnits <= 0) {
            throw new IllegalArgumentException("heightUnits must be > 0, got: " + heightUnits);
        }
        if (partId == null || partId.isBlank()) {
            throw new IllegalArgumentException("partId must not be blank");
        }
    }

    /**
     * Returns the maximum x coordinate occupied by this brick (exclusive).
     */
    public int maxX() {
        return x + studX;
    }

    /**
     * Returns the maximum y coordinate occupied by this brick (exclusive).
     * A brick with heightUnits=3 occupies 3 plate-height voxel layers;
     * a plate with heightUnits=1 occupies 1 layer.
     */
    public int maxY() {
        return y + heightUnits;
    }

    /**
     * Returns the maximum z coordinate occupied by this brick (exclusive).
     * Z is the depth axis (VoxelGrid Z = OBJ front-back), so the Z extent is studY.
     */
    public int maxZ() {
        return z + studY;
    }

    /**
     * Checks if this brick overlaps with another brick in voxel space.
     *
     * @param other the other brick
     * @return true if bricks overlap, false otherwise
     */
    public boolean overlaps(Brick other) {
        return !(maxX() <= other.x || x >= other.maxX() ||
                 maxY() <= other.y || y >= other.maxY() ||
                 maxZ() <= other.z || z >= other.maxZ());
    }
}
