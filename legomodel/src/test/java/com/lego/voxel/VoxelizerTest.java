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
