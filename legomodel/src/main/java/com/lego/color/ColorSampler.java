package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.voxel.VoxelGrid;

/**
 * Maps per-triangle color data through the voxel grid onto bricks.
 *
 * <p>For each filled voxel, determines which triangles from the normalized mesh
 * overlap it (using the same SAT-based overlap test as the voxelizer), then
 * applies a majority-vote rule to assign a single color. Per-brick color is then
 * determined by majority-vote across the brick's constituent voxels.
 *
 * <p>Color is remapped from pre-normalization triangle keys to post-normalization
 * triangle keys by index position (triangle order is preserved by
 * {@code MeshNormalizer}).
 */
public final class ColorSampler {

    private ColorSampler() {}

    /**
     * Determines per-brick color from triangle color data.
     *
     * @param originalMesh    mesh before normalization (triangle keys match {@code colorMap})
     * @param normalizedMesh  mesh after normalization (same triangle count and order)
     * @param colorMap        triangle → color from the loader
     * @param surface         filled surface voxel grid
     * @param bricks          placed bricks
     * @param resolution      voxel grid resolution
     * @return map from brick to its majority-vote RGB color
     */
    public static Map<Brick, ColorRgb> sampleBrickColors(
        Mesh originalMesh,
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        List<Brick> bricks,
        int resolution
    ) {
        // Remap color from original triangles to normalized triangles (by index)
        Map<Triangle, ColorRgb> normalizedColorMap = remapColors(
            originalMesh, normalizedMesh, colorMap
        );

        // Step 1: For each filled voxel, find majority color from overlapping triangles
        ColorRgb[][][] voxelColors = sampleVoxelColors(normalizedMesh, normalizedColorMap, surface, resolution);

        // Step 2: For each brick, majority-vote across its voxels
        Map<Brick, ColorRgb> brickColors = new HashMap<>();
        for (Brick brick : bricks) {
            ColorRgb brickColor = majorityVoteBrick(brick, voxelColors, surface);
            if (brickColor != null) {
                brickColors.put(brick, brickColor);
            }
        }

        return brickColors;
    }

    /**
     * Transfers color entries from pre-normalization to post-normalization triangles
     * using index position (triangle order is preserved by MeshNormalizer).
     */
    static Map<Triangle, ColorRgb> remapColors(
        Mesh original, Mesh normalized, Map<Triangle, ColorRgb> colorMap
    ) {
        Map<Triangle, ColorRgb> result = new HashMap<>();
        List<Triangle> origTris = original.triangles();
        List<Triangle> normTris = normalized.triangles();
        for (int i = 0; i < origTris.size(); i++) {
            ColorRgb color = colorMap.get(origTris.get(i));
            if (color != null) {
                result.put(normTris.get(i), color);
            }
        }
        return result;
    }

    /**
     * For each filled voxel, finds the majority color from overlapping triangles.
     */
    private static ColorRgb[][][] sampleVoxelColors(
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        int resolution
    ) {
        int w = surface.width();
        int h = surface.height();
        int d = surface.depth();
        Map<Long, List<ColorRgb>> voxelVotes = new HashMap<>();

        for (Triangle tri : normalizedMesh.triangles()) {
            ColorRgb color = colorMap.get(tri);
            if (color == null) continue;

            // Find candidate voxel range for this triangle
            double triMinX = Math.min(tri.v1().x(), Math.min(tri.v2().x(), tri.v3().x()));
            double triMaxX = Math.max(tri.v1().x(), Math.max(tri.v2().x(), tri.v3().x()));
            double triMinY = Math.min(tri.v1().y(), Math.min(tri.v2().y(), tri.v3().y()));
            double triMaxY = Math.max(tri.v1().y(), Math.max(tri.v2().y(), tri.v3().y()));
            double triMinZ = Math.min(tri.v1().z(), Math.min(tri.v2().z(), tri.v3().z()));
            double triMaxZ = Math.max(tri.v1().z(), Math.max(tri.v2().z(), tri.v3().z()));

            int iMin = clamp((int) Math.floor(triMinX) - 1, 0, resolution - 1);
            int iMax = clamp((int) Math.ceil(triMaxX) + 1, 0, resolution - 1);
            int jMin = clamp((int) Math.floor(triMinY) - 1, 0, resolution - 1);
            int jMax = clamp((int) Math.ceil(triMaxY) + 1, 0, resolution - 1);
            int kMin = clamp((int) Math.floor(triMinZ) - 1, 0, resolution - 1);
            int kMax = clamp((int) Math.ceil(triMaxZ) + 1, 0, resolution - 1);

            for (int i = iMin; i <= iMax; i++) {
                for (int j = jMin; j <= jMax; j++) {
                    for (int k = kMin; k <= kMax; k++) {
                        if (!surface.isFilled(i, j, k)) continue;

                        double cx = i + 0.5;
                        double cy = j + 0.5;
                        double cz = k + 0.5;

                        if (triangleOverlapsVoxel(tri, cx, cy, cz, 0.5, 0.5, 0.5)) {
                            long key = pack(i, j, k);
                            voxelVotes.computeIfAbsent(key, x -> new ArrayList<>()).add(color);
                        }
                    }
                }
            }
        }

        // Resolve majority color per voxel
        ColorRgb[][][] result = new ColorRgb[w][h][d];
        for (Map.Entry<Long, List<ColorRgb>> entry : voxelVotes.entrySet()) {
            long key = entry.getKey();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            result[x][y][z] = majorityColor(entry.getValue());
        }
        return result;
    }

