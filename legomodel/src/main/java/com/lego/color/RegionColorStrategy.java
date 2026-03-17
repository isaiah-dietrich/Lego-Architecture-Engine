package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.lego.color.LegoPaletteMapper.PaletteEntry;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;

/**
 * Region-based color strategy inspired by how real LEGO set designers work.
 *
 * Real LEGO models never include shadows, lighting gradients, or subtle color
 * variation within a single logical region. A yellow dog body is uniformly
 * "Bright Light Yellow" — not five shades of yellow from baked lighting. Color
 * variety is reserved for genuinely distinct features: black nose, white chest,
 * dark tan ears.
 *
 * Algorithm:
 *
 *   1. Convert all brick colors to CIE L*a*b*.
 *   2. Build a spatial adjacency graph (bricks touching in X/Z on the same
 *      Y layer, or stacked vertically).
 *   3. Flood-fill to segment bricks into contiguous regions using a
 *      hue-weighted merge criterion: two adjacent bricks merge if their
 *      hue and chroma are similar, even if lightness differs (since
 *      lightness variation is typically shadow/lighting noise).
 *   4. For each region, map every brick's Lab to a palette code via CIEDE2000,
 *      then take a majority vote. All bricks in the region get that single
 *      color — this kills shadow noise by design.
 *   5. Exception: regions smaller than the detail threshold are treated as
 *      fine detail (eyes, nose, markings). These retain per-brick palette
 *      matching to preserve sharpness.
 *
 * The merge threshold controls how aggressively regions consolidate. A higher
 * threshold produces larger uniform regions (fewer distinct colors). The detail
 * threshold controls the brick count below which a region is considered "detail"
 * and allowed per-brick color variation.
 */
public final class RegionColorStrategy implements ColorStrategy {

    /**
     * Maximum hue-weighted distance between adjacent bricks for them to merge
     * into the same region. Uses a custom distance that down-weights lightness
     * differences (shadows) and emphasizes hue/chroma shifts.
     *
     * Set high enough to absorb the a/b color shifts that baked-lighting
     * introduces (warm shadows shift toward red). The seed-anchored flood
     * fill prevents global drift even with a generous threshold.
     */
    static final double MERGE_THRESHOLD = 25.0;

    /**
     * Maximum chrominance (a*, b*) distance between a candidate brick's
     * original (pre-normalization) Lab and the region seed's original Lab
     * for the dual-gate merge criterion.
     *
     * Uses chrominance-only distance (sqrt(Δa² + Δb²)) because in original
     * Lab, lightness differences are always shadow/lighting artifacts while
     * chrominance differences indicate genuinely distinct features.
     *
     * Body shadows shift a* by ~5-12 and b* by ~15-25 from highlights
     * (chrominance distance ~17-28), while eyes/nose differ by 30-40 in b*
     * (chrominance distance ~35-40). Threshold 30 allows extreme warm
     * shadows while still separating genuine features.
     */
    static final double ORIGINAL_CHROMA_THRESHOLD = 35.0;

    /**
     * Minimum ratio of a neighbor's original lightness to the seed's original
     * lightness for them to merge. Prevents dark features (eyes, nostrils,
     * paw pads) in the L*=20–35 range from being absorbed into lighter
     * regions after shadow lifting makes their normalized Lab values similar.
     *
     * A ratio of 0.5 means a brick at L*=25 will not merge into a region
     * seeded at L*=60 (25/60 = 0.42 < 0.5). Body shadows rarely drop below
     * 50% of highlight lightness, while genuinely dark features typically
     * sit at 30-40% of the surrounding region's lightness.
     */
    static final double LIGHTNESS_RATIO_FLOOR = 0.5;

    /**
     * Regions with fewer bricks than this are treated as fine detail and
     * assigned per-brick colors instead of a single uniform color.
     */
    static final int DETAIL_REGION_SIZE = 10;

    /**
     * Lightness weight for CIEDE2000 palette matching. Higher values down-weight
     * lightness differences, emphasizing hue/chroma similarity.
     */
    private static final double KL = 1.5;

    @Override
    /** Returns the strategy name ("region"). */
    public String name() {
        return "region";
    }

    @Override
    /** Returns a human-readable description of this strategy. */
    public String description() {
        return "Region-based uniform coloring — segments bricks into zones, assigns one color per zone, preserves fine detail";
    }

