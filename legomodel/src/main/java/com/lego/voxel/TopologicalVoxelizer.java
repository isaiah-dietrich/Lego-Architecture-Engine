package com.lego.voxel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Topological surface voxelizer using segment-triangle intersection.
 *
 * <p>Implementation notes:
 * - Triangle-driven candidate traversal (no full-grid sweep)
 * - Sparse occupancy via packed voxel keys
 * - Connectivity targets:
 *   - TWENTY_SIX: 3 center crosshair lines through each voxel
 *   - SIX: 12 voxel edge lines
 * </p>
 */
public final class TopologicalVoxelizer {

    private static final double DEFAULT_EPSILON = 1e-9;
    private static final Connectivity DEFAULT_CONNECTIVITY = Connectivity.TWENTY_SIX;

    private TopologicalVoxelizer() {
        // Utility class
    }

    public static VoxelGrid voxelizeSurface(Mesh mesh, int resolution) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");
        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        TopologicalVoxelizerConfig config = new TopologicalVoxelizerConfig(
            1.0,
            1.0,
            1.0,
            DEFAULT_CONNECTIVITY,
            DEFAULT_EPSILON
        );

        return voxelizeSurfaceWithConfig(mesh, resolution, config);
    }

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

        if (mesh.triangles().isEmpty()) {
            return new VoxelGrid(resolution, resolution, resolution);
        }

        Bounds rawBounds = computeMeshBounds(mesh);
        AlignedBounds alignedBounds = computeAlignedBounds(rawBounds, config, resolution);
        SegmentTemplate[] targets = createTargets(config.connectivity());

        TopologicalVoxelGrid sparseGrid = new TopologicalVoxelGrid(resolution, resolution, resolution);

        for (Triangle triangle : mesh.triangles()) {
            int[] range = triangleToCandidateRange(triangle, alignedBounds, config, resolution);
            int iMin = range[0], iMax = range[1];
            int jMin = range[2], jMax = range[3];
            int kMin = range[4], kMax = range[5];

            for (int i = iMin; i <= iMax; i++) {
                for (int j = jMin; j <= jMax; j++) {
                    for (int k = kMin; k <= kMax; k++) {
                        double cx = alignedBounds.minX + (i + 0.5) * config.voxelSizeX();
                        double cy = alignedBounds.minY + (j + 0.5) * config.voxelSizeY();
                        double cz = alignedBounds.minZ + (k + 0.5) * config.voxelSizeZ();

                        for (SegmentTemplate t : targets) {
                            double p1x = cx + t.dx1 * config.voxelSizeX();
                            double p1y = cy + t.dy1 * config.voxelSizeY();
                            double p1z = cz + t.dz1 * config.voxelSizeZ();

                            double p2x = cx + t.dx2 * config.voxelSizeX();
                            double p2y = cy + t.dy2 * config.voxelSizeY();
                            double p2z = cz + t.dz2 * config.voxelSizeZ();

                            if (segmentIntersectsTriangle(p1x, p1y, p1z, p2x, p2y, p2z, triangle, config.epsilon())) {
                                sparseGrid.setSurfaceFilled(i, j, k);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return sparseGrid.toVoxelGrid();
    }

    private static Bounds computeMeshBounds(Mesh mesh) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Triangle t : mesh.triangles()) {
            Vector3[] vs = {t.v1(), t.v2(), t.v3()};
            for (Vector3 v : vs) {
                minX = Math.min(minX, v.x());
                minY = Math.min(minY, v.y());
                minZ = Math.min(minZ, v.z());
                maxX = Math.max(maxX, v.x());
                maxY = Math.max(maxY, v.y());
                maxZ = Math.max(maxZ, v.z());
            }
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AlignedBounds computeAlignedBounds(Bounds bounds, TopologicalVoxelizerConfig config, int resolution) {
        AxisAligned x = alignAxis(bounds.minX, bounds.maxX, config.voxelSizeX(), resolution);
        AxisAligned y = alignAxis(bounds.minY, bounds.maxY, config.voxelSizeY(), resolution);
        AxisAligned z = alignAxis(bounds.minZ, bounds.maxZ, config.voxelSizeZ(), resolution);
        return new AlignedBounds(x.min, y.min, z.min, x.max, y.max, z.max);
    }

    private static AxisAligned alignAxis(double rawMin, double rawMax, double size, int resolution) {
        double span = resolution * size;
        if ((rawMax - rawMin) > span + 1e-9) {
            throw new IllegalArgumentException(
                "Mesh extent exceeds configured grid span for topological voxelization"
            );
        }

        double min = Math.floor(rawMin / size) * size;
        if (min + span < rawMax - 1e-9) {
            min = Math.floor((rawMax - span) / size) * size;
        }
        double max = min + span;
        return new AxisAligned(min, max);
    }

    private static int[] triangleToCandidateRange(
        Triangle tri,
        AlignedBounds bounds,
        TopologicalVoxelizerConfig config,
        int resolution
    ) {
        double triMinX = Math.min(tri.v1().x(), Math.min(tri.v2().x(), tri.v3().x()));
        double triMaxX = Math.max(tri.v1().x(), Math.max(tri.v2().x(), tri.v3().x()));
        double triMinY = Math.min(tri.v1().y(), Math.min(tri.v2().y(), tri.v3().y()));
        double triMaxY = Math.max(tri.v1().y(), Math.max(tri.v2().y(), tri.v3().y()));
        double triMinZ = Math.min(tri.v1().z(), Math.min(tri.v2().z(), tri.v3().z()));
        double triMaxZ = Math.max(tri.v1().z(), Math.max(tri.v2().z(), tri.v3().z()));

        int iMin = clamp((int) Math.floor((triMinX - bounds.minX) / config.voxelSizeX()) - 1, 0, resolution - 1);
        int iMax = clamp((int) Math.ceil((triMaxX - bounds.minX) / config.voxelSizeX()) + 1, 0, resolution - 1);
        int jMin = clamp((int) Math.floor((triMinY - bounds.minY) / config.voxelSizeY()) - 1, 0, resolution - 1);
        int jMax = clamp((int) Math.ceil((triMaxY - bounds.minY) / config.voxelSizeY()) + 1, 0, resolution - 1);
        int kMin = clamp((int) Math.floor((triMinZ - bounds.minZ) / config.voxelSizeZ()) - 1, 0, resolution - 1);
        int kMax = clamp((int) Math.ceil((triMaxZ - bounds.minZ) / config.voxelSizeZ()) + 1, 0, resolution - 1);

        return new int[] {iMin, iMax, jMin, jMax, kMin, kMax};
    }

    private static SegmentTemplate[] createTargets(Connectivity connectivity) {
        return switch (connectivity) {
            case TWENTY_SIX -> createTwentySixTargets();
            case SIX -> createSixTargets();
        };
    }

    // 26-connectivity target family: 3 full axis lines through voxel center.
    private static SegmentTemplate[] createTwentySixTargets() {
        return new SegmentTemplate[] {
            new SegmentTemplate(-0.5, 0.0, 0.0, 0.5, 0.0, 0.0),
            new SegmentTemplate(0.0, -0.5, 0.0, 0.0, 0.5, 0.0),
            new SegmentTemplate(0.0, 0.0, -0.5, 0.0, 0.0, 0.5)
        };
    }

    // 6-connectivity target family: 12 voxel outline edges.
    private static SegmentTemplate[] createSixTargets() {
        return new SegmentTemplate[] {
            // 4 edges parallel to X
            new SegmentTemplate(-0.5, -0.5, -0.5, 0.5, -0.5, -0.5),
            new SegmentTemplate(-0.5, -0.5, 0.5, 0.5, -0.5, 0.5),
            new SegmentTemplate(-0.5, 0.5, -0.5, 0.5, 0.5, -0.5),
            new SegmentTemplate(-0.5, 0.5, 0.5, 0.5, 0.5, 0.5),

            // 4 edges parallel to Y
            new SegmentTemplate(-0.5, -0.5, -0.5, -0.5, 0.5, -0.5),
            new SegmentTemplate(-0.5, -0.5, 0.5, -0.5, 0.5, 0.5),
            new SegmentTemplate(0.5, -0.5, -0.5, 0.5, 0.5, -0.5),
            new SegmentTemplate(0.5, -0.5, 0.5, 0.5, 0.5, 0.5),

            // 4 edges parallel to Z
            new SegmentTemplate(-0.5, -0.5, -0.5, -0.5, -0.5, 0.5),
            new SegmentTemplate(-0.5, 0.5, -0.5, -0.5, 0.5, 0.5),
            new SegmentTemplate(0.5, -0.5, -0.5, 0.5, -0.5, 0.5),
            new SegmentTemplate(0.5, 0.5, -0.5, 0.5, 0.5, 0.5)
        };
    }

    private static boolean segmentIntersectsTriangle(
        double p1x,
        double p1y,
        double p1z,
        double p2x,
        double p2y,
        double p2z,
        Triangle tri,
        double epsilon
    ) {
        Vector3 v0 = tri.v1();
        Vector3 v1 = tri.v2();
        Vector3 v2 = tri.v3();

        double dirX = p2x - p1x;
        double dirY = p2y - p1y;
        double dirZ = p2z - p1z;

        double edge1X = v1.x() - v0.x();
        double edge1Y = v1.y() - v0.y();
        double edge1Z = v1.z() - v0.z();

        double edge2X = v2.x() - v0.x();
        double edge2Y = v2.y() - v0.y();
        double edge2Z = v2.z() - v0.z();

        double hX = (dirY * edge2Z) - (dirZ * edge2Y);
        double hY = (dirZ * edge2X) - (dirX * edge2Z);
        double hZ = (dirX * edge2Y) - (dirY * edge2X);

        double a = (edge1X * hX) + (edge1Y * hY) + (edge1Z * hZ);

        if (Math.abs(a) < epsilon) {
            return pointOnTriangle(p1x, p1y, p1z, tri, epsilon)
                || pointOnTriangle(p2x, p2y, p2z, tri, epsilon);
        }

        double f = 1.0 / a;

        double sX = p1x - v0.x();
        double sY = p1y - v0.y();
        double sZ = p1z - v0.z();

        double u = f * ((sX * hX) + (sY * hY) + (sZ * hZ));
        if (u < -epsilon || u > 1.0 + epsilon) {
            return false;
        }

        double qX = (sY * edge1Z) - (sZ * edge1Y);
        double qY = (sZ * edge1X) - (sX * edge1Z);
        double qZ = (sX * edge1Y) - (sY * edge1X);

        double v = f * ((dirX * qX) + (dirY * qY) + (dirZ * qZ));
        if (v < -epsilon || (u + v) > 1.0 + epsilon) {
            return false;
        }

        double t = f * ((edge2X * qX) + (edge2Y * qY) + (edge2Z * qZ));
        return t >= -epsilon && t <= 1.0 + epsilon;
    }

    private static boolean pointOnTriangle(double px, double py, double pz, Triangle tri, double epsilon) {
        Vector3 a = tri.v1();
        Vector3 b = tri.v2();
        Vector3 c = tri.v3();

        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double abz = b.z() - a.z();
        double acx = c.x() - a.x();
        double acy = c.y() - a.y();
        double acz = c.z() - a.z();

        double nx = aby * acz - abz * acy;
        double ny = abz * acx - abx * acz;
        double nz = abx * acy - aby * acx;

        double lenN = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (lenN < epsilon) {
            return false;
        }

        double apx = px - a.x();
        double apy = py - a.y();
        double apz = pz - a.z();
        double dist = Math.abs(nx * apx + ny * apy + nz * apz) / lenN;
        if (dist > epsilon) {
            return false;
        }

        double dot00 = acx * acx + acy * acy + acz * acz;
        double dot01 = acx * abx + acy * aby + acz * abz;
        double dot02 = acx * apx + acy * apy + acz * apz;
        double dot11 = abx * abx + aby * aby + abz * abz;
        double dot12 = abx * apx + aby * apy + abz * apz;

        double denom = dot00 * dot11 - dot01 * dot01;
        if (Math.abs(denom) < epsilon) {
            return false;
        }

        double invDenom = 1.0 / denom;
        double u = (dot11 * dot02 - dot01 * dot12) * invDenom;
        double v = (dot00 * dot12 - dot01 * dot02) * invDenom;

        return u >= -epsilon && v >= -epsilon && (u + v) <= 1.0 + epsilon;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static final class SegmentTemplate {
        final double dx1;
        final double dy1;
        final double dz1;
        final double dx2;
        final double dy2;
        final double dz2;

        SegmentTemplate(double dx1, double dy1, double dz1, double dx2, double dy2, double dz2) {
            this.dx1 = dx1;
            this.dy1 = dy1;
            this.dz1 = dz1;
            this.dx2 = dx2;
            this.dy2 = dy2;
            this.dz2 = dz2;
        }
    }

    private static final class Bounds {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    private static final class AxisAligned {
        final double min;
        final double max;

        AxisAligned(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class AlignedBounds {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        AlignedBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    private static final class TopologicalVoxelGrid {
        private final int width;
        private final int height;
        private final int depth;
        private final Set<Long> surface = new HashSet<>();

        TopologicalVoxelGrid(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        void setSurfaceFilled(int i, int j, int k) {
            if (i < 0 || i >= width || j < 0 || j >= height || k < 0 || k >= depth) {
                return;
            }
            surface.add(pack(i, j, k));
        }

        VoxelGrid toVoxelGrid() {
            VoxelGrid grid = new VoxelGrid(width, height, depth);
            for (long key : surface) {
                int i = unpackX(key);
                int j = unpackY(key);
                int k = unpackZ(key);
                grid.setFilled(i, j, k, true);
            }
            return grid;
        }

        private static long pack(int x, int y, int z) {
            return ((long) x << 42) | ((long) y << 21) | (long) z;
        }

        private static int unpackX(long key) {
            return (int) (key >>> 42);
        }

        private static int unpackY(long key) {
            return (int) ((key >>> 21) & 0x1FFFFF);
        }

        private static int unpackZ(long key) {
            return (int) (key & 0x1FFFFF);
        }
    }
}
