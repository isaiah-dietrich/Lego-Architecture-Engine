package com.lego.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SurfaceExtractorTest {

    @Test
    void testNullInputThrowsException() {
        assertThrows(NullPointerException.class, () -> SurfaceExtractor.extractSurface(null));
    }

    @Test
    void testSingleVoxelRemainsSurface() {
        VoxelGrid grid = new VoxelGrid(1, 1, 1);
        grid.setFilled(0, 0, 0, true);

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        assertEquals(1, surface.countFilledVoxels());
        assertTrue(surface.isFilled(0, 0, 0));
    }

    @Test
    void testFilled3x3x3RemovesCenterVoxel() {
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    grid.setFilled(x, y, z, true);
                }
            }
        }

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        assertEquals(26, surface.countFilledVoxels());
        assertTrue(surface.isFilled(0, 0, 0));
        assertTrue(surface.isFilled(2, 2, 2));
        assertEquals(false, surface.isFilled(1, 1, 1));
    }

    @Test
    void testEmptyGridStaysEmpty() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);
        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        assertEquals(0, surface.countFilledVoxels());
    }

    @Test
    void testDeterministicResult() {
        VoxelGrid grid = new VoxelGrid(2, 2, 2);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(1, 1, 1, true);

        int count1 = SurfaceExtractor.extractSurface(grid).countFilledVoxels();
        int count2 = SurfaceExtractor.extractSurface(grid).countFilledVoxels();

        assertEquals(count1, count2);
    }
}