    @Override
    /** Segments bricks into regions and assigns uniform colors per region. */
    public Map<Brick, Integer> apply(Map<Brick, ColorRgb> brickColors, LegoPaletteMapper palette) {
        if (brickColors.isEmpty()) {
            return new HashMap<>();
        }

        List<Brick> bricks = new ArrayList<>(brickColors.keySet());
        List<PaletteEntry> entries = palette.opaqueEntries();

        // Step 1: Convert all brick colors to L*a*b*
        Map<Brick, double[]> brickLab = new HashMap<>(bricks.size());
        for (Brick brick : bricks) {
            ColorRgb rgb = brickColors.get(brick);
            brickLab.put(brick, LegoPaletteMapper.linearRgbToLab(rgb.r(), rgb.g(), rgb.b()));
        }

        // Step 2: Build spatial adjacency graph
        Map<Brick, List<Brick>> adjacency = buildAdjacencyGraph(bricks);

        // Step 3: Flood-fill segmentation using hue-weighted merge criterion
        List<List<Brick>> regions = segmentRegions(bricks, adjacency, brickLab, brickLab);

        // Step 4–5: Assign colors per region
        Map<Brick, Integer> result = new HashMap<>(bricks.size());

        for (List<Brick> region : regions) {
            if (region.size() < DETAIL_REGION_SIZE) {
                // Small region = detail: per-brick CIEDE2000 matching
                for (Brick brick : region) {
                    double[] lab = brickLab.get(brick);
                    result.put(brick, Ciede2000.nearestPaletteEntry(
                            lab[0], lab[1], lab[2], entries, KL));
                }
            } else {
                // Large region: majority vote for a single palette color
                int uniformCode = majorityVote(region, brickLab, entries);
                for (Brick brick : region) {
                    result.put(brick, uniformCode);
                }
            }
        }

        return result;
    }



    /**
     * Full per-voxel pathway: applies aggressive shadow lifting and chroma
     * stabilization to per-voxel colors, then segments bricks into regions
     * and assigns uniform colors via per-voxel majority voting within each
     * region.
     *
     * This pathway avoids the information loss from pre-averaging voxel colors
     * into a single brick color, giving region segmentation and voting access
     * to the full per-voxel color distribution.
     *
     * @param brickVoxelColors map from brick to its per-voxel sampled colors
     * @param palette          the loaded LEGO palette
     * @return map from brick to LDraw color code
     */
    public Map<Brick, Integer> applyWithVoxelColors(
            Map<Brick, List<ColorRgb>> brickVoxelColors, LegoPaletteMapper palette) {
        return applyWithVoxelColors(brickVoxelColors, palette, false);
    }