    /**
     * Returns the most common color in the list. On ties, picks the first color
     * that reached the maximum count (deterministic for a given triangle order).
     */
    private static ColorRgb majorityColor(List<ColorRgb> votes) {
        if (votes.isEmpty()) return null;
        if (votes.size() == 1) return votes.get(0);

        Map<ColorRgb, Integer> counts = new HashMap<>();
        for (ColorRgb c : votes) {
            counts.merge(c, 1, Integer::sum);
        }

        ColorRgb best = null;
        int bestCount = 0;
        // Iterate in insertion order over votes to break ties deterministically
        for (ColorRgb c : votes) {
            int count = counts.get(c);
            if (count > bestCount) {
                bestCount = count;
                best = c;
            }
        }
        return best;
    }

    /**
     * Majority-vote color across all voxels covered by a brick.
     */
    private static ColorRgb majorityVoteBrick(
        Brick brick, ColorRgb[][][] voxelColors, VoxelGrid surface
    ) {
        List<ColorRgb> votes = new ArrayList<>();
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    if (x < voxelColors.length && y < voxelColors[0].length && z < voxelColors[0][0].length) {
                        ColorRgb c = voxelColors[x][y][z];
                        if (c != null) {
                            votes.add(c);
                        }
                    }
                }
            }
        }
        return majorityColor(votes);
    }

    // ---- SAT triangle-AABB overlap (same algorithm as TopologicalVoxelizer) ----

    private static boolean triangleOverlapsVoxel(
        Triangle tri,
        double cx, double cy, double cz,
        double hx, double hy, double hz
    ) {
        double v0x = tri.v1().x() - cx, v0y = tri.v1().y() - cy, v0z = tri.v1().z() - cz;
        double v1x = tri.v2().x() - cx, v1y = tri.v2().y() - cy, v1z = tri.v2().z() - cz;
        double v2x = tri.v3().x() - cx, v2y = tri.v3().y() - cy, v2z = tri.v3().z() - cz;

        if (separating(v0x, v1x, v2x, hx)) return false;
        if (separating(v0y, v1y, v2y, hy)) return false;
        if (separating(v0z, v1z, v2z, hz)) return false;

        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        double e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        double nx = e0y * (v2z - v0z) - e0z * (v2y - v0y);
        double ny = e0z * (v2x - v0x) - e0x * (v2z - v0z);
        double nz = e0x * (v2y - v0y) - e0y * (v2x - v0x);
        double d = nx * v0x + ny * v0y + nz * v0z;
        double rn = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
        if (d > rn || d < -rn) return false;

        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e0y, e0z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e0x, e0z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e0x, e0y, hx, hy)) return false;
        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e1y, e1z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e1x, e1z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e1x, e1y, hx, hy)) return false;
        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e2y, e2z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e2x, e2z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e2x, e2y, hx, hy)) return false;

        return true;
    }

    private static boolean separating(double p0, double p1, double p2, double h) {
        return Math.min(p0, Math.min(p1, p2)) > h || Math.max(p0, Math.max(p1, p2)) < -h;
    }

    private static boolean edgeCrossX(
        double v0y, double v0z, double v1y, double v1z, double v2y, double v2z,
        double ey, double ez, double hy, double hz
    ) {
        double p0 = ez * v0y - ey * v0z;
        double p1 = ez * v1y - ey * v1z;
        double p2 = ez * v2y - ey * v2z;
        double r = hy * Math.abs(ez) + hz * Math.abs(ey);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static boolean edgeCrossY(
        double v0x, double v0z, double v1x, double v1z, double v2x, double v2z,
        double ex, double ez, double hx, double hz
    ) {
        double p0 = -ez * v0x + ex * v0z;
        double p1 = -ez * v1x + ex * v1z;
        double p2 = -ez * v2x + ex * v2z;
        double r = hx * Math.abs(ez) + hz * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static boolean edgeCrossZ(
        double v0x, double v0y, double v1x, double v1y, double v2x, double v2y,
        double ex, double ey, double hx, double hy
    ) {
        double p0 = ey * v0x - ex * v0y;
        double p1 = ey * v1x - ex * v1y;
        double p2 = ey * v2x - ex * v2y;
        double r = hx * Math.abs(ey) + hy * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static long pack(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | (long) z;
    }

    private static int unpackX(long key) { return (int) (key >>> 42); }
    private static int unpackY(long key) { return (int) ((key >>> 21) & 0x1FFFFF); }
    private static int unpackZ(long key) { return (int) (key & 0x1FFFFF); }
}
