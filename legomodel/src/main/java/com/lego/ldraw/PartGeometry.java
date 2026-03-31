package com.lego.ldraw;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.lego.model.Triangle;

/**
 * Flattened triangle geometry plus transitive source dependencies.
 */
public record PartGeometry(List<Triangle> triangles, Set<Path> dependencyFiles) {

    public PartGeometry {
        if (triangles == null || triangles.isEmpty()) {
            throw new IllegalArgumentException("triangles must not be null/empty");
        }
        if (dependencyFiles == null || dependencyFiles.isEmpty()) {
            throw new IllegalArgumentException("dependencyFiles must not be null/empty");
        }
        triangles = List.copyOf(triangles);
        dependencyFiles = Set.copyOf(dependencyFiles);
    }
}