    /**
     * Full per-voxel pathway with optional shadow removal bypass.
     *
     * @param brickVoxelColors   map from brick to its per-voxel sampled colors
     * @param palette            the loaded LEGO palette
     * @param skipShadowRemoval  true for KHR_materials_unlit models (albedo already clean)
     * @return map from brick to LDraw color code
     */
    public Map<Brick, Integer> applyWithVoxelColors(
            Map<Brick, List<ColorRgb>> brickVoxelColors, LegoPaletteMapper palette,
            boolean skipShadowRemoval) {
        if (brickVoxelColors.isEmpty()) {
            return new HashMap<>();
        }

        List<Brick> bricks = new ArrayList<>(brickVoxelColors.keySet());
        List<PaletteEntry> entries = palette.opaqueEntries();

        // Step 1: Convert all voxel colors to Lab, collect lightness for statistics
        List<Double> allL = new ArrayList<>();
        Map<Brick, List<double[]>> brickVoxelLab = new HashMap<>(brickVoxelColors.size());

        for (Map.Entry<Brick, List<ColorRgb>> entry : brickVoxelColors.entrySet()) {
            List<double[]> labs = new ArrayList<>(entry.getValue().size());
            for (ColorRgb rgb : entry.getValue()) {
                double[] lab = LegoPaletteMapper.linearRgbToLab(rgb.r(), rgb.g(), rgb.b());
                labs.add(lab);
                allL.add(lab[0]);
            }
            brickVoxelLab.put(entry.getKey(), labs);
        }

        // Save original per-brick average Lab before normalization.
        // Used by the detail override post-pass to detect features (eyes, nostrils)
        // that shadow normalization would otherwise absorb into large body regions.
        Map<Brick, double[]> originalBrickLab = new HashMap<>(bricks.size());
        for (Brick brick : bricks) {
            List<double[]> labs = brickVoxelLab.get(brick);
            double lSum = 0, aSum = 0, bSum = 0;
            for (double[] lab : labs) {
                lSum += lab[0];
                aSum += lab[1];
                bSum += lab[2];
            }
            int n = labs.size();
            originalBrickLab.put(brick, new double[]{lSum / n, aSum / n, bSum / n});
        }

        // Step 2: Shadow lifting + hue-aware chrominance normalization + chroma stabilization
        if (!skipShadowRemoval) {
            ShadowRemover.LightnessStats stats =
                    ShadowRemover.computeLightnessStats(allL);
            List<double[]> allLabs = new ArrayList<>();
            for (List<double[]> labs : brickVoxelLab.values()) {
                allLabs.addAll(labs);
            }
            ShadowRemover.ChrominanceStats chromStats =
                    ShadowRemover.computeChrominanceStats(allLabs);

            for (List<double[]> labs : brickVoxelLab.values()) {
                for (double[] lab : labs) {
                    double originalL = lab[0];
                    if (stats != null) {
                        lab[0] = ShadowRemover.normalizeLightnessForRegion(lab[0], stats);
                    }
                    ShadowRemover.normalizeChrominance(lab, originalL, stats, chromStats);
                    ShadowRemover.stabilizeChroma(lab);
                }
            }
        }

        // Step 3: Compute representative Lab per brick (average of normalized voxels)
        Map<Brick, double[]> brickLab = new HashMap<>(bricks.size());
        for (Brick brick : bricks) {
            List<double[]> labs = brickVoxelLab.get(brick);
            double lSum = 0, aSum = 0, bSum = 0;
            for (double[] lab : labs) {
                lSum += lab[0];
                aSum += lab[1];
                bSum += lab[2];
            }
            int n = labs.size();
            brickLab.put(brick, new double[]{lSum / n, aSum / n, bSum / n});
        }

        // Step 4: Build adjacency graph and segment regions (dual-gate)
        Map<Brick, List<Brick>> adjacency = buildAdjacencyGraph(bricks);
        List<List<Brick>> regions = segmentRegions(bricks, adjacency, brickLab, originalBrickLab);

        // Step 5-6: Assign colors per region using per-voxel voting
        Map<Brick, Integer> result = new HashMap<>(bricks.size());
        for (List<Brick> region : regions) {
            if (region.size() < DETAIL_REGION_SIZE) {
                // Small region: per-brick majority vote from voxel data
                for (Brick brick : region) {
                    result.put(brick, voxelMajorityVote(brickVoxelLab.get(brick), entries));
                }
            } else {
                // Large region: majority vote across ALL voxels in the region
                List<double[]> allRegionLabs = new ArrayList<>();
                for (Brick brick : region) {
                    allRegionLabs.addAll(brickVoxelLab.get(brick));
                }
                int uniformCode = voxelMajorityVote(allRegionLabs, entries);
                for (Brick brick : region) {
                    result.put(brick, uniformCode);
                }
            }
        }

        return result;
    }

    /**
     * Builds a spatial adjacency graph where two bricks are neighbors if they
     * share a face or edge in voxel space. Uses a spatial hash for efficiency.
     */
    static Map<Brick, List<Brick>> buildAdjacencyGraph(List<Brick> bricks) {
        // Hash bricks by their occupied voxel columns for fast neighbor lookup
        // Key: packed (x, z, y-layer) → list of bricks occupying that cell
        Map<Long, List<Brick>> cellIndex = new HashMap<>();

        for (Brick brick : bricks) {
            for (int dy = 0; dy < brick.heightUnits(); dy++) {
                int y = brick.y() + dy;
                for (int dx = 0; dx < brick.studX(); dx++) {
                    for (int dz = 0; dz < brick.studY(); dz++) {
                        long key = packCell(brick.x() + dx, y, brick.z() + dz);
                        cellIndex.computeIfAbsent(key, k -> new ArrayList<>(2)).add(brick);
                    }
                }
            }
        }

        Map<Brick, List<Brick>> adjacency = new HashMap<>(bricks.size());
        for (Brick brick : bricks) {
            adjacency.put(brick, new ArrayList<>());
        }

        // For each brick, check cells just outside its boundary for neighbors
        for (Brick brick : bricks) {
            List<Brick> neighbors = adjacency.get(brick);
            addBorderNeighbors(brick, cellIndex, neighbors);
        }

        return adjacency;
    }

