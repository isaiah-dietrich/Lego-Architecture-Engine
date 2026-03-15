package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.TriangleAabbTest;
import com.lego.model.VoxelKey;
import com.lego.voxel.VoxelGrid;

/**
 * Maps per-triangle color data through the voxel grid onto bricks.
 *
 * <p>For each filled voxel, determines which triangles from the normalized mesh
 * overlap it (using the same SAT-based overlap test as the voxelizer), then
 * computes an area-weighted average of their colors in linear RGB to assign a
 * single color. Per-brick color is then determined by averaging across the
 * brick's constituent voxels.
 *
 * <p>Area weighting ensures that large triangles (e.g., flat body panels)
 * contribute proportionally more than small triangles from detailed/dense
 * geometry areas. Without it, detailed regions with many small triangles
 * would dominate the color simply by having more triangle overlaps per voxel.
 *
 * <p>Color is remapped from pre-normalization triangle keys to post-normalization
 * triangle keys by index position (triangle order is preserved by
 * {@code MeshNormalizer}).
 */
public final class ColorSampler {

    private ColorSampler() {}

    /** A color sample paired with its triangle's area weight. */
    private record WeightedColor(ColorRgb color, double weight) {}

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
        ColorRgb[][][] voxelColors = sampleVoxelColorGrid(originalMesh, normalizedMesh, colorMap, surface, resolution);

        // Step 2: For each brick, average color across its voxels
        Map<Brick, ColorRgb> brickColors = new HashMap<>();
        for (Brick brick : bricks) {
            ColorRgb brickColor = averageBrickColor(brick, voxelColors, surface);
            if (brickColor != null) {
                brickColors.put(brick, brickColor);
            }
        }

