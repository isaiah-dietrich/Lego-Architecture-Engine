package com.lego.voxel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Topological surface voxelizer using segment-triangle intersection (Nourian et al., 2016).
 *
 * <p><b>Algorithm overview:</b></p>
 * <ol>
 *   <li>Compute mesh AABB and expand to voxel-aligned bounds.</li>
 *   <li>For each triangle, compute local voxel range (AABB + padding).</li>
 *   <li>For each voxel in range, test connectivity segments against triangle.</li>
 *   <li>Mark voxel as surface if any segment intersects the triangle.</li>
 *   <li>Use sparse storage (HashSet) to avoid full grid allocation.</li>
 * </ol>
 *
 * <p><b>Connectivity models:</b></p>
 * <ul>
 *   <li><b>SIX:</b> 6 orthogonal segments (±X, ±Y, ±Z)—thinner, faster.</li>
 *   <li><b>TWENTY_SIX:</b> 26 segments to all neighbors—more surface detail.</li>
 * </ul>
 *
 * <p><b>Performance characteristics:</b></p>
 * Triangle-driven iteration (only candidate voxels) is typically O(T·A) where T is
 * triangle count and A is average voxel AABB area, vs. full-grid O(R³). Sparse storage
 * avoids allocating full resolution³ arrays.
 */
public final class TopologicalVoxelizer {

    private static final double EPSILON = 1e-9;

    // Default configuration for single-resolution entry point
    private static final Connectivity DEFAULT_CONNECTIVITY = Connectivity.TWENTY_SIX;
    private static final double DEFAULT_EPSILON = 1e-9;

    private TopologicalVoxelizer() {
        // Utility class
    }

    /**
     * Voxelizes a mesh surface using topological segment-triangle intersection.
     * Uses isotropic unit voxels and TWENTY_SIX connectivity by default.
     *
     * @param mesh the input mesh (must be non-null, valid)
     * @param resolution grid resolution (must be >= 2)
     * @return sparse surface voxel grid
     * @throws IllegalArgumentException if mesh is null or resolution is invalid
     */
    public static VoxelGrid voxelizeSurface(Mesh mesh, int resolution) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");
        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        TopologicalVoxelizerConfig config = new TopologicalVoxelizerConfig(
            1.0, 1.0, 1.0,
            DEFAULT_CONNECTIVITY,
            DEFAULT_EPSILON
        );

