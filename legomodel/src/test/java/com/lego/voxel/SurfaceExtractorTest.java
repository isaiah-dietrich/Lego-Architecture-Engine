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

    /**
     * INVARIANT: Surface voxels are a subset of solid voxels.
     * Every surface voxel must exist in the input grid as filled.
     */
    @Test
    void testSurfaceIsSubsetOfSolid() {
        VoxelGrid grid = new VoxelGrid(5, 5, 5);
        // Fill a 3x3x3 block in the center
        for (int x = 1; x < 4; x++) {
            for (int y = 1; y < 4; y++) {
                for (int z = 1; z < 4; z++) {
                    grid.setFilled(x, y, z, true);
                }
            }
        }

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        // Every surface voxel must be filled in the original grid
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (surface.isFilled(x, y, z)) {
                        assertTrue(grid.isFilled(x, y, z),
                            "Surface voxel at (" + x + ", " + y + ", " + z +
                            ") not in solid grid");
                    }
                }
            }
        }
    }

    /**
     * INVARIANT: Fully internal voxels (all 6 neighbors filled) are excluded from surface.
     * Only voxels with at least one empty neighbor remain.
     */
    @Test
    void testInternalVoxelsExcluded() {
        VoxelGrid grid = new VoxelGrid(5, 5, 5);
        // Fill everything
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    grid.setFilled(x, y, z, true);
                }
            }
        }

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        // Center voxel (2,2,2) should be excluded (all neighbors filled)
        assertEquals(false, surface.isFilled(2, 2, 2));

        // All edge voxels should be kept (have empty neighbor outside)
        assertTrue(surface.isFilled(0, 0, 0));
        assertTrue(surface.isFilled(4, 4, 4));
        assertTrue(surface.isFilled(2, 2, 0)); // Top edge
        assertTrue(surface.isFilled(2, 2, 4)); // Bottom edge
    }

    /**
     * INVARIANT: Every surface voxel has at least one empty or out-of-bounds neighbor.
     * Validates that surface extraction correctly identifies boundary voxels.
     */
    @Test
    void testAllSurfaceVoxelsHaveEmptyNeighbor() {
        VoxelGrid grid = new VoxelGrid(4, 4, 4);
        // Fill a 2x2x2 block
        for (int x = 1; x < 3; x++) {
            for (int y = 1; y < 3; y++) {
                for (int z = 1; z < 3; z++) {
                    grid.setFilled(x, y, z, true);
                }
            }
        }

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        // Check every surface voxel has at least one empty neighbor
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    if (surface.isFilled(x, y, z)) {
                        boolean hasEmptyNeighbor =
                            !grid.isFilledPosX(x, y, z) ||
                            !grid.isFilledNegX(x, y, z) ||
                            !grid.isFilledPosY(x, y, z) ||
                            !grid.isFilledNegY(x, y, z) ||
                            !grid.isFilledPosZ(x, y, z) ||
                            !grid.isFilledNegZ(x, y, z);

                        assertTrue(hasEmptyNeighbor,
                            "Surface voxel at (" + x + ", " + y + ", " + z +
                            ") has no empty neighbor");
                    }
                }
            }
        }
    }

    /**
     * INVARIANT: Larger solid grids correctly extract surface.
     * Tests 4x4x4 fully filled grid: internal voxels are (1,1,1) to (2,2,2), count = 8.
     * Surface voxels = 64 total - 8 internal = 56.
     */
    @Test
    void testLargeSolidGridSurfaceExtraction() {
        VoxelGrid grid = new VoxelGrid(4, 4, 4);
        // Fill all voxels
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    grid.setFilled(x, y, z, true);
                }
            }
        }

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        // 4x4x4 fully filled: internal voxels at x,y,z in [1,2] (2x2x2 = 8)
        // Surface = 64 - 8 = 56
        assertEquals(56, surface.countFilledVoxels());
    }

    /**
     * INVARIANT: Scattered voxels (no adjacent filled voxels) remain surface.
     * Isolated voxels have all 6 neighbors empty, so they are kept.
     */
    @Test
    void testScatteredVoxelsRemainSurface() {
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(2, 2, 2, true);
        grid.setFilled(1, 1, 1, true);

        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        // All three isolated voxels should remain
        assertEquals(3, surface.countFilledVoxels());
        assertTrue(surface.isFilled(0, 0, 0));
        assertTrue(surface.isFilled(2, 2, 2));
        assertTrue(surface.isFilled(1, 1, 1));
    }
}
