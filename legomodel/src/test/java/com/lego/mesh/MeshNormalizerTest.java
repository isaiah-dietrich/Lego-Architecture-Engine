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

        double maxAllowed = resolution - 1;

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
}
