package com.lego.model;

/**
 * Bit-packed (x, y, z) coordinate key for voxel grid lookups.
 * Supports coordinates up to 2,097,151 (21 bits per component).
 */
public final class VoxelKey {

    private static final int BITS = 21;
    private static final long MASK = (1L << BITS) - 1;

    private VoxelKey() {}

    public static long pack(int x, int y, int z) {
        return ((long) x << (2 * BITS)) | ((long) (y & MASK) << BITS) | (z & MASK);
    }

    public static int unpackX(long key) { return (int) (key >>> (2 * BITS)); }
    public static int unpackY(long key) { return (int) ((key >>> BITS) & MASK); }
    public static int unpackZ(long key) { return (int) (key & MASK); }
}
