package com.lego.color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                corrections.put(brick, dominant.getValue());
            }
        }

        // Apply corrections
        int changed = 0;
        for (Map.Entry<Brick, Integer> entry : corrections.entrySet()) {
            // Re-find dominant: we stored the count, now find the color
            Brick brick = entry.getKey();
            List<Brick> neighbors = findNeighbors(brick, voxelToBrick);
            Map<Integer, Integer> colorCounts = new HashMap<>();
            for (Brick neighbor : neighbors) {
                Integer neighborColor = brickColorCodes.get(neighbor);
                if (neighborColor != null) {
                    colorCounts.merge(neighborColor, 1, Integer::sum);
                }
            }
            // Find max-count color
            int bestColor = -1;
            int bestCount = 0;
            for (Map.Entry<Integer, Integer> cc : colorCounts.entrySet()) {
                if (cc.getValue() > bestCount) {
                    bestCount = cc.getValue();
                    bestColor = cc.getKey();
                }
            }
            if (bestColor >= 0) {
                brickColorCodes.put(brick, bestColor);
                changed++;
            }
        }

        return changed;
    }

    /**
     * Runs multiple smoothing passes until convergence or a maximum iteration count.
     *
     * @return total number of bricks changed across all passes
     */
    public static int smoothIterative(Map<Brick, Integer> brickColorCodes, List<Brick> bricks, int maxPasses) {
        int totalChanged = 0;
        for (int pass = 0; pass < maxPasses; pass++) {
            int changed = smooth(brickColorCodes, bricks);
            totalChanged += changed;
            if (changed == 0) break;
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
        java.util.Set<Brick> seen = new java.util.HashSet<>();
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
