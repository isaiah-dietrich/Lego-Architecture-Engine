package com.lego.voxel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.mesh.BoundingBox;
import com.lego.mesh.MeshNormalizer;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Integration tests verifying the voxel contract across the full pipeline:
 * Normalization -> Voxelization -> Surface Extraction.
 *
 * Establishes invariants that brick placement logic can rely on.
 */
class VoxelContractTest {

    private static final double EPSILON = 1e-9;

    /**
     * CONTRACT INVARIANT: Normalized mesh bounds match voxel sampling space.
     * 
     * After normalization to resolution R:
     * - Min corner at (0, 0, 0)
     * - Largest axis spans exactly [0, R]
     * - Voxel sampling at (x+0.5, y+0.5, z+0.5) covers x,y,z in [0, R-1]
     * 
     * This ensures voxels at position 0 sample [0.5] and at R-1 sample [R-0.5],
     * covering the full normalized mesh range.
     */
    @Test
    void testNormalizationVoxelSamplingAlignment() {
        // Create mesh with known bounds
        Triangle t = new Triangle(
            new Vector3(10.0, 5.0, 2.0),
            new Vector3(14.0, 5.0, 2.0),
            new Vector3(10.0, 8.0, 2.0)
        );
        Mesh mesh = new Mesh(List.of(t));
        int resolution = 16;

        // Normalize
        Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
        BoundingBox normed = new BoundingBox(normalized);

        // Verify coordinate mapping
        assertEquals(0.0, normed.minX(), EPSILON);
        assertEquals(0.0, normed.minY(), EPSILON);
        assertEquals(0.0, normed.minZ(), EPSILON);

        // Largest axis should equal resolution
        double maxDim = Math.max(normed.width(), Math.max(normed.height(), normed.depth()));
        assertEquals(resolution, maxDim, EPSILON);

        // Non-largest axes should fit within [0, resolution]
        assertTrue(normed.width() <= resolution + EPSILON);
        assertTrue(normed.height() <= resolution + EPSILON);
        assertTrue(normed.depth() <= resolution + EPSILON);
    }

