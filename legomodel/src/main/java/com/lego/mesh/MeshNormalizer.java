package com.lego.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Normalizes a mesh to fit within a voxel grid of a given resolution.
 */
public final class MeshNormalizer {

    private MeshNormalizer() {
        // Utility class, prevent instantiation
    }

    /**
     * Normalizes a mesh into voxel-space-friendly bounds.
     *
     * Translation:
     *   - Moves the minimum corner to (0, 0, 0)
     * Scaling:
     *   - Uniformly scales by the largest dimension to fit within [0, resolution - 1]
     *
     * @param mesh input mesh (must be non-null)
     * @param resolution voxel grid resolution (must be >= 2)
     * @return normalized mesh (new immutable Mesh)
     */
    public static Mesh normalize(Mesh mesh, int resolution) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");

        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        BoundingBox box = new BoundingBox(mesh);
        double width = box.width();
        double height = box.height();
        double depth = box.depth();

        double maxDimension = Math.max(width, Math.max(height, depth));
        if (maxDimension <= 0.0) {
            throw new IllegalArgumentException("Mesh is degenerate (zero size)");
        }

        double scale = (resolution - 1) / maxDimension;

        List<Triangle> normalized = new ArrayList<>(mesh.triangles().size());
        for (Triangle triangle : mesh.triangles()) {
            normalized.add(normalizeTriangle(triangle, box, scale));
        }

        return new Mesh(normalized);
    }

    private static Triangle normalizeTriangle(
        Triangle triangle,
        BoundingBox box,
        double scale
    ) {
        Vector3 n1 = normalizeVertex(triangle.v1(), box, scale);
        Vector3 n2 = normalizeVertex(triangle.v2(), box, scale);
        Vector3 n3 = normalizeVertex(triangle.v3(), box, scale);
        return new Triangle(n1, n2, n3);
    }

    private static Vector3 normalizeVertex(Vector3 v, BoundingBox box, double scale) {
        double x = (v.x() - box.minX()) * scale;
        double y = (v.y() - box.minY()) * scale;
        double z = (v.z() - box.minZ()) * scale;
        return new Vector3(x, y, z);
    }
}
