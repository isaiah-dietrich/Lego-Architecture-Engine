package com.lego.mesh;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

class BoundingBoxTest {

    @Test
    void testBoundingBoxValues() {
        Triangle t1 = new Triangle(
            new Vector3(-1.0, 2.0, 0.0),
            new Vector3(3.0, -2.0, 4.0),
            new Vector3(0.5, 1.0, -1.0)
        );
        Triangle t2 = new Triangle(
            new Vector3(2.0, 5.0, 2.0),
            new Vector3(-3.0, 1.0, 6.0),
            new Vector3(1.0, -4.0, 3.0)
        );

        Mesh mesh = new Mesh(List.of(t1, t2));
        BoundingBox box = new BoundingBox(mesh);

        assertEquals(-3.0, box.minX());
        assertEquals(-4.0, box.minY());
        assertEquals(-1.0, box.minZ());
        assertEquals(3.0, box.maxX());
        assertEquals(5.0, box.maxY());
        assertEquals(6.0, box.maxZ());
        assertEquals(6.0, box.width());
        assertEquals(9.0, box.height());
        assertEquals(7.0, box.depth());
    }

    @Test
    void testEmptyMeshThrowsException() {
        Mesh mesh = new Mesh(List.of());
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(mesh));
    }

    @Test
    void testNullMeshThrowsException() {
        assertThrows(NullPointerException.class, () -> new BoundingBox(null));
    }
}
