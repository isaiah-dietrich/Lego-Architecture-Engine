package com.lego.mesh;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

class MeshNormalizerTest {

    private static final double EPSILON = 1e-9;

    @Test
    void testTranslationToOrigin() {
        Triangle t = new Triangle(
            new Vector3(2.0, 3.0, 4.0),
            new Vector3(4.0, 3.0, 4.0),
            new Vector3(2.0, 6.0, 4.0)
        );

        Mesh mesh = new Mesh(List.of(t));
        Mesh normalized = MeshNormalizer.normalize(mesh, 10);
        BoundingBox box = new BoundingBox(normalized);

        assertEquals(0.0, box.minX(), EPSILON);
        assertEquals(0.0, box.minY(), EPSILON);
        assertEquals(0.0, box.minZ(), EPSILON);
    }

    @Test
    void testUniformScalingPreservesProportions() {
        Triangle t = new Triangle(
            new Vector3(1.0, 1.0, 1.0),
            new Vector3(5.0, 1.0, 1.0),
            new Vector3(1.0, 3.0, 1.0)
        );

        Mesh mesh = new Mesh(List.of(t));
        BoundingBox original = new BoundingBox(mesh);

        Mesh normalized = MeshNormalizer.normalize(mesh, 20);
        BoundingBox normalizedBox = new BoundingBox(normalized);

        double originalRatio = original.width() / original.height();
        double normalizedRatio = normalizedBox.width() / normalizedBox.height();

        assertEquals(originalRatio, normalizedRatio, EPSILON);
    }

    @Test
    void testNormalizedMeshFitsBounds() {
        Triangle t1 = new Triangle(
            new Vector3(-2.0, -1.0, 0.0),
            new Vector3(2.0, -1.0, 0.0),
            new Vector3(-2.0, 3.0, 4.0)
        );
        Triangle t2 = new Triangle(
            new Vector3(1.0, 2.0, 6.0),
            new Vector3(-1.0, 0.0, 2.0),
            new Vector3(3.0, 1.0, -1.0)
        );

        Mesh mesh = new Mesh(List.of(t1, t2));
        int resolution = 40;
        Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
        BoundingBox box = new BoundingBox(normalized);

        double maxAllowed = resolution;

        assertTrue(box.minX() >= 0.0);
        assertTrue(box.minY() >= 0.0);
        assertTrue(box.minZ() >= 0.0);
        assertTrue(box.maxX() <= maxAllowed + EPSILON);
        assertTrue(box.maxY() <= maxAllowed + EPSILON);
        assertTrue(box.maxZ() <= maxAllowed + EPSILON);
    }

    @Test
    void testBadResolutionThrowsException() {
        Triangle t = new Triangle(
            new Vector3(0.0, 0.0, 0.0),
            new Vector3(1.0, 0.0, 0.0),
            new Vector3(0.0, 1.0, 0.0)
        );
        Mesh mesh = new Mesh(List.of(t));

        assertThrows(IllegalArgumentException.class, () -> MeshNormalizer.normalize(mesh, 1));
    }

    @Test
    void testDegenerateMeshThrowsException() {
        Triangle t = new Triangle(
            new Vector3(1.0, 1.0, 1.0),
            new Vector3(1.0, 1.0, 1.0),
            new Vector3(1.0, 1.0, 1.0)
        );
        Mesh mesh = new Mesh(List.of(t));

        assertThrows(IllegalArgumentException.class, () -> MeshNormalizer.normalize(mesh, 10));
    }

    @Test
    void testNullMeshThrowsException() {
        assertThrows(NullPointerException.class, () -> MeshNormalizer.normalize(null, 10));
    }

    /**
     * INVARIANT: Normalized mesh max corner on largest axis equals resolution.
     * This ensures voxel sampling at [0, resolution-1] covers the full mesh range.
     */
    @Test
    void testLargestAxisMaxEqualsResolution() {
        Triangle t = new Triangle(
            new Vector3(0.0, 0.0, 0.0),
            new Vector3(4.0, 0.0, 0.0),
            new Vector3(0.0, 2.0, 0.0)
        );

        Mesh mesh = new Mesh(List.of(t));
        int resolution = 20;
        Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
        BoundingBox box = new BoundingBox(normalized);

        double maxDim = Math.max(box.width(), Math.max(box.height(), box.depth()));
        assertEquals(resolution, maxDim, EPSILON);
    }

    /**
     * INVARIANT: Asymmetric mesh normalization preserves aspect ratios.
     * Non-largest axes should scale proportionally to maintain shape.
     */
    @Test
    void testAsymmetricMeshAspectRatios() {
        // Create tall, thin rectangle: 2 wide x 8 tall x 1 deep
        Triangle t1 = new Triangle(
            new Vector3(0.0, 0.0, 0.0),
            new Vector3(2.0, 0.0, 0.0),
            new Vector3(0.0, 8.0, 0.0)
        );
        Triangle t2 = new Triangle(
            new Vector3(2.0, 0.0, 0.0),
            new Vector3(2.0, 8.0, 0.0),
            new Vector3(0.0, 8.0, 0.0)
        );

        Mesh mesh = new Mesh(List.of(t1, t2));
        Mesh normalized = MeshNormalizer.normalize(mesh, 32);
        BoundingBox box = new BoundingBox(normalized);

        // Original dimensions: 2 x 8 x 1 (depth is minimal due to all being z=0)
        // After normalization, largest dimension (height=8) becomes 32
        // So width should be 32 * (2/8) = 8
        double widthToHeight = box.width() / box.height();

        // Original width/height ratio is 2/8 = 0.25
        assertEquals(0.25, widthToHeight, EPSILON);
    }

    /**
     * INVARIANT: Resolution=2 edge case normalizes mesh correctly.
     * Lowest supported resolution should produce valid bounds [0, 2].
     */
    @Test
    void testResolutionTwoEdgeCase() {
        Triangle t = new Triangle(
            new Vector3(1.0, 1.0, 1.0),
            new Vector3(3.0, 1.0, 1.0),
            new Vector3(1.0, 2.0, 1.0)
        );

        Mesh mesh = new Mesh(List.of(t));
        Mesh normalized = MeshNormalizer.normalize(mesh, 2);
        BoundingBox box = new BoundingBox(normalized);

        assertEquals(0.0, box.minX(), EPSILON);
        assertEquals(0.0, box.minY(), EPSILON);
        assertEquals(2.0, box.maxX(), EPSILON + 0.01); // Allow small numerical error
        assertTrue(box.maxY() <= 2.0 + EPSILON);
    }
}
