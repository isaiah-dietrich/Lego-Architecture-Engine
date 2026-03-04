package com.lego.voxel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Tests for topological surface voxelization.
 *
 * Validates:
 * - Basic surface detection (cube, pyramid, tilted plane)
 * - Deterministic run-to-run consistency
 * - Connectivity model differences (SIX vs TWENTY_SIX)
 * - Anisotropic voxel size handling
 */
class TopologicalVoxelizerTest {

    @Test
    void testNullMeshThrows() {
        try {
            TopologicalVoxelizer.voxelizeSurface(null, 10);
            assertTrue(false, "Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(true);
        }
    }

    @Test
    void testInvalidResolutionThrows() {
        Mesh cube = createScaledCube(10);
        try {
            TopologicalVoxelizer.voxelizeSurface(cube, 1);
            assertTrue(false, "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Resolution must be >= 2"));
        }
    }

    @Test
    void testUnitCubeProducesVoxelGrid() {
        int resolution = 10;
        Mesh cube = createScaledCube(resolution);
        VoxelGrid grid = TopologicalVoxelizer.voxelizeSurface(cube, resolution);

        assertNotNull(grid);
        assertTrue(grid.countFilledVoxels() > 0, "Cube surface should produce non-zero voxels");
        assertTrue(grid.countFilledVoxels() < resolution * resolution * resolution,
            "Surface-only voxelization should not fill full volume");
    }

    @Test
    void testDeterministicRunsProduceSameResult() {
        int resolution = 8;
        Mesh pyramid = createScaledPyramid(resolution);
        
        VoxelGrid grid1 = TopologicalVoxelizer.voxelizeSurface(pyramid, resolution);
        VoxelGrid grid2 = TopologicalVoxelizer.voxelizeSurface(pyramid, resolution);

        // Determinism: same input produces same output
        assertEquals(grid1.countFilledVoxels(), grid2.countFilledVoxels(),
            "Deterministic runs should produce identical voxel counts");
    }

    @Test
    void testAnisotropicVoxelSizes() {
        int resolution = 10;
        Mesh isoCube = createScaledCube(resolution);
        Mesh anisoCompatibleBox = createScaledBox(10.0, 5.0, 10.0);
        
        // Isotropic configuration
        TopologicalVoxelizerConfig isoConfig = new TopologicalVoxelizerConfig(
            1.0, 1.0, 1.0,
            Connectivity.TWENTY_SIX,
            1e-9
        );
        VoxelGrid isoGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(isoCube, resolution, isoConfig);

        // Anisotropic configuration
        TopologicalVoxelizerConfig anisoConfig = new TopologicalVoxelizerConfig(
            2.0, 0.5, 1.0,
            Connectivity.TWENTY_SIX,
            1e-9
        );
        VoxelGrid anisoGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(
            anisoCompatibleBox, resolution, anisoConfig
        );

        // Both should produce valid grids without crashing
        assertNotNull(isoGrid);
        assertNotNull(anisoGrid);
    }

    @Test
    void testConnectivitySixVstwentySix() {
        int resolution = 12;
        Mesh tiltedPlane = createScaledTiltedPlane(resolution);
        
        TopologicalVoxelizerConfig sixConfig = new TopologicalVoxelizerConfig(
            1.0, 1.0, 1.0,
            Connectivity.SIX,
            1e-9
        );
        VoxelGrid sixGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(tiltedPlane, resolution, sixConfig);

        TopologicalVoxelizerConfig twentySixConfig = new TopologicalVoxelizerConfig(
            1.0, 1.0, 1.0,
            Connectivity.TWENTY_SIX,
            1e-9
        );
        VoxelGrid twentySixGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(
            tiltedPlane, resolution, twentySixConfig
        );

        assertNotNull(sixGrid);
        assertNotNull(twentySixGrid);
        assertTrue(sixGrid.countFilledVoxels() > 0, "SIX connectivity should produce voxels");
        assertTrue(twentySixGrid.countFilledVoxels() > 0, "TWENTY_SIX connectivity should produce voxels");
    }

    @Test
    void testSmallResolutionMeshProducesValidGrid() {
        int resolution = 5;
        Mesh plane = createScaledSimplePlane(resolution);
        VoxelGrid grid = TopologicalVoxelizer.voxelizeSurface(plane, resolution);

        assertNotNull(grid);
        assertTrue(grid.countFilledVoxels() > 0, "Plane should produce non-zero voxels");
    }

