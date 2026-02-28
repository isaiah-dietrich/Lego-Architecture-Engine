package com.lego.model;

/**
 * Immutable LEGO brick representation in voxel coordinates.
 *
 * A brick occupies a rectangular footprint of studX × studY studs,
 * starting at position (x, y, z) and extending heightUnits voxel layers upward.
 *
 * All fields are validated on construction to ensure a well-formed brick.
 */
public record Brick(int x, int y, int z, int studX, int studY, int heightUnits) {

    /**
     * Creates a brick with full validation.
     *
     * @param x           voxel x coordinate (>= 0)
     * @param y           voxel y coordinate (>= 0)
     * @param z           voxel z coordinate (>= 0)
     * @param studX       width in studs (> 0)
     * @param studY       depth in studs (> 0)
     * @param heightUnits height in voxel layers (> 0)
     * @throws IllegalArgumentException if any field is invalid
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
    }

    /**
     * Returns the maximum x coordinate occupied by this brick (exclusive).
     */
    public int maxX() {
        return x + studX;
    }

    /**
     * Returns the maximum y coordinate occupied by this brick (exclusive).
     */
    public int maxY() {
        return y + studY;
    }

    /**
     * Returns the maximum z coordinate occupied by this brick (exclusive).
     */
    public int maxZ() {
        return z + heightUnits;
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
