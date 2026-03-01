package com.lego.voxel;

import java.util.Objects;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Voxelizes a normalized mesh using ray-casting parity.
 */
public final class Voxelizer {

    private static final double EPSILON = 1e-9;
    // Small biases applied to Y and Z ray origins to avoid numerical instability
    // when rays intersect triangles exactly on shared edges. Values are kept
    // very small (< 1e-5) to minimize impact on symmetry while ensuring robust
    // boundary coverage.  Slightly different values help avoid pathological cases
    // where multiple voxels align perfectly with geometric features.
    private static final double RAY_BIAS_Y = 1.1e-6;
    private static final double RAY_BIAS_Z = 1.2e-6;

    private Voxelizer() {
        // Utility class, prevent instantiation
    }

    /**
     * Converts a normalized mesh into a filled voxel grid.
     *
     * Coordinate convention:
     *   - Normalized meshes span [0, resolution] on their largest axis.
     *   - Voxel centers are sampled at (x + 0.5, y + 0.5, z + 0.5), where
     *     x, y, z in [0, resolution - 1].
     *
     * Each voxel center casts a ray in the +X direction. An odd number of
     * triangle intersections means the voxel is inside the mesh.
     *
     * @param mesh input mesh (must be non-null)
     * @param resolution voxel grid resolution (must be >= 2)
     * @return filled VoxelGrid
     */
    public static VoxelGrid voxelize(Mesh mesh, int resolution) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");
        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        VoxelGrid grid = new VoxelGrid(resolution, resolution, resolution);

        for (int z = 0; z < resolution; z++) {
            for (int y = 0; y < resolution; y++) {
                for (int x = 0; x < resolution; x++) {
                    double ox = x + 0.5;
                    // Use slightly different biases to avoid ray hits exactly on shared edges.
                    double oy = y + 0.5 + RAY_BIAS_Y;
                    double oz = z + 0.5 + RAY_BIAS_Z;

                    if (isInside(mesh, ox, oy, oz)) {
                        grid.setFilled(x, y, z, true);
                    }
                }
            }
        }

        return grid;
    }

    private static boolean isInside(Mesh mesh, double ox, double oy, double oz) {
        int intersections = 0;
        for (Triangle triangle : mesh.triangles()) {
            if (intersectsRayTriangle(ox, oy, oz, triangle)) {
                intersections++;
            }
        }
        return (intersections % 2) == 1;
    }

    /**
     * Ray-triangle intersection using the Moller-Trumbore algorithm.
     * Ray direction is fixed as +X (1, 0, 0).
     */
    private static boolean intersectsRayTriangle(
        double ox,
        double oy,
        double oz,
        Triangle triangle
    ) {
        Vector3 v0 = triangle.v1();
        Vector3 v1 = triangle.v2();
        Vector3 v2 = triangle.v3();

        double edge1x = v1.x() - v0.x();
        double edge1y = v1.y() - v0.y();
        double edge1z = v1.z() - v0.z();

        double edge2x = v2.x() - v0.x();
        double edge2y = v2.y() - v0.y();
        double edge2z = v2.z() - v0.z();

        // h = dir x edge2, with dir = (1, 0, 0)
        double hx = 0.0;
        double hy = -edge2z;
        double hz = edge2y;

        double a = (edge1x * hx) + (edge1y * hy) + (edge1z * hz);
        if (Math.abs(a) < EPSILON) {
            return false; // Ray is parallel to triangle
        }

        double f = 1.0 / a;

        double sx = ox - v0.x();
        double sy = oy - v0.y();
        double sz = oz - v0.z();

        double u = f * ((sx * hx) + (sy * hy) + (sz * hz));
        if (u < -EPSILON || u > 1.0 + EPSILON) {
            return false;
        }

        // q = s x edge1
        double qx = (sy * edge1z) - (sz * edge1y);
        double qy = (sz * edge1x) - (sx * edge1z);
        double qz = (sx * edge1y) - (sy * edge1x);

        // v = f * dot(dir, q) with dir = (1, 0, 0)
        double v = f * qx;
        if (v < -EPSILON || u + v > 1.0 + EPSILON) {
            return false;
        }

        // t = f * dot(edge2, q)
        double t = f * ((edge2x * qx) + (edge2y * qy) + (edge2z * qz));

        return t > EPSILON;
    }
}
