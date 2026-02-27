package com.lego.voxel;

/**
 * 3D voxel grid container.
 */
public final class VoxelGrid {
    /**
     * True - Voxel is filled (Inside the mesh)
     * False - Voxel is empty (Outside the emsh)
     */
    private final boolean[][][] grid;
    private final int width;
    private final int height;
    private final int depth;

    /**
     * Creates a voxel grid with the given dimensions.
     *
     * @param width  x dimension (> 0)
     * @param height y dimension (> 0)
     * @param depth  z dimension (> 0)
     */
    public VoxelGrid(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException(
                "All dimensions must be > 0: width=" + width +
                ", height=" + height + ", depth=" + depth
            );
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.grid = new boolean[width][height][depth];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /**
     * Returns whether a voxel is filled. Out-of-bounds returns false.
     */
    public boolean isFilled(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return false;
        }
        return grid[x][y][z];
    }

    /**
     * Sets a voxel as filled or empty. Out-of-bounds throws.
     */
    public void setFilled(int x, int y, int z, boolean filled) {
        if (!inBounds(x, y, z)) {
            throw new IndexOutOfBoundsException(
                "Voxel coordinates out of bounds: x=" + x +
                ", y=" + y + ", z=" + z
            );
        }
        grid[x][y][z] = filled;
    }

    /**
     * Counts all filled voxels in the grid.
     */
    public int countFilledVoxels() {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    if (grid[x][y][z]) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public boolean isFilledPosX(int x, int y, int z) {
        return isFilled(x + 1, y, z);
    }

    public boolean isFilledNegX(int x, int y, int z) {
        return isFilled(x - 1, y, z);
    }

    public boolean isFilledPosY(int x, int y, int z) {
        return isFilled(x, y + 1, z);
    }

    public boolean isFilledNegY(int x, int y, int z) {
        return isFilled(x, y - 1, z);
    }

    public boolean isFilledPosZ(int x, int y, int z) {
        return isFilled(x, y, z + 1);
    }

    public boolean isFilledNegZ(int x, int y, int z) {
        return isFilled(x, y, z - 1);
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width &&
               y >= 0 && y < height &&
               z >= 0 && z < depth;
    }
}
