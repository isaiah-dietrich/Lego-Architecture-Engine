package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lego.model.Brick;

/**
 * Post-processing pass that smooths brick color assignments by replacing
 * isolated color outliers with the dominant color of their neighbors.
 *
 * <p>This fixes "color speckle" caused by noisy texture samples (JPEG
 * artifacts, shadow gradients, mouth/paw-pad redness, etc.) that produce
 * wrong-hue matches in the palette mapper.
 *
 * <p>Algorithm: for each brick, find all face-adjacent bricks. If the
 * brick's LDraw color code differs from every neighbor's color AND a
 * single neighbor color appears more than once, replace the brick's
 * color with that dominant neighbor color.
 */
public final class ColorSmoother {

    private ColorSmoother() {}

    /**
     * Smooths brick color assignments by eliminating isolated outliers.
     *
     * <p>A brick is considered an outlier if:
     * <ol>
     *   <li>It has at least 2 neighbors</li>
     *   <li>Its color does not match ANY neighbor's color</li>
     *   <li>One neighbor color is a clear majority (appears in &gt;50% of neighbors)</li>
     * </ol>
     * Outlier bricks are recolored to the majority neighbor color.
     *
     * @param brickColorCodes mutable map from brick to LDraw color code
     * @param bricks          all bricks in the model
     * @return number of bricks whose color was changed
     */
    public static int smooth(Map<Brick, Integer> brickColorCodes, List<Brick> bricks) {
        // Build spatial index: map from each voxel coordinate to its owning brick
        Map<Long, Brick> voxelToBrick = buildVoxelIndex(bricks);

        // Collect changes (don't mutate during iteration)
        Map<Brick, Integer> corrections = new HashMap<>();

        for (Brick brick : bricks) {
            Integer myColor = brickColorCodes.get(brick);
            if (myColor == null) continue;

            // Find all unique face-adjacent bricks
            List<Brick> neighbors = findNeighbors(brick, voxelToBrick);
            if (neighbors.size() < 2) continue;

            // Count neighbor colors
            Map<Integer, Integer> colorCounts = new HashMap<>();
            boolean matchFound = false;
            for (Brick neighbor : neighbors) {
                Integer neighborColor = brickColorCodes.get(neighbor);
                if (neighborColor == null) continue;
                if (neighborColor.equals(myColor)) {
                    matchFound = true;
                    break;
                }
                colorCounts.merge(neighborColor, 1, Integer::sum);
            }

            // If at least one neighbor shares our color, this isn't an outlier
            if (matchFound) continue;

            // Find the dominant neighbor color
            int totalNeighborsWithColor = colorCounts.values().stream().mapToInt(Integer::intValue).sum();
            if (totalNeighborsWithColor < 2) continue;

            Map.Entry<Integer, Integer> dominant = null;
            for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
                if (dominant == null || entry.getValue() > dominant.getValue()) {
                    dominant = entry;
                }
            }

            // Only replace if the dominant color has a clear majority (>50% of colored neighbors)
            if (dominant != null && dominant.getValue() > totalNeighborsWithColor / 2) {
                corrections.put(brick, dominant.getKey());
            }
        }

        // Apply all corrections atomically (don't mutate during iteration)
        for (Map.Entry<Brick, Integer> entry : corrections.entrySet()) {
            brickColorCodes.put(entry.getKey(), entry.getValue());
        }

