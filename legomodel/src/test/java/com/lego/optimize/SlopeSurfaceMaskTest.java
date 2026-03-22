package com.lego.optimize;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.model.Facing;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.voxel.VoxelGrid;

class SlopeSurfaceMaskTest {

    @Test
    void extract_keepsOnlySlopeEligibleSurfaceVoxels() {
        VoxelGrid surface = new VoxelGrid(3, 1, 2);

        // Slope-eligible voxel (~45 degrees toward NORTH)
        surface.setFilled(0, 0, 0, true);
        surface.accumulateNormal(0, 0, 0, new Vector3(0f, 0.707f, -0.707f));

        // Flat voxel (not slope-eligible)
        surface.setFilled(1, 0, 0, true);
        surface.accumulateNormal(1, 0, 0, new Vector3(0f, 1f, 0f));

        // Filled voxel without normal data (not slope-eligible)
        surface.setFilled(2, 0, 0, true);

        surface.normalizeNormals();

        List<BrickSpec> specs = Arrays.asList(
            new BrickSpec(2, 2, 3, "Bricks Sloped", "3039", "Slope 45° 2x2", 45.0),
            new BrickSpec(1, 1, 1, "Bricks", "3005")
        );

        VoxelGrid slopeMask = SlopeSurfaceMask.extract(surface, specs);

        assertEquals(1, slopeMask.countFilledVoxels());
        assertTrue(slopeMask.isFilled(0, 0, 0));
        assertFalse(slopeMask.isFilled(1, 0, 0));
        assertFalse(slopeMask.isFilled(2, 0, 0));
    }

    @Test
    void extract_withNoSlopeSpecs_returnsEmptyMask() {
        VoxelGrid surface = new VoxelGrid(2, 1, 2);
        surface.setFilled(0, 0, 0, true);
        surface.accumulateNormal(0, 0, 0, new Vector3(0f, 0.707f, -0.707f));
        surface.normalizeNormals();

        List<BrickSpec> nonSlopeOnly = List.of(new BrickSpec(1, 1, 1, "Bricks", "3005"));
        VoxelGrid slopeMask = SlopeSurfaceMask.extract(surface, nonSlopeOnly);

        assertEquals(0, slopeMask.countFilledVoxels());
    }

    @Test
    void extractPlaced_keepsOnlySurfaceVoxelsCoveredByPlacedSlopeBricks() {
        VoxelGrid surface = new VoxelGrid(4, 2, 4);
        // Surface voxels at y=0
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                surface.setFilled(x, 0, z, true);
            }
        }
        // Surface voxel above, should not be marked by base-layer extraction
        surface.setFilled(1, 1, 1, true);

        List<Brick> bricks = Arrays.asList(
            new Brick(0, 0, 0, 2, 2, 3, "3039", Facing.NORTH), // slope
            new Brick(2, 0, 0, 1, 1, 1, "3005")                // non-slope
        );

        VoxelGrid placed = SlopeSurfaceMask.extractPlaced(surface, bricks);
        assertEquals(4, placed.countFilledVoxels());
        assertTrue(placed.isFilled(0, 0, 0));
        assertTrue(placed.isFilled(1, 0, 0));
        assertTrue(placed.isFilled(0, 0, 1));
        assertTrue(placed.isFilled(1, 0, 1));

        assertFalse(placed.isFilled(2, 0, 0), "Non-slope brick footprint should not be included");
        assertFalse(placed.isFilled(1, 1, 1), "Only slope base-layer surface voxels should be included");
    }
}
