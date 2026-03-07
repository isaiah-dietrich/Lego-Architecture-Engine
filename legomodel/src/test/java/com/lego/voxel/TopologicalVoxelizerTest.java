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
 * - Anisotropic voxel size handling
 * - SAT-based hole-free detection of diagonal triangles
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

        assertEquals(grid1.countFilledVoxels(), grid2.countFilledVoxels(),
            "Deterministic runs should produce identical voxel counts");
    }

    @Test
    void testAnisotropicVoxelSizes() {
        int resolution = 10;
        Mesh isoCube = createScaledCube(resolution);
        Mesh anisoCompatibleBox = createScaledBox(10.0, 5.0, 10.0);

        TopologicalVoxelizerConfig isoConfig = new TopologicalVoxelizerConfig(1.0, 1.0, 1.0, 1e-9);
        VoxelGrid isoGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(isoCube, resolution, isoConfig);

        TopologicalVoxelizerConfig anisoConfig = new TopologicalVoxelizerConfig(2.0, 0.5, 1.0, 1e-9);
        VoxelGrid anisoGrid = TopologicalVoxelizer.voxelizeSurfaceWithConfig(
            anisoCompatibleBox, resolution, anisoConfig
        );

        assertNotNull(isoGrid);
        assertNotNull(anisoGrid);
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
            new TopologicalVoxelizerConfig(-1.0, 1.0, 1.0, 1e-9);
            assertTrue(false, "Should reject negative voxel size");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    void testConfigValidationRejectsNegativeEpsilon() {
        try {
            new TopologicalVoxelizerConfig(1.0, 1.0, 1.0, -1e-9);
            assertTrue(false, "Should reject negative epsilon");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Epsilon"));
        }
    }

    /**
     * A triangle that passes through the corner of a voxel without intersecting
     * any of its 3 center crosshair lines would have been silently missed by the
     * old segment-intersection approach. SAT must detect it.
     *
     * Triangle at the (0,0,0) corner of the first voxel with all three vertices
     * inside [1.0, 1.2]^3. The voxel center crosshair lines pass through (1.5, 1.5, 1.5),
     * which none of the triangle's vertices or edges can reach at max coord 1.2.
     */
    @Test
    void testCornerTriangleCapturedBySat() {
        Triangle corner = new Triangle(
            new Vector3(1.0, 1.0, 1.2),
            new Vector3(1.2, 1.0, 1.0),
            new Vector3(1.0, 1.2, 1.0)
        );
        Mesh mesh = new Mesh(List.of(corner));

        VoxelGrid grid = TopologicalVoxelizer.voxelizeSurface(mesh, 5);

        assertTrue(grid.isFilled(0, 0, 0),
            "Triangle at voxel corner must be detected by SAT even though it misses all center crosshair lines");
    }

    private Mesh createScaledCube(int resolution) {
        return createScaledBox(resolution, resolution, resolution);
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
            new Triangle(vertices[0], vertices[2], vertices[1]),
            new Triangle(vertices[0], vertices[3], vertices[2]),
            new Triangle(vertices[7], vertices[6], vertices[5]),
            new Triangle(vertices[7], vertices[5], vertices[4]),
            new Triangle(vertices[0], vertices[1], vertices[6]),
            new Triangle(vertices[0], vertices[6], vertices[7]),
            new Triangle(vertices[3], vertices[5], vertices[2]),
            new Triangle(vertices[3], vertices[4], vertices[5]),
            new Triangle(vertices[0], vertices[7], vertices[4]),
            new Triangle(vertices[0], vertices[4], vertices[3]),
            new Triangle(vertices[1], vertices[2], vertices[5]),
            new Triangle(vertices[1], vertices[5], vertices[6])
        );

        return new Mesh(triangles);
    }

    private Mesh createScaledPyramid(int resolution) {
        double scale = resolution;
        Vector3[] vertices = {
            new Vector3(0, 0, 0),
            new Vector3(scale, 0, 0),
            new Vector3(scale / 2, scale, 0),
            new Vector3(scale / 2, scale / 2, scale)
        };

        List<Triangle> triangles = List.of(
            new Triangle(vertices[0], vertices[1], vertices[2]),
            new Triangle(vertices[0], vertices[3], vertices[1]),
            new Triangle(vertices[1], vertices[3], vertices[2]),
            new Triangle(vertices[2], vertices[3], vertices[0])
        );

        return new Mesh(triangles);
    }

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