        return corrections.size();
    }

    /**
     * Runs multiple smoothing passes until convergence or a maximum iteration count.
     *
     * @return total number of bricks changed across all passes
     */
    public static int smoothIterative(Map<Brick, Integer> brickColorCodes, List<Brick> bricks, int maxPasses) {
        int totalChanged = 0;

        // Pass 1: frequency-based smoothing — replace rare colors with common neighbor colors.
        // This catches patches of wrong-hue artifacts (e.g., Sand Purple surrounded by browns)
        // that the outlier detector can't fix because bricks within the patch have same-color neighbors.
        totalChanged += smoothRareColors(brickColorCodes, bricks, 0.02, 3);

        // Pass 2: outlier smoothing — remove isolated single-brick color speckle
        for (int pass = 0; pass < maxPasses; pass++) {
            int changed = smooth(brickColorCodes, bricks);
            totalChanged += changed;
            if (changed == 0) break;
        }
        return totalChanged;
    }

    /**
     * Replaces bricks whose color is globally rare (used by fewer than
     * {@code threshold} fraction of total bricks) with the most common
     * non-rare neighbor color. Runs iteratively until convergence.
     *
     * <p>This targets patches of wrong-hue artifacts caused by baked lighting
     * in textures. Within such a patch, neighboring bricks share the same
     * wrong color, so the standard outlier detector doesn't flag them. But
     * the wrong color appears in very few bricks overall — this pass detects
     * and corrects those minority-color clusters.
     *
     * @param brickColorCodes mutable map from brick to LDraw color code
     * @param bricks          all bricks in the model
     * @param threshold       colors used by fewer than this fraction of total colored bricks are "rare"
     * @param maxPasses        maximum iterations
     * @return number of bricks whose color was changed
     */
    static int smoothRareColors(Map<Brick, Integer> brickColorCodes, List<Brick> bricks,
                                double threshold, int maxPasses) {
        Map<Long, Brick> voxelToBrick = buildVoxelIndex(bricks);
        int totalChanged = 0;

        for (int pass = 0; pass < maxPasses; pass++) {
            // Compute color frequencies
            Map<Integer, Integer> colorFreq = new HashMap<>();
            for (Integer code : brickColorCodes.values()) {
                colorFreq.merge(code, 1, Integer::sum);
            }
            int totalColored = brickColorCodes.size();
            int minCount = (int) (totalColored * threshold);

            // Identify rare colors
            Set<Integer> rareColors = new HashSet<>();
            for (Map.Entry<Integer, Integer> entry : colorFreq.entrySet()) {
                if (entry.getValue() <= minCount) {
                    rareColors.add(entry.getKey());
                }
            }
            if (rareColors.isEmpty()) break;

            // For each brick with a rare color, try to replace with non-rare neighbor majority
            Map<Brick, Integer> corrections = new HashMap<>();
            for (Brick brick : bricks) {
                Integer myColor = brickColorCodes.get(brick);
                if (myColor == null || !rareColors.contains(myColor)) continue;

                List<Brick> neighbors = findNeighbors(brick, voxelToBrick);
                if (neighbors.isEmpty()) continue;

                // Find the most common NON-RARE neighbor color
                Map<Integer, Integer> neighborCounts = new HashMap<>();
                for (Brick neighbor : neighbors) {
                    Integer nc = brickColorCodes.get(neighbor);
                    if (nc != null && !rareColors.contains(nc)) {
                        neighborCounts.merge(nc, 1, Integer::sum);
                    }
                }
                if (neighborCounts.isEmpty()) continue;

                // Pick the most frequent non-rare neighbor color
                int bestColor = -1;
                int bestCount = 0;
                for (Map.Entry<Integer, Integer> entry : neighborCounts.entrySet()) {
                    if (entry.getValue() > bestCount) {
                        bestCount = entry.getValue();
                        bestColor = entry.getKey();
                    }
                }
                if (bestColor >= 0) {
                    corrections.put(brick, bestColor);
                }
            }

            if (corrections.isEmpty()) break;

            for (Map.Entry<Brick, Integer> entry : corrections.entrySet()) {
                brickColorCodes.put(entry.getKey(), entry.getValue());
            }
            totalChanged += corrections.size();
        }

        return totalChanged;
    }

    /**
     * Builds a spatial index mapping each voxel coordinate to its owning brick.
     */
    private static Map<Long, Brick> buildVoxelIndex(List<Brick> bricks) {
        Map<Long, Brick> index = new HashMap<>();
        for (Brick brick : bricks) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    for (int z = brick.z(); z < brick.maxZ(); z++) {
                        index.put(pack(x, y, z), brick);
                    }
                }
            }
        }
        return index;
    }

    /**
     * Finds all unique bricks that are face-adjacent to the given brick.
     * Checks one voxel layer outside each face of the brick's bounding box.
     */
    private static List<Brick> findNeighbors(Brick brick, Map<Long, Brick> voxelToBrick) {
        List<Brick> neighbors = new ArrayList<>();
        Set<Brick> seen = new HashSet<>();
        seen.add(brick);

        // Check all 6 faces (one voxel outside each face)
        // -X face
        if (brick.x() > 0) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    addNeighbor(voxelToBrick, pack(brick.x() - 1, y, z), seen, neighbors);
                }
            }
        }
        // +X face
        for (int y = brick.y(); y < brick.maxY(); y++) {
            for (int z = brick.z(); z < brick.maxZ(); z++) {
                addNeighbor(voxelToBrick, pack(brick.maxX(), y, z), seen, neighbors);
            }
        }
        // -Y face
        if (brick.y() > 0) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int z = brick.z(); z < brick.maxZ(); z++) {
                    addNeighbor(voxelToBrick, pack(x, brick.y() - 1, z), seen, neighbors);
                }
            }
        }
        // +Y face
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int z = brick.z(); z < brick.maxZ(); z++) {
                addNeighbor(voxelToBrick, pack(x, brick.maxY(), z), seen, neighbors);
            }
        }
        // -Z face
        if (brick.z() > 0) {
            for (int x = brick.x(); x < brick.maxX(); x++) {
                for (int y = brick.y(); y < brick.maxY(); y++) {
                    addNeighbor(voxelToBrick, pack(x, y, brick.z() - 1), seen, neighbors);
                }
            }
        }
        // +Z face
        for (int x = brick.x(); x < brick.maxX(); x++) {
            for (int y = brick.y(); y < brick.maxY(); y++) {
                addNeighbor(voxelToBrick, pack(x, y, brick.maxZ()), seen, neighbors);
            }
        }

        return neighbors;
    }

    private static void addNeighbor(Map<Long, Brick> index, long key, java.util.Set<Brick> seen, List<Brick> out) {
        Brick neighbor = index.get(key);
        if (neighbor != null && seen.add(neighbor)) {
            out.add(neighbor);
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) x << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