        return brickColors;
    }

    /**
     * Samples per-voxel colors from triangle data.
     *
     * <p>For each filled voxel, computes an area-weighted average color from
     * overlapping triangles. This can be called independently of brick placement
     * to provide color data for color-aware placement policies.</p>
     *
     * @param originalMesh    mesh before normalization (triangle keys match {@code colorMap})
     * @param normalizedMesh  mesh after normalization (same triangle count and order)
     * @param colorMap        triangle → color from the loader
     * @param surface         filled surface voxel grid
     * @param resolution      voxel grid resolution
     * @return 3D array of per-voxel colors (null where no color data)
     */
    public static ColorRgb[][][] sampleVoxelColorGrid(
        Mesh originalMesh,
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        int resolution
    ) {
        Map<Triangle, ColorRgb> normalizedColorMap = remapColors(
            originalMesh, normalizedMesh, colorMap
        );
        return sampleVoxelColors(normalizedMesh, normalizedColorMap, surface, resolution);
    }

    /**
     * Returns per-voxel colors for each brick without any brick-level averaging.
     *
     * <p>At the voxel level, each voxel gets the color of the triangle with the
     * highest area overlap (dominant color), rather than an area-weighted average.
     * This preserves sharp color boundaries that would be lost by averaging.
     *
     * @return map from brick to its list of per-voxel colors (one per filled voxel)
     */
    public static Map<Brick, List<ColorRgb>> sampleBrickVoxelColors(
        Mesh originalMesh,
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        List<Brick> bricks,
        int resolution
    ) {
        Map<Triangle, ColorRgb> normalizedColorMap = remapColors(
            originalMesh, normalizedMesh, colorMap
        );

        // For each voxel, pick the dominant (highest-area) triangle color
        ColorRgb[][][] voxelColors = sampleVoxelColorsDominant(normalizedMesh, normalizedColorMap, surface, resolution);

        // Collect per-voxel colors for each brick (no averaging)
        Map<Brick, List<ColorRgb>> result = new HashMap<>();
        for (Brick brick : bricks) {
            List<ColorRgb> colors = new ArrayList<>();
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    for (int z = brick.z(); z < brick.maxZ(); z++) {
                        if (x < voxelColors.length && y < voxelColors[0].length && z < voxelColors[0][0].length) {
                            ColorRgb c = voxelColors[x][y][z];
                            if (c != null) {
                                colors.add(c);
                            }
                        }
                    }
                }
            }
            if (!colors.isEmpty()) {
                result.put(brick, colors);
            }
        }
        return result;
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
     * For each filled voxel, computes area-weighted average color from overlapping triangles.
     */
    private static ColorRgb[][][] sampleVoxelColors(
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        int resolution
    ) {
        Map<Long, List<WeightedColor>> voxelVotes = collectVoxelTriangleOverlaps(normalizedMesh, colorMap, surface, resolution);

        // Area-weighted average color per voxel
        ColorRgb[][][] result = new ColorRgb[surface.width()][surface.height()][surface.depth()];
        for (Map.Entry<Long, List<WeightedColor>> entry : voxelVotes.entrySet()) {
            long key = entry.getKey();
            result[VoxelKey.unpackX(key)][VoxelKey.unpackY(key)][VoxelKey.unpackZ(key)] = weightedAverageColor(entry.getValue());
        }
        return result;
    }

    /**
     * For each filled voxel, picks the color of the triangle with the highest area overlap.
     * This preserves sharp color boundaries that averaging would blur.
     */
    private static ColorRgb[][][] sampleVoxelColorsDominant(
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        int resolution
    ) {
        Map<Long, List<WeightedColor>> voxelVotes = collectVoxelTriangleOverlaps(normalizedMesh, colorMap, surface, resolution);

        // Pick the highest-area triangle color per voxel
        ColorRgb[][][] result = new ColorRgb[surface.width()][surface.height()][surface.depth()];
        for (Map.Entry<Long, List<WeightedColor>> entry : voxelVotes.entrySet()) {
            long key = entry.getKey();
            List<WeightedColor> samples = entry.getValue();
            WeightedColor best = samples.get(0);
            for (int i = 1; i < samples.size(); i++) {
                if (samples.get(i).weight() > best.weight()) {
                    best = samples.get(i);
                }
            }
            result[VoxelKey.unpackX(key)][VoxelKey.unpackY(key)][VoxelKey.unpackZ(key)] = best.color();
        }
        return result;
    }

    /**
     * Collects all triangle-voxel overlaps with area weights.
     * Shared by both averaging and dominant-vote voxel sampling.
     */
    private static Map<Long, List<WeightedColor>> collectVoxelTriangleOverlaps(
        Mesh normalizedMesh,
        Map<Triangle, ColorRgb> colorMap,
        VoxelGrid surface,
        int resolution
    ) {
        Map<Long, List<WeightedColor>> voxelVotes = new HashMap<>();

        for (Triangle tri : normalizedMesh.triangles()) {
            ColorRgb color = colorMap.get(tri);
            if (color == null) continue;

            double area = triangleArea(tri);
            if (area <= 0) continue;

            // Find candidate voxel range for this triangle
            double triMinX = Math.min(tri.v1().x(), Math.min(tri.v2().x(), tri.v3().x()));
            double triMaxX = Math.max(tri.v1().x(), Math.max(tri.v2().x(), tri.v3().x()));
            double triMinY = Math.min(tri.v1().y(), Math.min(tri.v2().y(), tri.v3().y()));
            double triMaxY = Math.max(tri.v1().y(), Math.max(tri.v2().y(), tri.v3().y()));
            double triMinZ = Math.min(tri.v1().z(), Math.min(tri.v2().z(), tri.v3().z()));
            double triMaxZ = Math.max(tri.v1().z(), Math.max(tri.v2().z(), tri.v3().z()));

            int iMin = clamp((int) Math.floor(triMinX) - 1, 0, resolution - 1);
            int iMax = clamp((int) Math.ceil(triMaxX) + 1, 0, resolution - 1);
            // Y voxels are 1/3 the mesh-unit height (one plate per voxel layer).
            // Map mesh-Y range to voxel-Y range by multiplying by 3.
            int yResolution = surface.height();
            int jMin = clamp((int) Math.floor(triMinY * 3) - 1, 0, yResolution - 1);
            int jMax = clamp((int) Math.ceil(triMaxY * 3) + 1, 0, yResolution - 1);
            int kMin = clamp((int) Math.floor(triMinZ) - 1, 0, resolution - 1);
            int kMax = clamp((int) Math.ceil(triMaxZ) + 1, 0, resolution - 1);

            WeightedColor wc = new WeightedColor(color, area);

            for (int i = iMin; i <= iMax; i++) {
                for (int j = jMin; j <= jMax; j++) {
                    for (int k = kMin; k <= kMax; k++) {
                        if (!surface.isFilled(i, j, k)) continue;

                        double cx = i + 0.5;
                        // Voxel j maps to mesh-Y via meshY = (j + 0.5) / 3.0
                        double cy = (j + 0.5) / 3.0;
                        double cz = k + 0.5;

                        if (TriangleAabbTest.overlaps(tri, cx, cy, cz, 0.5, 1.0 / 6.0, 0.5)) {
                            long key = VoxelKey.pack(i, j, k);
                            voxelVotes.computeIfAbsent(key, x -> new ArrayList<>()).add(wc);
                        }
                    }
                }
            }
        }

        return voxelVotes;
    }

    /**
     * Computes the area of a triangle using the cross-product formula.
     */
    private static double triangleArea(Triangle tri) {
        double ex1 = tri.v2().x() - tri.v1().x();
        double ey1 = tri.v2().y() - tri.v1().y();
        double ez1 = tri.v2().z() - tri.v1().z();
        double ex2 = tri.v3().x() - tri.v1().x();
        double ey2 = tri.v3().y() - tri.v1().y();
        double ez2 = tri.v3().z() - tri.v1().z();
        double cx = ey1 * ez2 - ez1 * ey2;
        double cy = ez1 * ex2 - ex1 * ez2;
        double cz = ex1 * ey2 - ey1 * ex2;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /**
     * Computes area-weighted average color.
     */
    private static ColorRgb weightedAverageColor(List<WeightedColor> samples) {
        if (samples.isEmpty()) return null;
        if (samples.size() == 1) return samples.get(0).color();

        double rSum = 0, gSum = 0, bSum = 0, wSum = 0;
        for (WeightedColor wc : samples) {
            double w = wc.weight();
            rSum += wc.color().r() * w;
            gSum += wc.color().g() * w;
            bSum += wc.color().b() * w;
            wSum += w;
        }
        return new ColorRgb(
            (float) Math.min(1.0, rSum / wSum),
            (float) Math.min(1.0, gSum / wSum),
            (float) Math.min(1.0, bSum / wSum)
        );
    }

    /**
     * Averages the colors in the list (in linear RGB space).
     * Returns null if the list is empty.
     */
    private static ColorRgb averageColor(List<ColorRgb> colors) {
        if (colors.isEmpty()) return null;
        if (colors.size() == 1) return colors.get(0);

        float rSum = 0, gSum = 0, bSum = 0;
        for (ColorRgb c : colors) {
            rSum += c.r();
            gSum += c.g();
            bSum += c.b();
        }
        int n = colors.size();
        return new ColorRgb(
            Math.min(1f, rSum / n),
            Math.min(1f, gSum / n),
            Math.min(1f, bSum / n)
        );
    }

    /**
     * Averages color across all voxels covered by a brick.
     */
    private static ColorRgb averageBrickColor(
        Brick brick, ColorRgb[][][] voxelColors, VoxelGrid surface
    ) {
        float rSum = 0, gSum = 0, bSum = 0;
        int count = 0;
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    if (x < voxelColors.length && y < voxelColors[0].length && z < voxelColors[0][0].length) {
                        ColorRgb c = voxelColors[x][y][z];
                        if (c != null) {
                            rSum += c.r();
                            gSum += c.g();
                            bSum += c.b();
                            count++;
                        }
                    }
                }
            }
        }
        if (count == 0) return null;
        return new ColorRgb(
            Math.min(1f, rSum / count),
            Math.min(1f, gSum / count),
            Math.min(1f, bSum / count)
        );
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
