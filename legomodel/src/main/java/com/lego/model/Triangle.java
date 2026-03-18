package com.lego.model;

/**
 * Immutable triangle representation in 3D space.
 *
 * A triangle is defined by three vertices. All vertices must be non-null.
 * Vertex order is preserved for rendering and geometric computations.
 */
public record Triangle(Vector3 v1, Vector3 v2, Vector3 v3) {

    /**
     * Compact constructor validates that all vertices are non-null.
     */
    public Triangle {
        if (v1 == null) {
            throw new IllegalArgumentException("Triangle vertex v1 cannot be null");
        }
        if (v2 == null) {
            throw new IllegalArgumentException("Triangle vertex v2 cannot be null");
        }
        if (v3 == null) {
            throw new IllegalArgumentException("Triangle vertex v3 cannot be null");
        }
    }

    /**
     * Returns the face normal of the triangle via cross product of edges.
     * The result is normalized (unit length). Returns Vector3.ZERO for degenerate triangles.
     */
    public Vector3 normal() {
        Vector3 edge1 = new Vector3(v2.x() - v1.x(), v2.y() - v1.y(), v2.z() - v1.z());
        Vector3 edge2 = new Vector3(v3.x() - v1.x(), v3.y() - v1.y(), v3.z() - v1.z());
        return edge1.cross(edge2).normalize();
    }
}
