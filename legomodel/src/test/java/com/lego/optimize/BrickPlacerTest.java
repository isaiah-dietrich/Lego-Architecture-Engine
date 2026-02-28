package com.lego.optimize;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.voxel.VoxelGrid;

class BrickPlacerTest {

    @Test
    void testNullSurfaceThrows() {
        assertThrows(IllegalArgumentException.class, () -> BrickPlacer.placeBricks(null));
    }

    @Test
    void testEmptyGrid_ReturnsEmptyList() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        List<Brick> bricks = BrickPlacer.placeBricks(surface);
        assertTrue(bricks.isEmpty());
    }

    @Test
    void testSingleVoxel_Returns1x1Brick() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(2, 3, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(1, bricks.size());
        Brick brick = bricks.get(0);
        assertEquals(2, brick.x());
        assertEquals(3, brick.y());
        assertEquals(1, brick.z());
        assertEquals(1, brick.studX());
        assertEquals(1, brick.studY());
        assertEquals(1, brick.heightUnits());
    }

    @Test
    void testTwoAdjacentVoxelsHorizontal_Returns1x2Brick() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(1, 2, 0, true);
        surface.setFilled(2, 2, 0, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(1, bricks.size());
        Brick brick = bricks.get(0);
        assertEquals(1, brick.x());
        assertEquals(2, brick.y());
        assertEquals(0, brick.z());
        assertEquals(2, brick.studX());  // 1x2 horizontal
        assertEquals(1, brick.studY());
    }

    @Test
    void testTwoAdjacentVoxelsVertical_Returns1x2Brick() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(1, 2, 0, true);
        surface.setFilled(1, 3, 0, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(1, bricks.size());
        Brick brick = bricks.get(0);
        assertEquals(1, brick.x());
        assertEquals(2, brick.y());
        assertEquals(0, brick.z());
        assertEquals(1, brick.studX());  // 1x2 vertical
        assertEquals(2, brick.studY());
    }

    @Test
    void testThreeHorizontalVoxels_Returns1x2And1x1() {
        VoxelGrid surface = new VoxelGrid(5, 5, 5);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(2, 0, 0, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(2, bricks.size());
        
        // First brick should be 1x2 at (0, 0, 0)
        Brick brick1 = bricks.get(0);
        assertEquals(0, brick1.x());
        assertEquals(0, brick1.y());
        assertEquals(2, brick1.studX());
        assertEquals(1, brick1.studY());

        // Second brick should be 1x1 at (2, 0, 0)
        Brick brick2 = bricks.get(1);
        assertEquals(2, brick2.x());
        assertEquals(0, brick2.y());
        assertEquals(1, brick2.studX());
        assertEquals(1, brick2.studY());
    }

    @Test
    void testMixedPattern_IsDeterministic() {
        VoxelGrid surface = new VoxelGrid(4, 4, 2);
        
        // Layer 0: scattered pattern
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(0, 2, 0, true);
        surface.setFilled(1, 2, 0, true);
        surface.setFilled(2, 2, 0, true);
        
        // Layer 1: single voxel
        surface.setFilled(1, 1, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        // Verify deterministic placement
        assertEquals(4, bricks.size());
        
        // First brick: layer 0, y=0, x=0 -> 1x2 horizontal
        assertEquals(0, bricks.get(0).x());
        assertEquals(0, bricks.get(0).y());
        assertEquals(0, bricks.get(0).z());
        assertEquals(2, bricks.get(0).studX());
        
        // Second brick: layer 0, y=2, x=0 -> 1x2 horizontal
        assertEquals(0, bricks.get(1).x());
        assertEquals(2, bricks.get(1).y());
        assertEquals(0, bricks.get(1).z());
        assertEquals(2, bricks.get(1).studX());
        
        // Third brick: layer 0, y=2, x=2 -> 1x1
        assertEquals(2, bricks.get(2).x());
        assertEquals(2, bricks.get(2).y());
        assertEquals(0, bricks.get(2).z());
        assertEquals(1, bricks.get(2).studX());
        
        // Fourth brick: layer 1, y=1, x=1 -> 1x1
        assertEquals(1, bricks.get(3).x());
        assertEquals(1, bricks.get(3).y());
        assertEquals(1, bricks.get(3).z());
        assertEquals(1, bricks.get(3).studX());
    }

    @Test
    void testNoOverlaps() {
        VoxelGrid surface = new VoxelGrid(10, 10, 3);
        
        // Fill with a complex pattern
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                if ((x + y) % 2 == 0) {
                    surface.setFilled(x, y, 0, true);
                }
            }
        }

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        // Verify no overlaps
        for (int i = 0; i < bricks.size(); i++) {
            for (int j = i + 1; j < bricks.size(); j++) {
                assertFalse(bricks.get(i).overlaps(bricks.get(j)),
                    "Bricks " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void testFullCoverage() {
        VoxelGrid surface = new VoxelGrid(5, 5, 3);
        
        // Fill a pattern
        surface.setFilled(1, 1, 0, true);
        surface.setFilled(2, 1, 0, true);
        surface.setFilled(3, 1, 0, true);
        surface.setFilled(1, 2, 0, true);
        surface.setFilled(1, 3, 1, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        // Collect all covered voxels
        Set<String> covered = new HashSet<>();
        for (Brick brick : bricks) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    for (int z = brick.z(); z < brick.maxZ(); z++) {
                        String key = x + "," + y + "," + z;
                        assertFalse(covered.contains(key), 
                            "Voxel " + key + " covered twice");
                        covered.add(key);
                    }
                }
            }
        }

        // Verify all filled voxels are covered
        int filledCount = 0;
        for (int x = 0; x < surface.width(); x++) {
            for (int y = 0; y < surface.height(); y++) {
                for (int z = 0; z < surface.depth(); z++) {
                    if (surface.isFilled(x, y, z)) {
                        filledCount++;
                        String key = x + "," + y + "," + z;
                        assertTrue(covered.contains(key), 
                            "Voxel " + key + " not covered");
                    }
                }
            }
        }

        assertEquals(filledCount, covered.size());
    }

    @Test
    void testMultipleLayersProcessedInOrder() {
        VoxelGrid surface = new VoxelGrid(3, 3, 3);
        
        // Fill one voxel per layer
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(1, 1, 1, true);
        surface.setFilled(2, 2, 2, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(3, bricks.size());
        
        // Verify z-order
        assertEquals(0, bricks.get(0).z());
        assertEquals(1, bricks.get(1).z());
        assertEquals(2, bricks.get(2).z());
    }

    @Test
    void testEdgeCase_GridBoundary1x2() {
        VoxelGrid surface = new VoxelGrid(3, 3, 1);
        
        // Two voxels at right edge
        surface.setFilled(1, 0, 0, true);
        surface.setFilled(2, 0, 0, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(1, bricks.size());
        assertEquals(1, bricks.get(0).x());
        assertEquals(2, bricks.get(0).studX());
    }

    @Test
    void testEdgeCase_CannotPlace1x2AtBoundary() {
        VoxelGrid surface = new VoxelGrid(3, 3, 1);
        
        // Single voxel at edge (cannot extend)
        surface.setFilled(2, 0, 0, true);

        List<Brick> bricks = BrickPlacer.placeBricks(surface);

        assertEquals(1, bricks.size());
        assertEquals(2, bricks.get(0).x());
        assertEquals(1, bricks.get(0).studX());  // Falls back to 1x1
    }
}
