package com.lego.voxel;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Topological surface voxelizer using triangle-AABB overlap detection.
 *
 * <p>Implementation notes:
 * - Triangle-driven candidate traversal (no full-grid sweep)
 * - Sparse occupancy via packed voxel keys
 * - Triangle-AABB overlap tested via the Separating Axis Theorem (SAT,
 *   Akenine-Möller 2001): 13 axes tested — 3 AABB face normals, 1 triangle
 *   face normal, 9 edge cross-products. Any triangle that overlaps a voxel
 *   AABB is guaranteed to be detected.
 * </p>
 */
public final class TopologicalVoxelizer {

    private static final double DEFAULT_EPSILON = 1e-9;

    private TopologicalVoxelizer() {
        // Utility class
    }

    public static VoxelGrid voxelizeSurface(Mesh mesh, int resolution) {
        Objects.requireNonNull(mesh, "Mesh cannot be null");
        if (resolution < 2) {
            throw new IllegalArgumentException("Resolution must be >= 2");
        }

        TopologicalVoxelizerConfig config = new TopologicalVoxelizerConfig(
            1.0, 1.0 / 3.0, 1.0, DEFAULT_EPSILON
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
            int yResolution = (int) Math.round(resolution / config.voxelSizeY());
            return new VoxelGrid(resolution, yResolution, resolution);
        }

        Bounds rawBounds = computeMeshBounds(mesh);
        int yResolution = (int) Math.round(resolution / config.voxelSizeY());
        AlignedBounds alignedBounds = computeAlignedBounds(rawBounds, config, resolution, yResolution);

        TopologicalVoxelGrid sparseGrid = new TopologicalVoxelGrid(resolution, yResolution, resolution);

        double hx = config.voxelSizeX() * 0.5;
        double hy = config.voxelSizeY() * 0.5;
        double hz = config.voxelSizeZ() * 0.5;

        for (Triangle triangle : mesh.triangles()) {
            int[] range = triangleToCandidateRange(triangle, alignedBounds, config, resolution, yResolution);
            int iMin = range[0], iMax = range[1];
            int jMin = range[2], jMax = range[3];
            int kMin = range[4], kMax = range[5];

            for (int i = iMin; i <= iMax; i++) {
                for (int j = jMin; j <= jMax; j++) {
                    for (int k = kMin; k <= kMax; k++) {
                        double cx = alignedBounds.minX + (i + 0.5) * config.voxelSizeX();
                        double cy = alignedBounds.minY + (j + 0.5) * config.voxelSizeY();
                        double cz = alignedBounds.minZ + (k + 0.5) * config.voxelSizeZ();

                        if (triangleOverlapsVoxel(triangle, cx, cy, cz, hx, hy, hz)) {
                            sparseGrid.setSurfaceFilled(i, j, k);
                        }
                    }
                }
            }
        }

        VoxelGrid result = sparseGrid.toVoxelGrid();
        return fillAxisAlignedGaps(result, 2);
    }