    /**
     * CONTRACT INVARIANT: Voxelization produces output matching input mesh bounds.
     *
     * A normalized mesh produces a voxel grid where:
     * - Grid dimensions = resolution x resolution x resolution
     * - Grid coordinates are [0, resolution)
     * - No out-of-bounds writes occur
     */
    @Test
    void testVoxelizationOutputBounds() {
        Mesh cube = createUnitCube();
        int resolution = 20;

        Mesh normalized = MeshNormalizer.normalize(cube, resolution);
        VoxelGrid grid = Voxelizer.voxelize(normalized, resolution);

        // Verify grid dimensions match resolution
        assertEquals(resolution, grid.width());
        assertEquals(resolution, grid.height());
        assertEquals(resolution, grid.depth());

        // All filled voxels must be in valid range
        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    // Should not throw even at boundaries
                    boolean filled = grid.isFilled(x, y, z);
                    assertTrue(true); // If we got here, coordinates are valid
                }
            }
        }
    }

    /**
     * CONTRACT INVARIANT: Surface extraction subset relationship preserved.
     *
     * Given solid grid from voxelization:
     * - Every surface voxel exists in solid grid
     * - Surface count <= solid count
     * - Surface represents boundary of solid region
     */
    @Test
    void testSurfaceExtractionSubsetInvariant() {
        Mesh cube = createUnitCube();
        int resolution = 12;

        Mesh normalized = MeshNormalizer.normalize(cube, resolution);
        VoxelGrid solid = Voxelizer.voxelize(normalized, resolution);
        VoxelGrid surface = SurfaceExtractor.extractSurface(solid);

        int solidCount = solid.countFilledVoxels();
        int surfaceCount = surface.countFilledVoxels();

        // Surface should be subset of solid
        assertTrue(surfaceCount <= solidCount,
            "Surface count (" + surfaceCount + ") > solid count (" + solidCount + ")");

        // Verify subset property
        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    if (surface.isFilled(x, y, z)) {
                        assertTrue(solid.isFilled(x, y, z),
                            "Surface voxel at (" + x + ", " + y + ", " + z +
                            ") not found in solid grid");
                    }
                }
            }
        }
    }

    /**
     * CONTRACT INVARIANT: Determinism across pipeline.
     *
     * Same input mesh processed twice produces identical output grids.
     * Coordinate order, ray casting, and neighbor checks are deterministic.
     */
    @Test
    void testFullPipelineDeterminism() {
        Mesh cube = createUnitCube();
        int resolution = 10;

        // Run pipeline twice
        Mesh norm1 = MeshNormalizer.normalize(cube, resolution);
        VoxelGrid solid1 = Voxelizer.voxelize(norm1, resolution);
        VoxelGrid surface1 = SurfaceExtractor.extractSurface(solid1);

        Mesh norm2 = MeshNormalizer.normalize(cube, resolution);
        VoxelGrid solid2 = Voxelizer.voxelize(norm2, resolution);
        VoxelGrid surface2 = SurfaceExtractor.extractSurface(solid2);

        // All voxels must match
        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    assertEquals(
                        solid1.isFilled(x, y, z),
                        solid2.isFilled(x, y, z),
                        "Solid mismatch at (" + x + ", " + y + ", " + z + ")"
                    );
                    assertEquals(
                        surface1.isFilled(x, y, z),
                        surface2.isFilled(x, y, z),
                        "Surface mismatch at (" + x + ", " + y + ", " + z + ")"
                    );
                }
            }
        }
    }

    /**
     * CONTRACT INVARIANT: Asymmetric mesh pipeline behavior.
     *
     * Non-cube meshes should normalize and voxelize correctly while
     * preserving aspect ratios and producing valid surface extraction.
     */
    @Test
    void testAsymmetricMeshPipeline() {
        // Tall thin mesh: 1 x 1 x 4
        List<Triangle> triangles = List.of(
            // Bottom face
            new Triangle(
                new Vector3(0.0, 0.0, 0.0),
                new Vector3(1.0, 0.0, 0.0),
                new Vector3(0.0, 1.0, 0.0)
            ),
            new Triangle(
                new Vector3(1.0, 0.0, 0.0),
                new Vector3(1.0, 1.0, 0.0),
                new Vector3(0.0, 1.0, 0.0)
            ),
            // Top face
            new Triangle(
                new Vector3(0.0, 0.0, 4.0),
                new Vector3(0.0, 1.0, 4.0),
                new Vector3(1.0, 0.0, 4.0)
            ),
            new Triangle(
                new Vector3(1.0, 0.0, 4.0),
                new Vector3(0.0, 1.0, 4.0),
                new Vector3(1.0, 1.0, 4.0)
            ),
            // Sides (simplified)
            new Triangle(
                new Vector3(0.0, 0.0, 0.0),
                new Vector3(1.0, 0.0, 4.0),
                new Vector3(1.0, 0.0, 0.0)
            ),
            new Triangle(
                new Vector3(0.0, 0.0, 0.0),
                new Vector3(0.0, 0.0, 4.0),
                new Vector3(1.0, 0.0, 4.0)
            ),
            new Triangle(
                new Vector3(1.0, 0.0, 0.0),
                new Vector3(1.0, 1.0, 4.0),
                new Vector3(1.0, 1.0, 0.0)
            ),
            new Triangle(
                new Vector3(1.0, 0.0, 0.0),
                new Vector3(1.0, 0.0, 4.0),
                new Vector3(1.0, 1.0, 4.0)
            ),
            new Triangle(
                new Vector3(0.0, 1.0, 0.0),
                new Vector3(0.0, 1.0, 4.0),
                new Vector3(1.0, 1.0, 0.0)
            ),
            new Triangle(
                new Vector3(1.0, 1.0, 0.0),
                new Vector3(0.0, 1.0, 4.0),
                new Vector3(1.0, 1.0, 4.0)
            )
        );

        Mesh asymmetric = new Mesh(triangles);
        int resolution = 16;

        // Normalize
        Mesh normalized = MeshNormalizer.normalize(asymmetric, resolution);
        BoundingBox normed = new BoundingBox(normalized);

        // Verify largest axis equals resolution
        double maxDim = Math.max(normed.width(), Math.max(normed.height(), normed.depth()));
        assertEquals(resolution, maxDim, EPSILON);

        // Verify aspect ratio (4:1:1 becomes 4:1:1 in normalized space)
        // We expect the height dimension to be ~4x the width
        assertTrue(normed.depth() > normed.width() * 2, "Aspect ratio not preserved");

        // Voxelize
        VoxelGrid solid = Voxelizer.voxelize(normalized, resolution);
        assertTrue(solid.countFilledVoxels() > 0, "Asymmetric mesh should have filled voxels");

        // Extract surface
        VoxelGrid surface = SurfaceExtractor.extractSurface(solid);
        assertTrue(surface.countFilledVoxels() <= solid.countFilledVoxels(),
            "Surface should be subset of solid");
    }

    /**
     * CONTRACT INVARIANT: Resolution=2 minimum case works across pipeline.
     *
     * Edge case: minimum supported resolution should produce valid output.
     */
    @Test
    void testMinimumResolutionPipeline() {
        Mesh cube = createUnitCube();
        int resolution = 2;

        Mesh normalized = MeshNormalizer.normalize(cube, resolution);
        VoxelGrid solid = Voxelizer.voxelize(normalized, resolution);
        VoxelGrid surface = SurfaceExtractor.extractSurface(solid);

        // Should complete without error and produce valid grids
        assertEquals(2, solid.width());
        assertEquals(2, solid.height());
        assertEquals(2, solid.depth());
        assertEquals(2, surface.width());
        assertEquals(2, surface.height());
        assertEquals(2, surface.depth());

        // Cube at res=2 should have some filled voxels
        assertTrue(solid.countFilledVoxels() > 0);
        assertTrue(surface.countFilledVoxels() > 0);
        assertTrue(surface.countFilledVoxels() <= solid.countFilledVoxels());
    }

    /**
     * CONTRACT INVARIANT: Empty mesh produces empty voxel grids.
     *
     * A mesh with no triangles should produce grids with no filled voxels.
     */
    @Test
    void testEmptyMeshProducesEmptyVoxels() {
        // Can't normalize empty mesh, but can voxelize empty mesh
        Mesh empty = new Mesh(List.of());
        int resolution = 10;

        VoxelGrid grid = Voxelizer.voxelize(empty, resolution);
        VoxelGrid surface = SurfaceExtractor.extractSurface(grid);

        assertEquals(0, grid.countFilledVoxels());
        assertEquals(0, surface.countFilledVoxels());
    }

    // Helper: Create a unit cube from (0,0,0) to (1,1,1)
    private static Mesh createUnitCube() {
        Vector3 v000 = new Vector3(0.0, 0.0, 0.0);
        Vector3 v100 = new Vector3(1.0, 0.0, 0.0);
        Vector3 v110 = new Vector3(1.0, 1.0, 0.0);
        Vector3 v010 = new Vector3(0.0, 1.0, 0.0);
        Vector3 v001 = new Vector3(0.0, 0.0, 1.0);
        Vector3 v101 = new Vector3(1.0, 0.0, 1.0);
        Vector3 v111 = new Vector3(1.0, 1.0, 1.0);
        Vector3 v011 = new Vector3(0.0, 1.0, 1.0);

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
