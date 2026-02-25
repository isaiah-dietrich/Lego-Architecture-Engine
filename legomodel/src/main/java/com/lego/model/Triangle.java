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
}
