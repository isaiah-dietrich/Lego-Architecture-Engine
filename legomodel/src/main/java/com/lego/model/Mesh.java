package com.lego.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable 3D mesh representation.
 *
 * A mesh is a collection of triangles that define a 3D surface.
 * The mesh is defensively constructed to ensure full immutability:
 * the input list is copied, and the internal list is unmodifiable.
 *
 * All triangles must be non-null. Null elements are rejected at construction.
 */
public final class Mesh {

    private final List<Triangle> triangles;

    /**
     * Constructs an immutable Mesh from a list of triangles.
     *
     * @param triangles list of triangles (must not be null or contain nulls)
     * @throws IllegalArgumentException if triangles is null or contains null elements
     */
    public Mesh(List<Triangle> triangles) {
        Objects.requireNonNull(triangles, "Mesh triangles list cannot be null");

        // Validate that no triangle is null
        for (int i = 0; i < triangles.size(); i++) {
            if (triangles.get(i) == null) {
                throw new IllegalArgumentException(
                    "Mesh triangle at index " + i + " cannot be null"
                );
            }
        }

        // Defensive copy: create immutable copy of the list
        this.triangles = Collections.unmodifiableList(
            List.copyOf(triangles)
        );
    }

    /**
     * Returns the immutable list of triangles in this mesh.
     *
     * @return unmodifiable list of triangles
     */
    public List<Triangle> triangles() {
        return triangles;
    }

    /**
     * Returns the number of triangles in this mesh.
     *
     * @return triangle count
     */
    public int triangleCount() {
        return triangles.size();
    }

    @Override
    public String toString() {
        return "Mesh{" +
               "triangles=" + triangles.size() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mesh mesh)) return false;
        return triangles.equals(mesh.triangles);
    }

    @Override
    public int hashCode() {
        return triangles.hashCode();
    }
}