    /**
     * Checks cells adjacent to the brick boundary and adds any bricks found
     * as neighbors (avoiding self-references and duplicates).
     */
    private static void addBorderNeighbors(Brick brick, Map<Long, List<Brick>> cellIndex,
                                           List<Brick> neighbors) {
        // We check a 1-cell border around the brick's volume
        int xMin = brick.x();
        int xMax = brick.maxX(); // exclusive
        int yMin = brick.y();
        int yMax = brick.maxY();
        int zMin = brick.z();
        int zMax = brick.maxZ();

        // Check in 6 face directions around the brick volume
        for (int dy = yMin; dy < yMax; dy++) {
            for (int dz = zMin; dz < zMax; dz++) {
                // -X face
                checkCell(cellIndex, packCell(xMin - 1, dy, dz), brick, neighbors);
                // +X face
                checkCell(cellIndex, packCell(xMax, dy, dz), brick, neighbors);
            }
            for (int dx = xMin; dx < xMax; dx++) {
                // -Z face
                checkCell(cellIndex, packCell(dx, dy, zMin - 1), brick, neighbors);
                // +Z face
                checkCell(cellIndex, packCell(dx, dy, zMax), brick, neighbors);
            }
        }
        for (int dx = xMin; dx < xMax; dx++) {
            for (int dz = zMin; dz < zMax; dz++) {
                // -Y face (below)
                checkCell(cellIndex, packCell(dx, yMin - 1, dz), brick, neighbors);
                // +Y face (above)
                checkCell(cellIndex, packCell(dx, yMax, dz), brick, neighbors);
            }
        }
    }

    /** Adds bricks at the given cell to the neighbor list, skipping self and duplicates. */
    private static void checkCell(Map<Long, List<Brick>> cellIndex, long key,
                                  Brick self, List<Brick> neighbors) {
        List<Brick> occupants = cellIndex.get(key);
        if (occupants == null) return;
        for (Brick b : occupants) {
            if (b != self && !neighbors.contains(b)) {
                neighbors.add(b);
            }
        }
    }

    /** Packs (x, y, z) into a single long key for spatial hashing. */
    private static long packCell(int x, int y, int z) {
        return ((long) (x + 4096) << 26) | ((long) (y + 4096) << 13) | (z + 4096);
    }

    /**
     * Flood-fill segmentation: groups bricks into contiguous regions where
     * adjacent bricks have similar hue/chroma (ignoring lightness shifts from
     * shadows and lighting).
     *
     * To prevent gradual color drift from merging an entire model into one
     * region (e.g., smooth baked-lighting gradients where each adjacent pair
     * is similar but endpoints are very different), each candidate brick must
     * be close to both its immediate neighbor AND the region's seed brick.
     * The seed acts as a fixed anchor — unlike a running average, it cannot
     * drift with the expanding frontier.
     *
     * A triple-gate merge criterion is used: bricks must be similar in the
     * normalized Lab (after shadow removal), similar in chrominance in the
     * original Lab (before normalization), AND have a sufficiently close
     * original lightness ratio. This prevents dark features (eyes, nostrils)
     * from being absorbed into large body regions — shadow normalization can
     * make very different original colors appear similar, but the original-Lab
     * gates catch the genuine difference.
     *
     * @param bricks          all bricks to segment
     * @param adjacency       spatial adjacency graph
     * @param brickLab        normalized Lab per brick (after shadow removal)
     * @param originalBrickLab original Lab per brick (before normalization);
     *                         pass the same map as brickLab when no normalization
     *                         has been applied
     */
    static List<List<Brick>> segmentRegions(List<Brick> bricks,
                                            Map<Brick, List<Brick>> adjacency,
                                            Map<Brick, double[]> brickLab,
                                            Map<Brick, double[]> originalBrickLab) {
        Map<Brick, Boolean> visited = new HashMap<>(bricks.size());
        List<List<Brick>> regions = new ArrayList<>();

        for (Brick seed : bricks) {
            if (visited.containsKey(seed)) continue;

            List<Brick> region = new ArrayList<>();
            Queue<Brick> queue = new LinkedList<>();
            queue.add(seed);
            visited.put(seed, true);

            double[] seedLab = brickLab.get(seed);
            double[] seedOrig = originalBrickLab.get(seed);

            while (!queue.isEmpty()) {
                Brick current = queue.poll();
                region.add(current);
                double[] currentLab = brickLab.get(current);

                for (Brick neighbor : adjacency.getOrDefault(current, List.of())) {
                    if (visited.containsKey(neighbor)) continue;
                    double[] neighborLab = brickLab.get(neighbor);
                    double[] neighborOrig = originalBrickLab.get(neighbor);

                    // Triple gate: close in normalized Lab AND similar
                    // chrominance in original Lab AND similar lightness
                    // ratio in original Lab.
                    double oa = seedOrig[1] - neighborOrig[1];
                    double ob = seedOrig[2] - neighborOrig[2];
                    double chromaDist = Math.sqrt(oa * oa + ob * ob);

                    // Lightness-ratio gate: prevents dark features (eyes,
                    // nostrils) from merging into much lighter regions.
                    // Shadow lifting can make L*=25 eyes look like L*=42
                    // (passing the normalized merge check), but the original
                    // lightness ratio exposes the genuine difference.
                    boolean lightnessRatioOk = true;
                    if (seedOrig[0] > 1.0) {
                        double ratio = neighborOrig[0] / seedOrig[0];
                        lightnessRatioOk = ratio >= LIGHTNESS_RATIO_FLOOR;
                    }

                    if (shouldMerge(currentLab, neighborLab)
                            && shouldMerge(seedLab, neighborLab)
                            && chromaDist < ORIGINAL_CHROMA_THRESHOLD
                            && lightnessRatioOk) {
                        visited.put(neighbor, true);
                        queue.add(neighbor);
                    }
                }
            }

            regions.add(region);
        }

        return regions;
    }



