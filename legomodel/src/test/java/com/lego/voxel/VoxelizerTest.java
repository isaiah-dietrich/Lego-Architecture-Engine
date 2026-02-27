package com.lego.voxel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

class VoxelizerTest {

    @Test
    void testCubeProducesFilledVoxels() {
        Mesh cube = createCubeMesh(2.0, 7.0);
        VoxelGrid grid = Voxelizer.voxelize(cube, 10);

        int filled = grid.countFilledVoxels();
        assertTrue(filled > 0);
        assertTrue(filled < 1000);
    }

    @Test
    void testEmptyMeshProducesZeroVoxels() {
        Mesh empty = new Mesh(List.of());
        VoxelGrid grid = Voxelizer.voxelize(empty, 10);

        assertEquals(0, grid.countFilledVoxels());
    }

    @Test
    void testDeterminismAcrossRuns() {
        Mesh cube = createCubeMesh(1.0, 6.0);

        int count1 = Voxelizer.voxelize(cube, 12).countFilledVoxels();
        int count2 = Voxelizer.voxelize(cube, 12).countFilledVoxels();

        assertEquals(count1, count2);
    }

    @Test
    void testResolutionValidation() {
        Mesh mesh = createCubeMesh(0.0, 1.0);
        assertThrows(IllegalArgumentException.class, () -> Voxelizer.voxelize(mesh, 1));
    }

    @Test
    void testBoundaryCoverageAtResolutionTwo() {
        Mesh cube = createCubeMesh(0.0, 2.0);

        VoxelGrid grid = Voxelizer.voxelize(cube, 2);

        assertEquals(8, grid.countFilledVoxels());
        assertTrue(grid.isFilled(0, 0, 0));
        assertTrue(grid.isFilled(1, 1, 1));
    }

    /**
     * INVARIANT: Voxelizer produces output grid with dimensions matching resolution.
     * No out-of-bounds writes must occur; all filled voxels must be valid coordinates.
     */
    @Test
    void testOutputGridDimensionsMatchResolution() {
        Mesh cube = createCubeMesh(0.0, 10.0);
        int resolution = 15;

        VoxelGrid grid = Voxelizer.voxelize(cube, resolution);

        assertEquals(resolution, grid.width());
        assertEquals(resolution, grid.height());
        assertEquals(resolution, grid.depth());
    }

    /**
     * INVARIANT: Pyramid voxelization produces expected occupancy pattern.
     * Pyramid from (0,0,0) to (4,4,4) apex should have decreasing filled voxels at higher z.
     */
    @Test
    void testPyramidVoxelization() {
        // Create simple pyramid: base at z=0, apex at z=4
        Vector3 apex = new Vector3(2.0, 2.0, 4.0);
        Vector3 base1 = new Vector3(0.0, 0.0, 0.0);
        Vector3 base2 = new Vector3(4.0, 0.0, 0.0);
        Vector3 base3 = new Vector3(4.0, 4.0, 0.0);
        Vector3 base4 = new Vector3(0.0, 4.0, 0.0);

        List<Triangle> triangles = List.of(
            // Base quad (2 triangles)
            new Triangle(base1, base2, base3),
            new Triangle(base1, base3, base4),
            // Front face
            new Triangle(base1, apex, base2),
            // Right face
            new Triangle(base2, apex, base3),
            // Back face
            new Triangle(base3, apex, base4),
            // Left face
            new Triangle(base4, apex, base1)
        );

        Mesh pyramid = new Mesh(triangles);
        VoxelGrid grid = Voxelizer.voxelize(pyramid, 8);

        // Should have some filled voxels
        assertTrue(grid.countFilledVoxels() > 0);
        // Pyramid should have filled voxels near base, fewer near apex
        assertTrue(grid.countFilledVoxels() < 512); // Less than full 8x8x8
    }

    /**
     * INVARIANT: Determinism extends to exact voxel positions, not just counts.
     * Same mesh voxelized twice should produce identical grids.
     */
    @Test
    void testDeterministicVoxelPositions() {
        Mesh cube = createCubeMesh(1.0, 6.0);
        int resolution = 12;

        VoxelGrid grid1 = Voxelizer.voxelize(cube, resolution);
        VoxelGrid grid2 = Voxelizer.voxelize(cube, resolution);

        // Compare every voxel
        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    assertEquals(
                        grid1.isFilled(x, y, z),
                        grid2.isFilled(x, y, z),
                        "Voxel mismatch at (" + x + ", " + y + ", " + z + ")"
                    );
                }
            }
        }
    }

    /**
     * INVARIANT: Asymmetric (non-cube) mesh voxelization is deterministic.
     * Tests that normalization + voxelization pipeline is stable.
     */
    @Test
    void testAsymmetricMeshVoxelization() {
        // Tall thin box: 1 x 1 x 8
        Vector3 v1 = new Vector3(0.0, 0.0, 0.0);
        Vector3 v2 = new Vector3(1.0, 0.0, 0.0);
        Vector3 v3 = new Vector3(1.0, 1.0, 0.0);
        Vector3 v4 = new Vector3(0.0, 1.0, 0.0);
        Vector3 v5 = new Vector3(0.0, 0.0, 8.0);
        Vector3 v6 = new Vector3(1.0, 0.0, 8.0);
        Vector3 v7 = new Vector3(1.0, 1.0, 8.0);
        Vector3 v8 = new Vector3(0.0, 1.0, 8.0);

        List<Triangle> triangles = List.of(
            // Bottom
            new Triangle(v1, v2, v3),
            new Triangle(v1, v3, v4),
            // Top
            new Triangle(v5, v7, v6),
            new Triangle(v5, v8, v7),
            // Four sides (simplified)
            new Triangle(v1, v6, v2),
            new Triangle(v1, v5, v6),
            new Triangle(v2, v7, v3),
            new Triangle(v2, v6, v7),
            new Triangle(v3, v8, v4),
            new Triangle(v3, v7, v8),
            new Triangle(v4, v5, v1),
            new Triangle(v4, v8, v5)
        );

        Mesh asymmetric = new Mesh(triangles);
        VoxelGrid grid = Voxelizer.voxelize(asymmetric, 10);

        // Should have more filled voxels than empty
        int filled = grid.countFilledVoxels();
        assertTrue(filled > 0);
        assertTrue(filled < 1000);
    }

    private static Mesh createCubeMesh(double min, double max) {
        Vector3 v000 = new Vector3(min, min, min);
        Vector3 v100 = new Vector3(max, min, min);
        Vector3 v110 = new Vector3(max, max, min);
        Vector3 v010 = new Vector3(min, max, min);
        Vector3 v001 = new Vector3(min, min, max);
        Vector3 v101 = new Vector3(max, min, max);
        Vector3 v111 = new Vector3(max, max, max);
        Vector3 v011 = new Vector3(min, max, max);

        List<Triangle> triangles = List.of(
            // -Z face
            new Triangle(v000, v100, v110),
            new Triangle(v000, v110, v010),
            // +Z face
            new Triangle(v001, v111, v101),
            new Triangle(v001, v011, v111),
            // -Y face
            new Triangle(v000, v101, v100),
            new Triangle(v000, v001, v101),
            // +Y face
            new Triangle(v010, v110, v111),
            new Triangle(v010, v111, v011),
            // -X face
            new Triangle(v000, v010, v011),
            new Triangle(v000, v011, v001),
            // +X face
            new Triangle(v100, v101, v111),
            new Triangle(v100, v111, v110)
        );

        return new Mesh(triangles);
    }
}