        return voxelizeSurfaceWithConfig(mesh, resolution, config);
    }

    /**
     * Voxelizes a mesh surface with explicit configuration.
     *
     * @param mesh the input mesh (must be non-null, valid)
     * @param resolution grid resolution (must be >= 2)
     * @param config voxelization configuration
     * @return sparse surface voxel grid
     */
    public static VoxelGrid voxelizeSurfaceWithConfig(
        Mesh mesh,
        int resolution,
        TopologicalVoxelizerConfig config
    ) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");
        Objects.requireNonNull(config, "Config cannot be null");
        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        TopologicalVoxelGrid grid = new TopologicalVoxelGrid(resolution, resolution, resolution);
        Bounds bounds = computeAlignedBounds(mesh, config);

        // Connectivity segment template
        SegmentTemplate[] segments = createSegmentTemplate(config.connectivity());

        // Process each triangle
        for (Triangle tri : mesh.triangles()) {
            // Local candidate region: triangle AABB expanded by voxel size with 1-cell padding
            int[] candidateRange = triAngleAABBToVoxelRange(tri, bounds, config);

            // Early-exit optimization: if triangle projects outside grid, skip
            if (candidateRange == null) {
                continue;
            }

            int iMin = candidateRange[0], iMax = candidateRange[1];
            int jMin = candidateRange[2], jMax = candidateRange[3];
            int kMin = candidateRange[4], kMax = candidateRange[5];

            // Test voxels in candidate region
            for (int i = iMin; i <= iMax; i++) {
                for (int j = jMin; j <= jMax; j++) {
                    for (int k = kMin; k <= kMax; k++) {
                        // Compute voxel center in world coordinates
                        double cx = bounds.minX + (i + 0.5) * config.voxelSizeX();
                        double cy = bounds.minY + (j + 0.5) * config.voxelSizeY();
                        double cz = bounds.minZ + (k + 0.5) * config.voxelSizeZ();

                        // Test all segments; mark surface if any hits
                        for (SegmentTemplate seg : segments) {
                            double p1x = cx + seg.dx1 * config.voxelSizeX();
                            double p1y = cy + seg.dy1 * config.voxelSizeY();
                            double p1z = cz + seg.dz1 * config.voxelSizeZ();

                            double p2x = cx + seg.dx2 * config.voxelSizeX();
                            double p2y = cy + seg.dy2 * config.voxelSizeY();
                            double p2z = cz + seg.dz2 * config.voxelSizeZ();

                            if (segmentIntersectsTriangle(p1x, p1y, p1z, p2x, p2y, p2z, tri, config.epsilon())) {
                                grid.setSurfaceFilled(i, j, k, true);
                                break; // Early exit: segment hit found
                            }
                        }
                    }
                }
            }
        }

        return grid.toVoxelGrid();
    }

    /**
     * Computes mesh AABB and expands to exact voxel-aligned bounds.
     */
    private static Bounds computeAlignedBounds(Mesh mesh, TopologicalVoxelizerConfig config) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (Triangle tri : mesh.triangles()) {
            Vector3 v1 = tri.v1();
            minX = Math.min(minX, v1.x());
            minY = Math.min(minY, v1.y());
            minZ = Math.min(minZ, v1.z());
            maxX = Math.max(maxX, v1.x());
            maxY = Math.max(maxY, v1.y());
            maxZ = Math.max(maxZ, v1.z());

            Vector3 v2 = tri.v2();
            minX = Math.min(minX, v2.x());
            minY = Math.min(minY, v2.y());
            minZ = Math.min(minZ, v2.z());
            maxX = Math.max(maxX, v2.x());
            maxY = Math.max(maxY, v2.y());
            maxZ = Math.max(maxZ, v2.z());

            Vector3 v3 = tri.v3();
            minX = Math.min(minX, v3.x());
            minY = Math.min(minY, v3.y());
            minZ = Math.min(minZ, v3.z());
            maxX = Math.max(maxX, v3.x());
            maxY = Math.max(maxY, v3.y());
            maxZ = Math.max(maxZ, v3.z());
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Converts a triangle's AABB to voxel index range with 1-cell padding.
     *
     * @return {iMin, iMax, jMin, jMax, kMin, kMax} or null if outside grid
     */
    private static int[] triAngleAABBToVoxelRange(
        Triangle tri,
        Bounds bounds,
        TopologicalVoxelizerConfig config
    ) {
        Vector3 v1 = tri.v1();
        Vector3 v2 = tri.v2();
        Vector3 v3 = tri.v3();

        double minX = Math.min(v1.x(), Math.min(v2.x(), v3.x()));
        double maxX = Math.max(v1.x(), Math.max(v2.x(), v3.x()));
        double minY = Math.min(v1.y(), Math.min(v2.y(), v3.y()));
        double maxY = Math.max(v1.y(), Math.max(v2.y(), v3.y()));
        double minZ = Math.min(v1.z(), Math.min(v2.z(), v3.z()));
        double maxZ = Math.max(v1.z(), Math.max(v2.z(), v3.z()));

        // Convert to voxel indices with padding
        int iMin = Math.max(0, (int) Math.floor((minX - bounds.minX) / config.voxelSizeX()) - 1);
        int iMax = Math.min(Integer.MAX_VALUE / 2, (int) Math.ceil((maxX - bounds.minX) / config.voxelSizeX()) + 1);
        int jMin = Math.max(0, (int) Math.floor((minY - bounds.minY) / config.voxelSizeY()) - 1);
        int jMax = Math.min(Integer.MAX_VALUE / 2, (int) Math.ceil((maxY - bounds.minY) / config.voxelSizeY()) + 1);
        int kMin = Math.max(0, (int) Math.floor((minZ - bounds.minZ) / config.voxelSizeZ()) - 1);
        int kMax = Math.min(Integer.MAX_VALUE / 2, (int) Math.ceil((maxZ - bounds.minZ) / config.voxelSizeZ()) + 1);

        // Quick prune: if range is entirely outside, return null
        if (iMin > iMax || jMin > jMax || kMin > kMax) {
            return null;
        }

        return new int[] { iMin, iMax, jMin, jMax, kMin, kMax };
    }

    /**
     * Creates connectivity segment templates (normalized offsets from voxel center).
     */
    private static SegmentTemplate[] createSegmentTemplate(Connectivity connectivity) {
        return switch (connectivity) {
            case SIX -> createSixConnectivitySegments();
            case TWENTY_SIX -> createTwentySixConnectivitySegments();
        };
    }

    /**
     * SIX-connectivity: 6 segments to face centers.
     */
    private static SegmentTemplate[] createSixConnectivitySegments() {
        return new SegmentTemplate[] {
            new SegmentTemplate(0, 0, 0, 0.5, 0, 0),    // +X
            new SegmentTemplate(0, 0, 0, -0.5, 0, 0),   // -X
            new SegmentTemplate(0, 0, 0, 0, 0.5, 0),    // +Y
            new SegmentTemplate(0, 0, 0, 0, -0.5, 0),   // -Y
            new SegmentTemplate(0, 0, 0, 0, 0, 0.5),    // +Z
            new SegmentTemplate(0, 0, 0, 0, 0, -0.5)    // -Z
        };
    }

    /**
     * TWENTY_SIX-connectivity: 26 segments to all neighbors.
     * Includes face neighbors (6), edge neighbors (12), and corner neighbors (8).
     */
    private static SegmentTemplate[] createTwentySixConnectivitySegments() {
        SegmentTemplate[] segments = new SegmentTemplate[26];
        int idx = 0;

        // Face neighbors (6)
        double[] faceOffsets = {0.5, -0.5};
        for (double dx : faceOffsets) {
            segments[idx++] = new SegmentTemplate(0, 0, 0, dx, 0, 0);
        }
        for (double dy : faceOffsets) {
            segments[idx++] = new SegmentTemplate(0, 0, 0, 0, dy, 0);
        }
        for (double dz : faceOffsets) {
            segments[idx++] = new SegmentTemplate(0, 0, 0, 0, 0, dz);
        }

        // Edge neighbors (12): connecting face centers
        double[] edgeValues = {0.5, -0.5};
        for (double dx : edgeValues) {
            for (double dy : edgeValues) {
                segments[idx++] = new SegmentTemplate(0, 0, 0, dx, dy, 0);
            }
        }
        for (double dx : edgeValues) {
            for (double dz : edgeValues) {
                segments[idx++] = new SegmentTemplate(0, 0, 0, dx, 0, dz);
            }
        }
        for (double dy : edgeValues) {
            for (double dz : edgeValues) {
                segments[idx++] = new SegmentTemplate(0, 0, 0, 0, dy, dz);
            }
        }

        // Corner neighbors (8)
        for (double dx : edgeValues) {
            for (double dy : edgeValues) {
                for (double dz : edgeValues) {
                    segments[idx++] = new SegmentTemplate(0, 0, 0, dx, dy, dz);
                }
            }
        }

        return segments;
    }

    /**
     * Möller–Trumbore-style segment-triangle intersection test.
     *
     * <p>Robust handling of parallel, degenerate, and endpoint cases.</p>
     */
    private static boolean segmentIntersectsTriangle(
        double p1x, double p1y, double p1z,
        double p2x, double p2y, double p2z,
        Triangle tri,
        double epsilon
    ) {
        Vector3 v0 = tri.v1();
        Vector3 v1 = tri.v2();
        Vector3 v2 = tri.v3();

        // Segment direction
        double dirX = p2x - p1x;
        double dirY = p2y - p1y;
        double dirZ = p2z - p1z;

        // Edge vectors
        double edge1X = v1.x() - v0.x();
        double edge1Y = v1.y() - v0.y();
        double edge1Z = v1.z() - v0.z();

        double edge2X = v2.x() - v0.x();
        double edge2Y = v2.y() - v0.y();
        double edge2Z = v2.z() - v0.z();

        // h = dir × edge2
        double hX = (dirY * edge2Z) - (dirZ * edge2Y);
        double hY = (dirZ * edge2X) - (dirX * edge2Z);
        double hZ = (dirX * edge2Y) - (dirY * edge2X);

        // a = edge1 · h
        double a = (edge1X * hX) + (edge1Y * hY) + (edge1Z * hZ);

        // Parallel or degenerate triangle
        if (Math.abs(a) < epsilon) {
            return false;
        }

        double f = 1.0 / a;

        // s = p1 - v0
        double sX = p1x - v0.x();
        double sY = p1y - v0.y();
        double sZ = p1z - v0.z();

        // u = f * (s · h)
        double u = f * ((sX * hX) + (sY * hY) + (sZ * hZ));
        if (u < -epsilon || u > 1.0 + epsilon) {
            return false;
        }

        // q = s × edge1
        double qX = (sY * edge1Z) - (sZ * edge1Y);
        double qY = (sZ * edge1X) - (sX * edge1Z);
        double qZ = (sX * edge1Y) - (sY * edge1X);

        // v = f * (dir · q)
        double v = f * ((dirX * qX) + (dirY * qY) + (dirZ * qZ));
        if (v < -epsilon || (u + v) > 1.0 + epsilon) {
            return false;
        }

        // t = f * (edge2 · q)
        double t = f * ((edge2X * qX) + (edge2Y * qY) + (edge2Z * qZ));

        // Intersection is valid if t ∈ (0, 1) for a segment (not ray)
        return t > epsilon && t < 1.0 - epsilon;
    }

    /**
     * Simple segment template: normalized offset from voxel center to endpoint.
     */
    private static class SegmentTemplate {
        final double dx1, dy1, dz1;  // First endpoint offset
        final double dx2, dy2, dz2;  // Second endpoint offset

        SegmentTemplate(double dx1, double dy1, double dz1, double dx2, double dy2, double dz2) {
            this.dx1 = dx1;
            this.dy1 = dy1;
            this.dz1 = dz1;
            this.dx2 = dx2;
            this.dy2 = dy2;
            this.dz2 = dz2;
        }
    }

    /**
     * Axis-aligned bounding box representation.
     */
    private static class Bounds {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    /**
     * Sparse voxel grid using HashSet<Long> for surface-only storage.
     * Packs (i,j,k) into a single long: (i << 40) | (j << 20) | k
     */
    private static class TopologicalVoxelGrid {
        private final int width, height, depth;
        private final Set<Long> surfaceVoxels = new HashSet<>();

        TopologicalVoxelGrid(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        void setSurfaceFilled(int i, int j, int k, boolean filled) {
            if (filled && isInBounds(i, j, k)) {
                surfaceVoxels.add(packCoordinates(i, j, k));
            }
        }

        private boolean isInBounds(int i, int j, int k) {
            return i >= 0 && i < width && j >= 0 && j < height && k >= 0 && k < depth;
        }

        VoxelGrid toVoxelGrid() {
            VoxelGrid grid = new VoxelGrid(width, height, depth);
            for (long packed : surfaceVoxels) {
                int i = (int) (packed >> 40);
                int j = (int) ((packed >> 20) & 0xFFFFF);
                int k = (int) (packed & 0xFFFFF);
                grid.setFilled(i, j, k, true);
            }
            return grid;
        }

        private static long packCoordinates(int i, int j, int k) {
            return ((long) i << 40) | ((long) j << 20) | (long) k;
        }
    }
}
