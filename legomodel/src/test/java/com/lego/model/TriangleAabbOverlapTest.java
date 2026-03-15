package com.lego.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TriangleAabbOverlapTest {

    @Test
    void triangleInsideBoxOverlaps() {
        // Triangle at center of a unit box centered at (0.5, 0.5, 0.5)
        Triangle tri = new Triangle(
            new Vector3(0.3, 0.3, 0.5),
            new Vector3(0.7, 0.3, 0.5),
            new Vector3(0.5, 0.7, 0.5)
        );
        assertTrue(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void triangleFarAwayDoesNotOverlap() {
        Triangle tri = new Triangle(
            new Vector3(10, 10, 10),
            new Vector3(11, 10, 10),
            new Vector3(10, 11, 10)
        );
        // Box centered at origin with half-extent 0.5
        assertFalse(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void triangleTouchingEdgeOverlaps() {
        // Triangle touches the +X face of the box
        Triangle tri = new Triangle(
            new Vector3(1.0, 0.5, 0.5),
            new Vector3(1.5, 0.0, 0.5),
            new Vector3(1.5, 1.0, 0.5)
        );
        assertTrue(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void triangleCompletelyAboveBoxDoesNotOverlap() {
        // Triangle above the box on Y axis
        Triangle tri = new Triangle(
            new Vector3(0.5, 2.0, 0.5),
            new Vector3(0.0, 3.0, 0.5),
            new Vector3(1.0, 3.0, 0.5)
        );
        assertFalse(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void degenerateTriangleAtBoxCenter() {
        // Degenerate triangle (zero area) at box center
        Triangle tri = new Triangle(
            new Vector3(0.5, 0.5, 0.5),
            new Vector3(0.5, 0.5, 0.5),
            new Vector3(0.5, 0.5, 0.5)
        );
        assertTrue(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void triangleSpanningBoxOverlaps() {
        // Large triangle that spans across the box
        Triangle tri = new Triangle(
            new Vector3(-5, -5, 0.5),
            new Vector3(5, -5, 0.5),
            new Vector3(0, 5, 0.5)
        );
        assertTrue(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void anisotropicBoxHalfExtents() {
        // Flat plate-like box: wide in X/Z, thin in Y
        Triangle tri = new Triangle(
            new Vector3(0.5, 0.5, 0.5),
            new Vector3(0.8, 0.5, 0.5),
            new Vector3(0.5, 0.5, 0.8)
        );
        // Half-extents: 0.5 in X, 1/6 in Y (plate height), 0.5 in Z
        assertTrue(TriangleAabbTest.overlaps(tri, 0.5, 0.5, 0.5, 0.5, 1.0 / 6.0, 0.5));
    }
}
