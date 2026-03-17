package com.lego.color;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.color.LegoPaletteMapper.PaletteEntry;
import com.lego.color.ShadowRemover.LightnessStats;
import com.lego.mesh.TexturedTriangle;
import com.lego.model.Brick;
import com.lego.model.ColorMath;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.VoxelKey;
import com.lego.voxel.VoxelGrid;

/**
 * BVH-accelerated supersampled voxel color pipeline.
 *
 * Algorithm
 * 
 *   - Build a BVH from the normalized mesh triangles.
 *   - For each surface voxel, generate stratified sample points inside
 *       the voxel cube.
 *   - For each sample point, find the nearest surface point on the mesh
 *       via the BVH. This yields barycentric coordinates on a triangle.
 *   - Use the barycentrics to interpolate UV coordinates, then bilinearly
 *       sample the base-color texture. If the triangle has vertex colors,
 *       interpolate those instead.
 *   - Average all valid samples in linear RGB (gamma-correct averaging).
 *   - Convert the average to CIE L*a*b* and find the nearest LEGO
 *       palette entry using CIEDE2000.
 *   - Each voxel independently votes for a palette color; per-brick
 *       majority vote determines the final assignment.
 * 
 *
 * This pipeline reads texture pixels directly via BVH + barycentric
 * interpolation. After sampling, it applies shadow lifting and chroma
 * stabilization (from UVLabPaletteProjection) to compensate for baked
 * lighting in GLB textures before palette matching.
 */
public final class SupersampledVoxelColorPipeline implements ColorStrategy {

    static final int DEFAULT_SAMPLES_PER_VOXEL = 64;

    @Override
    /** Returns the strategy name ("supersampled"). */
    public String name() {
        return "supersampled";
    }

    @Override
    /** Returns a human-readable description of this strategy. */
    public String description() {
        return "BVH-accelerated supersampled texture sampling with bilinear "
             + "interpolation and CIEDE2000 palette matching";
    }