    /**
     * Determines whether two adjacent bricks should merge into the same region.
     *
     * Uses a hue-weighted distance that de-emphasizes lightness differences
     * (which are typically shadows/lighting) and emphasizes hue and chroma
     * differences (which indicate genuinely different colors).
     *
     * The distance is computed as:
     *   sqrt( (0.3 * ΔL)² + (Δa)² + (Δb)² )
     *
     * The 0.3 weight on lightness means a DL of 30 (typical shadow range)
     * contributes only 9 units of distance, while a Da or Db of 30 (strong
     * hue shift) contributes the full 30. This makes the algorithm blind to
     * shadows while still detecting real color boundaries. The seed-anchored
     * flood fill prevents gradual drift even with aggressive lightness deweighting.
     */
    static boolean shouldMerge(double[] lab1, double[] lab2) {
        return shouldMerge(lab1, lab2, MERGE_THRESHOLD);
    }

    /**
     * Overload accepting a custom threshold.
     */
    static boolean shouldMerge(double[] lab1, double[] lab2, double threshold) {
        double dl = lab1[0] - lab2[0];
        double da = lab1[1] - lab2[1];
        double db = lab1[2] - lab2[2];

        double lightnessWeight = 0.3;
        double distance = Math.sqrt(
                lightnessWeight * lightnessWeight * dl * dl + da * da + db * db);

        return distance < threshold;
    }

    /**
     * Majority vote: maps each brick in the region to its nearest palette color,
     * then returns the code that appears most often.
     */
    private static int majorityVote(List<Brick> region, Map<Brick, double[]> brickLab,
                                    List<PaletteEntry> entries) {
        Map<Integer, Integer> votes = new HashMap<>();

        for (Brick brick : region) {
            double[] lab = brickLab.get(brick);
            int code = Ciede2000.nearestPaletteEntry(lab[0], lab[1], lab[2], entries, KL);
            votes.merge(code, 1, Integer::sum);
        }

        // Find the code with the most votes
        int bestCode = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestCode = entry.getKey();
            }
        }

        return bestCode;
    }

    /**
     * Majority vote across Lab samples: each sample votes for its nearest
     * palette color via CIEDE2000, and the most-voted code wins.
     */
    private static int voxelMajorityVote(List<double[]> labSamples, List<PaletteEntry> entries) {
        Map<Integer, Integer> votes = new HashMap<>();
        for (double[] lab : labSamples) {
            int code = Ciede2000.nearestPaletteEntry(lab[0], lab[1], lab[2], entries, KL);
            votes.merge(code, 1, Integer::sum);
        }
        int bestCode = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestCode = entry.getKey();
            }
        }
        return bestCode;
    }
}
