package com.lego.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VoxelGridTest {

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new VoxelGrid(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new VoxelGrid(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VoxelGrid(1, 1, 0));
    }

    @Test
    void testSetAndGetInBounds() {
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        grid.setFilled(1, 1, 1, true);

        assertTrue(grid.isFilled(1, 1, 1));
        assertFalse(grid.isFilled(0, 0, 0));
    }

    @Test
    void testOutOfBoundsReadReturnsFalse() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);

        assertFalse(grid.isFilled(-1, 0, 0));
        assertFalse(grid.isFilled(0, -1, 0));
        assertFalse(grid.isFilled(0, 0, -1));
        assertFalse(grid.isFilled(2, 0, 0));
        assertFalse(grid.isFilled(0, 2, 0));
        assertFalse(grid.isFilled(0, 0, 2));
    }

    @Test
    void testOutOfBoundsWriteThrows() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);

        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(-1, 0, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(0, -1, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(0, 0, -1, true));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(2, 0, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(0, 2, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> grid.setFilled(0, 0, 2, true));
    }

    @Test
    void testFilledVoxelCount() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(1, 0, 0, true);
        grid.setFilled(1, 1, 1, true);

        assertEquals(3, grid.countFilledVoxels());
    }

    @Test
    void testNeighborLookupAtCenter() {
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        grid.setFilled(2, 1, 1, true); // +X
        grid.setFilled(0, 1, 1, true); // -X
        grid.setFilled(1, 2, 1, true); // +Y
        grid.setFilled(1, 0, 1, true); // -Y
        grid.setFilled(1, 1, 2, true); // +Z
        grid.setFilled(1, 1, 0, true); // -Z

        assertTrue(grid.isFilledPosX(1, 1, 1));
        assertTrue(grid.isFilledNegX(1, 1, 1));
        assertTrue(grid.isFilledPosY(1, 1, 1));
        assertTrue(grid.isFilledNegY(1, 1, 1));
        assertTrue(grid.isFilledPosZ(1, 1, 1));
        assertTrue(grid.isFilledNegZ(1, 1, 1));
    }

    @Test
    void testNeighborLookupAtBoundary() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);
        grid.setFilled(1, 0, 0, true);

        assertTrue(grid.isFilledPosX(0, 0, 0));
        assertFalse(grid.isFilledNegX(0, 0, 0));
        assertFalse(grid.isFilledNegY(0, 0, 0));
        assertFalse(grid.isFilledNegZ(0, 0, 0));
    }
}
