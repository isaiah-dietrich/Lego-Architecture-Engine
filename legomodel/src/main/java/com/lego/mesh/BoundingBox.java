package com.lego.mesh;

import java.util.List;
import java.util.Objects;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Axis-aligned bounding box for a mesh.
 */
public final class BoundingBox {

    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    /**
     * Constructs a bounding box from a mesh.
     *
     * @param mesh input mesh (must be non-null and contain at least one triangle)
     */
    public BoundingBox(Mesh mesh) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");

        List<Triangle> triangles = mesh.triangles();
        if (triangles.isEmpty()) {
            throw new IllegalArgumentException("Mesh must contain at least one triangle");
        }

        Vector3 first = firstVertex(triangles.get(0));
        double minX = first.x();
        double minY = first.y();
        double minZ = first.z();
        double maxX = first.x();
        double maxY = first.y();
        double maxZ = first.z();

        for (Triangle triangle : triangles) {
            minX = min(minX, triangle.v1().x(), triangle.v2().x(), triangle.v3().x());
            minY = min(minY, triangle.v1().y(), triangle.v2().y(), triangle.v3().y());
            minZ = min(minZ, triangle.v1().z(), triangle.v2().z(), triangle.v3().z());

            maxX = max(maxX, triangle.v1().x(), triangle.v2().x(), triangle.v3().x());
            maxY = max(maxY, triangle.v1().y(), triangle.v2().y(), triangle.v3().y());
            maxZ = max(maxZ, triangle.v1().z(), triangle.v2().z(), triangle.v3().z());
        }

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double minZ() {
        return minZ;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public double maxZ() {
        return maxZ;
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public double depth() {
        return maxZ - minZ;
    }

    private static Vector3 firstVertex(Triangle triangle) {
        return triangle.v1();
    }

    private static double min(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }
}