    /**
     * Closes single-voxel gaps in a surface grid by filling any empty voxel that is
     * sandwiched between two filled voxels along at least one coordinate axis (±X, ±Y,
     * or ±Z). Two passes are run so adjacent gaps in thin structures are also closed.
     *
     * @param grid   the surface voxel grid to process
     * @param passes number of fill passes to run
     * @return a new VoxelGrid with gaps filled (the input grid is not modified)
     */
    private static VoxelGrid fillAxisAlignedGaps(VoxelGrid grid, int passes) {
        int w = grid.width();
        int h = grid.height();
        int d = grid.depth();

        VoxelGrid current = grid;
        for (int pass = 0; pass < passes; pass++) {
            VoxelGrid next = new VoxelGrid(w, h, d);
            boolean anyFilled = false;
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < d; z++) {
                        if (current.isFilled(x, y, z)) {
                            next.setFilled(x, y, z, true);
                        } else if (
                            (current.isFilled(x - 1, y, z) && current.isFilled(x + 1, y, z)) ||
                            (current.isFilled(x, y - 1, z) && current.isFilled(x, y + 1, z)) ||
                            (current.isFilled(x, y, z - 1) && current.isFilled(x, y, z + 1))
                        ) {
                            next.setFilled(x, y, z, true);
                            anyFilled = true;
                        }
                    }
                }
            }
            current = next;
            if (!anyFilled) break;
        }
        return current;
    }

    /**
     * Tests whether a triangle overlaps an axis-aligned voxel box using the
     * Separating Axis Theorem (SAT). Tests 13 candidate separating axes:
     * 3 AABB face normals, 1 triangle face normal, and 9 edge cross-products.
     *
     * @param tri the triangle
     * @param cx  voxel center X
     * @param cy  voxel center Y
     * @param cz  voxel center Z
     * @param hx  voxel half-extent on X
     * @param hy  voxel half-extent on Y
     * @param hz  voxel half-extent on Z
     * @return true if the triangle overlaps the voxel
     */
    private static boolean triangleOverlapsVoxel(
        Triangle tri,
        double cx, double cy, double cz,
        double hx, double hy, double hz
    ) {
        // Translate triangle vertices so AABB is centered at origin
        double v0x = tri.v1().x() - cx, v0y = tri.v1().y() - cy, v0z = tri.v1().z() - cz;
        double v1x = tri.v2().x() - cx, v1y = tri.v2().y() - cy, v1z = tri.v2().z() - cz;
        double v2x = tri.v3().x() - cx, v2y = tri.v3().y() - cy, v2z = tri.v3().z() - cz;

        // Axes 1–3: AABB face normals (coordinate axes)
        if (separatingOnAxis(v0x, v1x, v2x, hx)) return false;
        if (separatingOnAxis(v0y, v1y, v2y, hy)) return false;
        if (separatingOnAxis(v0z, v1z, v2z, hz)) return false;

        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        double e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        // Axis 4: triangle face normal  n = e0 × (v2 - v0)
        double nx = e0y * (v2z - v0z) - e0z * (v2y - v0y);
        double ny = e0z * (v2x - v0x) - e0x * (v2z - v0z);
        double nz = e0x * (v2y - v0y) - e0y * (v2x - v0x);
        double d = nx * v0x + ny * v0y + nz * v0z;
        double rn = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
        if (d > rn || d < -rn) return false;

        // Axes 5–13: 9 edge × AABB-axis cross products
        // e × X = (0, ez, -ey);  e × Y = (-ez, 0, ex);  e × Z = (ey, -ex, 0)
        if (axisEdgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e0y, e0z, hy, hz)) return false;
        if (axisEdgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e0x, e0z, hx, hz)) return false;
        if (axisEdgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e0x, e0y, hx, hy)) return false;

        if (axisEdgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e1y, e1z, hy, hz)) return false;
        if (axisEdgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e1x, e1z, hx, hz)) return false;
        if (axisEdgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e1x, e1y, hx, hy)) return false;

        if (axisEdgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e2y, e2z, hy, hz)) return false;
        if (axisEdgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e2x, e2z, hx, hz)) return false;
        if (axisEdgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e2x, e2y, hx, hy)) return false;

        return true;
    }

    /** Returns true if the AABB interval [-h, h] does not overlap the triangle's projection. */
    private static boolean separatingOnAxis(double p0, double p1, double p2, double h) {
        return Math.min(p0, Math.min(p1, p2)) > h || Math.max(p0, Math.max(p1, p2)) < -h;
    }

    /** SAT test for axis = e × X = (0, ez, -ey); r = hy|ez| + hz|ey|. */
    private static boolean axisEdgeCrossX(
        double v0y, double v0z, double v1y, double v1z, double v2y, double v2z,
        double ey, double ez, double hy, double hz
    ) {
        double p0 = ez * v0y - ey * v0z;
        double p1 = ez * v1y - ey * v1z;
        double p2 = ez * v2y - ey * v2z;
        double r = hy * Math.abs(ez) + hz * Math.abs(ey);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    /** SAT test for axis = e × Y = (-ez, 0, ex); r = hx|ez| + hz|ex|. */
    private static boolean axisEdgeCrossY(
        double v0x, double v0z, double v1x, double v1z, double v2x, double v2z,
        double ex, double ez, double hx, double hz
    ) {
        double p0 = -ez * v0x + ex * v0z;
        double p1 = -ez * v1x + ex * v1z;
        double p2 = -ez * v2x + ex * v2z;
        double r = hx * Math.abs(ez) + hz * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    /** SAT test for axis = e × Z = (ey, -ex, 0); r = hx|ey| + hy|ex|. */
    private static boolean axisEdgeCrossZ(
        double v0x, double v0y, double v1x, double v1y, double v2x, double v2y,
        double ex, double ey, double hx, double hy
    ) {
        double p0 = ey * v0x - ex * v0y;
        double p1 = ey * v1x - ex * v1y;
        double p2 = ey * v2x - ex * v2y;
        double r = hx * Math.abs(ey) + hy * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static Bounds computeMeshBounds(Mesh mesh) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Triangle t : mesh.triangles()) {
            for (Vector3 v : new Vector3[]{t.v1(), t.v2(), t.v3()}) {
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

    private static AlignedBounds computeAlignedBounds(Bounds bounds, TopologicalVoxelizerConfig config, int resolution, int yResolution) {
        AxisAligned x = alignAxis(bounds.minX, bounds.maxX, config.voxelSizeX(), resolution);
        AxisAligned y = alignAxis(bounds.minY, bounds.maxY, config.voxelSizeY(), yResolution);
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
        int resolution,
        int yResolution
    ) {
        double triMinX = Math.min(tri.v1().x(), Math.min(tri.v2().x(), tri.v3().x()));
        double triMaxX = Math.max(tri.v1().x(), Math.max(tri.v2().x(), tri.v3().x()));
        double triMinY = Math.min(tri.v1().y(), Math.min(tri.v2().y(), tri.v3().y()));
        double triMaxY = Math.max(tri.v1().y(), Math.max(tri.v2().y(), tri.v3().y()));
        double triMinZ = Math.min(tri.v1().z(), Math.min(tri.v2().z(), tri.v3().z()));
        double triMaxZ = Math.max(tri.v1().z(), Math.max(tri.v2().z(), tri.v3().z()));

        int iMin = clamp((int) Math.floor((triMinX - bounds.minX) / config.voxelSizeX()) - 1, 0, resolution - 1);
        int iMax = clamp((int) Math.ceil((triMaxX - bounds.minX) / config.voxelSizeX()) + 1, 0, resolution - 1);
        int jMin = clamp((int) Math.floor((triMinY - bounds.minY) / config.voxelSizeY()) - 1, 0, yResolution - 1);
        int jMax = clamp((int) Math.ceil((triMaxY - bounds.minY) / config.voxelSizeY()) + 1, 0, yResolution - 1);
        int kMin = clamp((int) Math.floor((triMinZ - bounds.minZ) / config.voxelSizeZ()) - 1, 0, resolution - 1);
        int kMax = clamp((int) Math.ceil((triMaxZ - bounds.minZ) / config.voxelSizeZ()) + 1, 0, resolution - 1);

        return new int[] {iMin, iMax, jMin, jMax, kMin, kMax};
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static final class Bounds {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    private static final class AxisAligned {
        final double min, max;

        AxisAligned(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class AlignedBounds {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        AlignedBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    private static final class TopologicalVoxelGrid {
        private final int width, height, depth;
        private final Set<Long> surface = new HashSet<>();

        TopologicalVoxelGrid(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        void setSurfaceFilled(int i, int j, int k) {
            if (i < 0 || i >= width || j < 0 || j >= height || k < 0 || k >= depth) return;
            surface.add(pack(i, j, k));
        }

        VoxelGrid toVoxelGrid() {
            VoxelGrid grid = new VoxelGrid(width, height, depth);
            for (long key : surface) {
                grid.setFilled(unpackX(key), unpackY(key), unpackZ(key), true);
            }
            return grid;
        }

        private static long pack(int x, int y, int z) {
            return ((long) x << 42) | ((long) y << 21) | (long) z;
        }

        private static int unpackX(long key) { return (int) (key >>> 42); }
        private static int unpackY(long key) { return (int) ((key >>> 21) & 0x1FFFFF); }
        private static int unpackZ(long key) { return (int) (key & 0x1FFFFF); }
    }
}
