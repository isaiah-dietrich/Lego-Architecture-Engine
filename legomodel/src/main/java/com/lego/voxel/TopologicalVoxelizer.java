package com.lego.voxel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.TriangleAabbTest;
import com.lego.model.Vector3;

/**
 * Topological surface voxelizer using triangle-AABB overlap detection.
 *
 * Implementation notes:
 * - Triangle-driven candidate traversal (no full-grid sweep)
 * - Sparse occupancy via packed voxel keys
 * - Triangle-AABB overlap tested via the Separating Axis Theorem (SAT,
 *   Akenine-Möller 2001): 13 axes tested — 3 AABB face normals, 1 triangle
 *   face normal, 9 edge cross-products. Any triangle that overlaps a voxel
 *   AABB is guaranteed to be detected.
 * 
 */
public final class TopologicalVoxelizer {

    private static final double DEFAULT_EPSILON = 1e-9;

    /** Non-instantiable utility class. */
    private TopologicalVoxelizer() {
        // Utility class
    }

    /** Voxelizes a mesh surface using triangle-AABB overlap with default config. */
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

    /** Voxelizes a mesh surface using triangle-AABB overlap with the given config. */
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

        // Sparse normal accumulation: only surface voxels get entries, avoiding the
        // ~80 MB dense array (150×450×150) and the O(grid_size) transfer scan.
        // ConcurrentHashMap.compute() provides per-key atomicity for parallel writes.
        Map<Long, double[]> normalAccum = new ConcurrentHashMap<>();

        double hx = config.voxelSizeX() * 0.5;
        double hy = config.voxelSizeY() * 0.5;
        double hz = config.voxelSizeZ() * 0.5;

        // Triangles are independent — process in parallel across all available cores.
        mesh.triangles().parallelStream().forEach(triangle -> {
            // Area-weighted normal: cross product magnitude is proportional to triangle area
            double e1x = triangle.v2().x() - triangle.v1().x();
            double e1y = triangle.v2().y() - triangle.v1().y();
            double e1z = triangle.v2().z() - triangle.v1().z();
            double e2x = triangle.v3().x() - triangle.v1().x();
            double e2y = triangle.v3().y() - triangle.v1().y();
            double e2z = triangle.v3().z() - triangle.v1().z();
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;

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

                        if (TriangleAabbTest.overlaps(triangle, cx, cy, cz, hx, hy, hz)) {
                            sparseGrid.setSurfaceFilled(i, j, k);
                            // Accumulate into sparse map; compute() is per-key atomic
                            long key = TopologicalVoxelGrid.pack(i, j, k);
                            normalAccum.compute(key, (ignored, acc) -> {
                                if (acc == null) return new double[]{nx, ny, nz};
                                acc[0] += nx;
                                acc[1] += ny;
                                acc[2] += nz;
                                return acc;
                            });
                        }
                    }
                }
            }
        });

        VoxelGrid result = sparseGrid.toVoxelGrid();

        // Transfer normals — iterate only the surface voxels, not the full grid
        for (Map.Entry<Long, double[]> e : normalAccum.entrySet()) {
            long key = e.getKey();
            double[] n = e.getValue();
            result.accumulateNormal(
                TopologicalVoxelGrid.unpackX(key),
                TopologicalVoxelGrid.unpackY(key),
                TopologicalVoxelGrid.unpackZ(key),
                new Vector3(n[0], n[1], n[2])
            );
        }
        result.normalizeNormals();

        VoxelGrid filled = fillAxisAlignedGaps(result, 2);
        filled.normalizeNormals();
        return filled;
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
        boolean hasNormals = grid.hasNormals();
        for (int pass = 0; pass < passes; pass++) {
            VoxelGrid next = new VoxelGrid(w, h, d);
            boolean anyFilled = false;
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < d; z++) {
                        boolean fill;
                        if (current.isFilled(x, y, z)) {
                            fill = true;
                        } else if (
                            (current.isFilled(x - 1, y, z) && current.isFilled(x + 1, y, z)) ||
                            (current.isFilled(x, y - 1, z) && current.isFilled(x, y + 1, z)) ||
                            (current.isFilled(x, y, z - 1) && current.isFilled(x, y, z + 1))
                        ) {
                            fill = true;
                            anyFilled = true;
                        } else {
                            fill = false;
                        }
                        if (fill) {
                            next.setFilled(x, y, z, true);
                            if (hasNormals) {
                                Vector3 n = current.getNormal(x, y, z);
                                if (n.length() > 1e-6) {
                                    next.accumulateNormal(x, y, z, n);
                                }
                            }
                        }
                    }
                }
            }
            current = next;
            if (!anyFilled) break;
        }
        return current;
    }

    /** Computes the axis-aligned bounding box of all triangle vertices. */
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

    /** Computes grid-aligned bounds for each axis using the configured voxel sizes. */
    private static AlignedBounds computeAlignedBounds(Bounds bounds, TopologicalVoxelizerConfig config, int resolution, int yResolution) {
        AxisAligned x = alignAxis(bounds.minX, bounds.maxX, config.voxelSizeX(), resolution);
        AxisAligned y = alignAxis(bounds.minY, bounds.maxY, config.voxelSizeY(), yResolution);
        AxisAligned z = alignAxis(bounds.minZ, bounds.maxZ, config.voxelSizeZ(), resolution);
        return new AlignedBounds(x.min, y.min, z.min, x.max, y.max, z.max);
    }

    /** Aligns a single axis range to grid cells of the given size. */
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

    /** Maps a triangle's bounding box to a range of candidate voxel indices. */
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

    /** Clamps an integer value to the range [min, max]. */
    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Raw min/max bounds on each axis. */
    private static final class Bounds {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    /** Grid-aligned range for a single axis: origin, cell size, and cell count. */
    private static final class AxisAligned {
        final double min, max;

        AxisAligned(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    /** Combined grid-aligned bounds for all three axes. */
    private static final class AlignedBounds {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        AlignedBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
    }

    /** Sparse voxel grid backed by a HashSet of packed coordinates. */
    private static final class TopologicalVoxelGrid {
        private final int width, height, depth;
        private final Set<Long> surface = ConcurrentHashMap.newKeySet();

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

        /** Packs three coordinates into a single long key (21 bits each). */
        private static long pack(int x, int y, int z) {
            return ((long) x << 42) | ((long) y << 21) | (long) z;
        }

        /** Extracts the X coordinate from a packed key. */
        private static int unpackX(long key) { return (int) (key >>> 42); }
        /** Extracts the Y coordinate from a packed key. */
        private static int unpackY(long key) { return (int) ((key >>> 21) & 0x1FFFFF); }
        /** Extracts the Z coordinate from a packed key. */
        private static int unpackZ(long key) { return (int) (key & 0x1FFFFF); }
    }
}