    /**
     * Fallback for the standard ColorStrategy interface (receives pre-averaged
     * brick colors). Uses CIEDE2000 nearest-match without preprocessing.
     */
    @Override
    public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors,
                                      LegoPaletteMapper palette) {
        List<PaletteEntry> entries = palette.opaqueEntries();
        Map<Brick, Integer> result = new HashMap<>(brickColors.size());
        for (Map.Entry<Brick, ColorRgb> e : brickColors.entrySet()) {
            ColorRgb rgb = e.getValue();
            double[] lab = LegoPaletteMapper.linearRgbToLab(rgb.r(), rgb.g(), rgb.b());
            result.put(e.getKey(), Ciede2000.nearestPaletteEntry(lab[0], lab[1], lab[2], entries, KL));
        }
        return result;
    }

    // ------------------------------------------------------------------ //
    //  Full supersampled pipeline
    // ------------------------------------------------------------------ //

    /**
     * Runs the full supersampled color pipeline.
     *
     * @param normalizedMesh      mesh in voxel space [0, resolution]
     * @param texturedTriangles   parallel to normalizedMesh.triangles()
     * @param surface             filled surface voxel grid
     * @param bricks              placed bricks
     * @param resolution          voxel grid resolution
     * @param palette             LEGO palette for matching
     * @param samplesPerVoxel     number of stratified samples per voxel (cubed root → 3D grid)
     * @return map from brick to LDraw color code
     */
    public Map<Brick, Integer> colorize(
            Mesh normalizedMesh,
            List<TexturedTriangle> texturedTriangles,
            VoxelGrid surface,
            List<Brick> bricks,
            int resolution,
            LegoPaletteMapper palette,
            int samplesPerVoxel) {
        return colorize(normalizedMesh, texturedTriangles, surface, bricks,
                resolution, palette, samplesPerVoxel, false);
    }

    /**
     * Runs the full supersampled color pipeline with optional shadow removal bypass.
     */
    public Map<Brick, Integer> colorize(
            Mesh normalizedMesh,
            List<TexturedTriangle> texturedTriangles,
            VoxelGrid surface,
            List<Brick> bricks,
            int resolution,
            LegoPaletteMapper palette,
            int samplesPerVoxel,
            boolean skipShadowRemoval) {

        // 1. Build BVH from normalized mesh
        TriangleBVH bvh = TriangleBVH.build(normalizedMesh.triangles());
        List<PaletteEntry> entries = palette.opaqueEntries();

        // 2. Build voxel → brick spatial index
        Map<Long, Brick> voxelToBrick = new HashMap<>();
        for (Brick brick : bricks) {
            for (int bx = brick.x(); bx < brick.maxX(); bx++) {
                for (int by = brick.y(); by < brick.maxY(); by++) {
                    for (int bz = brick.z(); bz < brick.maxZ(); bz++) {
                        voxelToBrick.put(VoxelKey.pack(bx, by, bz), brick);
                    }
                }
            }
        }

        // 3. Compute stratified sample offsets once (reused for every voxel)
        double[][] sampleOffsets = stratifiedOffsets(samplesPerVoxel);

        // 4. For each surface voxel: supersample → collect per-sample L*a*b*
        // Each sample is independently converted to L*a*b* for per-sample voting.
        // This preserves sub-voxel features (eyes, nose) that would be destroyed
        // by averaging samples in RGB before palette matching.
        /** Associates a brick with a Lab color sample for voting. */
        record SampleLab(Brick brick, double[] lab) {}
        java.util.List<SampleLab> allSamples = new java.util.ArrayList<>();
        java.util.List<Double> allL = new java.util.ArrayList<>();

        int yResolution = surface.height();
        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < yResolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    if (!surface.isFilled(x, y, z)) continue;

                    Brick brick = voxelToBrick.get(VoxelKey.pack(x, y, z));
                    if (brick == null) continue;

                    for (double[] offset : sampleOffsets) {
                        double sx = x + offset[0];
                        // Map plate-voxel Y back to mesh space (1 plate = 1/3 brick)
                        double sy = (y + offset[1]) / 3.0;
                        double sz = z + offset[2];

                        TriangleBVH.Hit hit = bvh.nearestSurfacePoint(sx, sy, sz);
                        if (hit == null) continue;

                        ColorRgb color = sampleColorAtHit(hit, texturedTriangles);
                        if (color == null) continue;

                        double[] lab = LegoPaletteMapper.linearRgbToLab(
                            color.r(), color.g(), color.b());
                        allSamples.add(new SampleLab(brick, lab));
                        allL.add(lab[0]);
                    }
                }
            }
        }

        // 5. Shadow lifting + chrominance normalization + chroma stabilization
        if (!skipShadowRemoval) {
            LightnessStats stats = ShadowRemover.computeLightnessStats(allL);
            java.util.List<double[]> allLabArrays = new java.util.ArrayList<>(allSamples.size());
            for (SampleLab sl : allSamples) {
                allLabArrays.add(sl.lab);
            }
            ShadowRemover.ChrominanceStats chromStats =
                    ShadowRemover.computeChrominanceStats(allLabArrays);

            for (SampleLab sl : allSamples) {
                double originalL = sl.lab[0];
                if (stats != null) {
                    sl.lab[0] = ShadowRemover.normalizeLightness(sl.lab[0], stats);
                }
                ShadowRemover.normalizeChrominance(sl.lab, originalL, stats, chromStats);
                ShadowRemover.stabilizeChroma(sl.lab);
            }
        }

        // 6. Per-sample palette match → per-brick majority vote
        // Each sample independently votes for a palette color, preserving
        // sub-voxel detail: an eye voxel with 40 dark + 24 golden samples
        // correctly votes Black instead of averaging to muddy brown.
        Map<Brick, Map<Integer, Integer>> brickVotes = new HashMap<>();
        for (SampleLab sl : allSamples) {
            int code = Ciede2000.nearestPaletteEntry(sl.lab[0], sl.lab[1], sl.lab[2], entries, KL);
            brickVotes.computeIfAbsent(sl.brick, k -> new HashMap<>())
                      .merge(code, 1, Integer::sum);
        }

        // 7. Majority vote per brick
        Map<Brick, Integer> result = new HashMap<>(brickVotes.size());
        for (Map.Entry<Brick, Map<Integer, Integer>> entry : brickVotes.entrySet()) {
            int bestCode = -1;
            int bestCount = 0;
            for (Map.Entry<Integer, Integer> vote : entry.getValue().entrySet()) {
                if (vote.getValue() > bestCount) {
                    bestCount = vote.getValue();
                    bestCode = vote.getKey();
                }
            }
            if (bestCode >= 0) {
                result.put(entry.getKey(), bestCode);
            }
        }

        return result;
    }

    // ------------------------------------------------------------------ //
    //  Stratified sampling
    // ------------------------------------------------------------------ //

    /**
     * Pre-computes stratified sample offsets within a unit cube [0,1]³.
     * Divides the cube into a perAxis × perAxis × perAxis grid and places
     * one sample at the center of each subcell.
     */
    private static double[][] stratifiedOffsets(int totalSamples) {
        int perAxis = Math.max(1, (int) Math.round(Math.cbrt(totalSamples)));
        int actual = perAxis * perAxis * perAxis;
        double[][] offsets = new double[actual][3];
        double cell = 1.0 / perAxis;
        int idx = 0;
        for (int sx = 0; sx < perAxis; sx++) {
            for (int sy = 0; sy < perAxis; sy++) {
                for (int sz = 0; sz < perAxis; sz++) {
                    offsets[idx][0] = (sx + 0.5) * cell;
                    offsets[idx][1] = (sy + 0.5) * cell;
                    offsets[idx][2] = (sz + 0.5) * cell;
                    idx++;
                }
            }
        }
        return offsets;
    }

    // ------------------------------------------------------------------ //
    //  Color sampling at a BVH hit
    // ------------------------------------------------------------------ //

    /**
     * Samples the color at a BVH hit point using the triangle's texture data.
     *
     * Priority: vertex colors → texture × materialColor → materialColor.
     */
    private static ColorRgb sampleColorAtHit(TriangleBVH.Hit hit,
                                              List<TexturedTriangle> texTris) {
        if (hit.triangleIndex() < 0 || hit.triangleIndex() >= texTris.size()) {
            return null;
        }
        TexturedTriangle tt = texTris.get(hit.triangleIndex());

        // 1. Per-vertex color interpolation
        if (tt.hasVertexColors()) {
            return tt.interpolateVertexColor(hit.bary0(), hit.bary1(), hit.bary2());
        }

        // 2. Texture sampling with barycentric UV interpolation
        if (tt.hasTexture()) {
            float[] uv = tt.interpolateUV(hit.bary0(), hit.bary1(), hit.bary2());
            ColorRgb texColor = sampleTextureBilinear(tt.texture(), uv[0], uv[1]);
            if (texColor != null && tt.materialColor() != null) {
                return new ColorRgb(
                    ColorMath.clamp01(texColor.r() * tt.materialColor().r()),
                    ColorMath.clamp01(texColor.g() * tt.materialColor().g()),
                    ColorMath.clamp01(texColor.b() * tt.materialColor().b())
                );
            }
            return texColor;
        }

        // 3. Material color fallback
        return tt.materialColor();
    }

    // ------------------------------------------------------------------ //
    //  Bilinear texture sampling (sRGB → linear)
    // ------------------------------------------------------------------ //

    /**
     * Bilinear texture sample at the given UV coordinate.
     * UV is wrapped to [0,1) and V is flipped (glTF convention).
     * Pixel values are converted from sRGB to linear RGB.
     */
    private static ColorRgb sampleTextureBilinear(BufferedImage image,
                                                   float u, float v) {
        // Wrap UV
        u = u - (float) Math.floor(u);
        v = v - (float) Math.floor(v);

        // Flip V: glTF V=0 is bottom, image Y=0 is top
        v = 1.0f - v;

        int w = image.getWidth();
        int h = image.getHeight();

        // Continuous pixel coordinates (centered on texel)
        float fx = u * w - 0.5f;
        float fy = v * h - 0.5f;

        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float fracX = fx - x0;
        float fracY = fy - y0;

        // Wrap pixel coordinates
        int x1 = wrapPixel(x0 + 1, w);
        int y1 = wrapPixel(y0 + 1, h);
        x0 = wrapPixel(x0, w);
        y0 = wrapPixel(y0, h);

        // Read 4 neighbor pixels in linear RGB
        float[] c00 = readPixelLinear(image, x0, y0);
        float[] c10 = readPixelLinear(image, x1, y0);
        float[] c01 = readPixelLinear(image, x0, y1);
        float[] c11 = readPixelLinear(image, x1, y1);

        // Bilinear interpolation in linear space
        float r = lerp(lerp(c00[0], c10[0], fracX), lerp(c01[0], c11[0], fracX), fracY);
        float g = lerp(lerp(c00[1], c10[1], fracX), lerp(c01[1], c11[1], fracX), fracY);
        float b = lerp(lerp(c00[2], c10[2], fracX), lerp(c01[2], c11[2], fracX), fracY);

        return new ColorRgb(ColorMath.clamp01(r), ColorMath.clamp01(g), ColorMath.clamp01(b));
    }

    /** Wraps a pixel coordinate to stay within texture bounds. */
    private static int wrapPixel(int p, int size) {
        p = p % size;
        return p < 0 ? p + size : p;
    }

    /** Reads a pixel from the image and converts sRGB to linear RGB. */
    private static float[] readPixelLinear(BufferedImage image, int x, int y) {
        int argb = image.getRGB(x, y);
        float sR = ((argb >> 16) & 0xFF) / 255f;
        float sG = ((argb >>  8) & 0xFF) / 255f;
        float sB = ( argb        & 0xFF) / 255f;
        return new float[] {
            (float) ColorMath.srgbToLinear(sR),
            (float) ColorMath.srgbToLinear(sG),
            (float) ColorMath.srgbToLinear(sB)
        };
    }

    /** Linearly interpolates between two float values. */
    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    // ------------------------------------------------------------------ //
    //  Utilities
    // ------------------------------------------------------------------ //

    private static final double KL = 1.5;
}