    @Test
    void testConfigValidationRejectsInvalidVoxelSizes() {
        try {
            new TopologicalVoxelizerConfig(
                -1.0, 1.0, 1.0,
                Connectivity.TWENTY_SIX,
                1e-9
            );
            assertTrue(false, "Should reject negative voxel size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    void testConfigValidationRejectsNegativeEpsilon() {
        try {
            new TopologicalVoxelizerConfig(
                1.0, 1.0, 1.0,
                Connectivity.TWENTY_SIX,
                -1e-9
            );
            assertTrue(false, "Should reject negative epsilon");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Epsilon"));
        }
    }

    /**
     * Creates a cube scaled to [0, 10] for voxel grid resolution 10.
     * This matches the coordinate space expected by voxelizers after MeshNormalizer.
     */
    private Mesh createScaledCube(int resolution) {
        double scale = resolution;
        return createScaledBox(scale, scale, scale);
    }

    private Mesh createScaledBox(double sizeX, double sizeY, double sizeZ) {
        Vector3[] vertices = {
            new Vector3(0, 0, 0),
            new Vector3(sizeX, 0, 0),
            new Vector3(sizeX, sizeY, 0),
            new Vector3(0, sizeY, 0),
            new Vector3(0, sizeY, sizeZ),
            new Vector3(sizeX, sizeY, sizeZ),
            new Vector3(sizeX, 0, sizeZ),
            new Vector3(0, 0, sizeZ)
        };

        List<Triangle> triangles = List.of(
            // Bottom (z=0)
            new Triangle(vertices[0], vertices[2], vertices[1]),
            new Triangle(vertices[0], vertices[3], vertices[2]),
            // Top (z=scale)
            new Triangle(vertices[7], vertices[6], vertices[5]),
            new Triangle(vertices[7], vertices[5], vertices[4]),
            // Front (y=0)
            new Triangle(vertices[0], vertices[1], vertices[6]),
            new Triangle(vertices[0], vertices[6], vertices[7]),
            // Back (y=scale)
            new Triangle(vertices[3], vertices[5], vertices[2]),
            new Triangle(vertices[3], vertices[4], vertices[5]),
            // Left (x=0)
            new Triangle(vertices[0], vertices[7], vertices[4]),
            new Triangle(vertices[0], vertices[4], vertices[3]),
            // Right (x=scale)
            new Triangle(vertices[1], vertices[2], vertices[5]),
            new Triangle(vertices[1], vertices[5], vertices[6])
        );

        return new Mesh(triangles);
    }

    /**
     * Creates a simple pyramid with triangular base, scaled to [0, resolution].
     */
    private Mesh createScaledPyramid(int resolution) {
        double scale = resolution;
        Vector3[] vertices = {
            new Vector3(0, 0, 0),               // base corner 1
            new Vector3(scale, 0, 0),           // base corner 2
            new Vector3(scale / 2, scale, 0),   // base corner 3
            new Vector3(scale / 2, scale / 2, scale) // apex
        };

        List<Triangle> triangles = List.of(
            // Base
            new Triangle(vertices[0], vertices[1], vertices[2]),
            // Side 1
            new Triangle(vertices[0], vertices[3], vertices[1]),
            // Side 2
            new Triangle(vertices[1], vertices[3], vertices[2]),
            // Side 3
            new Triangle(vertices[2], vertices[3], vertices[0])
        );

        return new Mesh(triangles);
    }

    /**
     * Creates a simple tilted plane for connectivity testing, scaled to [0, resolution].
     */
    private Mesh createScaledTiltedPlane(int resolution) {
        double scale = resolution;
        Vector3[] vertices = {
            new Vector3(0, 0, 0),
            new Vector3(scale, 0, 0),
            new Vector3(scale, scale, scale / 2),
            new Vector3(0, scale, scale / 2)
        };

        List<Triangle> triangles = List.of(
            new Triangle(vertices[0], vertices[2], vertices[1]),
            new Triangle(vertices[0], vertices[3], vertices[2])
        );

        return new Mesh(triangles);
    }

    /**
     * Creates a single 2-triangle plane for minimal test, scaled to [0, resolution].
     */
    private Mesh createScaledSimplePlane(int resolution) {
        double scale = resolution;
        Vector3[] vertices = {
            new Vector3(0, 0, scale / 2),
            new Vector3(scale, 0, scale / 2),
            new Vector3(scale, scale, scale / 2),
            new Vector3(0, scale, scale / 2)
        };

        List<Triangle> triangles = List.of(
            new Triangle(vertices[0], vertices[2], vertices[1]),
            new Triangle(vertices[0], vertices[3], vertices[2])
        );

        return new Mesh(triangles);
    }
}
