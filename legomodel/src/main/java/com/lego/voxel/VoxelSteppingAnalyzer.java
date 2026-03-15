package com.lego.voxel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import com.lego.model.Mesh;

/**
 * Analyzer for voxel stepping/transition artifacts across layers.
 */
public final class VoxelSteppingAnalyzer {

    private static final int[][] NEIGHBORS_6 = {
        { 1, 0, 0 }, { -1, 0, 0 },
        { 0, 1, 0 }, { 0, -1, 0 },
        { 0, 0, 1 }, { 0, 0, -1 }
    };

    private static final int[][] NEIGHBORS_2D_4 = {
        { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    private VoxelSteppingAnalyzer() {
        // Utility class
    }

    public static VoxelSteppingMetrics analyze(
        VoxelGrid solid,
        VoxelGrid surface,
        AnalysisMetadata metadata,
        int largeJumpThreshold
    ) {
        if (surface == null && solid == null) {
            throw new IllegalArgumentException("At least one voxel grid must be provided");
        }
        if (largeJumpThreshold < 0) {
            throw new IllegalArgumentException("largeJumpThreshold must be >= 0");
        }

        VoxelGrid effectiveSurface = surface != null ? surface : solid;
        VoxelGrid effectiveSolid = solid != null ? solid : effectiveSurface;

        validateSameDimensions(effectiveSolid, effectiveSurface);

        int width = effectiveSurface.width();
        int height = effectiveSurface.height();
        int depth = effectiveSurface.depth();

        int[] filledPerLayer = new int[depth];
        int[] surfacePerLayer = new int[depth];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    if (effectiveSolid.isFilled(x, y, z)) {
                        filledPerLayer[z]++;
                    }
                    if (effectiveSurface.isFilled(x, y, z)) {
                        surfacePerLayer[z]++;
                    }
                }
            }
        }

        int[] deltas = computeDeltas(surfacePerLayer);
        DeltaStats deltaStats = computeDeltaStats(deltas);
        JumpStats jumpStats = computeJumpStats(deltas, largeJumpThreshold);
        PlateauStats plateauStats = computePlateauStats(surfacePerLayer);

        SymmetryStats symmetryStats = computeSymmetryStats(effectiveSurface);

        int overallSolidComponents = count3DConnectedComponents(effectiveSolid);
        int overallSurfaceComponents = count3DConnectedComponents(effectiveSurface);
        int[] filledComponentsPerLayer = count2DComponentsPerLayer(effectiveSolid);
        int[] surfaceComponentsPerLayer = count2DComponentsPerLayer(effectiveSurface);

        AnalysisMetadata effectiveMetadata = metadata != null ? metadata :
            new AnalysisMetadata(
                "",
                effectiveSurface.width(),
                "legacy",
                "brick",
                Instant.now().toString()
            );

        return new VoxelSteppingMetrics(
            effectiveMetadata,
            filledPerLayer,
            surfacePerLayer,
            deltas,
            deltaStats,
            jumpStats,
            plateauStats,
            symmetryStats,
            new ComponentStats(
                overallSolidComponents,
                overallSurfaceComponents,
                filledComponentsPerLayer,
                surfaceComponentsPerLayer
            )
        );
    }

    /**
     * @deprecated Use {@link ResolutionSweepRunner#runResolutionSweep} directly.
     */
    public static ResolutionSweepResult runResolutionSweep(
        Mesh mesh,
        Path modelPath,
        List<Integer> resolutions,
        VoxelizationStrategy voxelizationStrategy,
        String exportMode,
        int largeJumpThreshold
    ) {
        return ResolutionSweepRunner.runResolutionSweep(
            mesh, modelPath, resolutions, voxelizationStrategy, exportMode, largeJumpThreshold);
    }

    /**
     * @deprecated Use {@link SteppingAnalysisWriter#writeMetricsJson} directly.
     */
    public static void writeMetricsJson(VoxelSteppingMetrics metrics, Path filePath) throws IOException {
        SteppingAnalysisWriter.writeMetricsJson(metrics, filePath);
    }

    /**
     * @deprecated Use {@link SteppingAnalysisWriter#writeLayersCsv} directly.
     */
    public static void writeLayersCsv(VoxelSteppingMetrics metrics, Path filePath) throws IOException {
        SteppingAnalysisWriter.writeLayersCsv(metrics, filePath);
    }

    /**
     * @deprecated Use {@link SteppingAnalysisWriter#writeSweepJson} directly.
     */
    public static void writeSweepJson(ResolutionSweepResult sweepResult, Path filePath) throws IOException {
        SteppingAnalysisWriter.writeSweepJson(sweepResult, filePath);
    }

    /**
     * @deprecated Use {@link SteppingAnalysisWriter#writeSweepCsv} directly.
     */
    public static void writeSweepCsv(ResolutionSweepResult sweepResult, Path filePath) throws IOException {
        SteppingAnalysisWriter.writeSweepCsv(sweepResult, filePath);
    }

    private static void validateSameDimensions(VoxelGrid a, VoxelGrid b) {
        if (a.width() != b.width() || a.height() != b.height() || a.depth() != b.depth()) {
            throw new IllegalArgumentException("Voxel grids must have identical dimensions");
        }
    }

    private static int[] computeDeltas(int[] counts) {
        if (counts.length <= 1) {
            return new int[0];
        }
        int[] deltas = new int[counts.length - 1];
        for (int z = 0; z < counts.length - 1; z++) {
            deltas[z] = counts[z] - counts[z + 1];
        }
        return deltas;
    }

    private static DeltaStats computeDeltaStats(int[] deltas) {
        if (deltas.length == 0) {
            return new DeltaStats(0.0, 0.0, 0, 0, 0.0);
        }

        int min = deltas[0];
        int max = deltas[0];
        double sum = 0.0;
        for (int delta : deltas) {
            sum += delta;
            if (delta < min) {
                min = delta;
            }
            if (delta > max) {
                max = delta;
            }
        }

        double mean = sum / deltas.length;

        double varSum = 0.0;
        for (int delta : deltas) {
            double d = delta - mean;
            varSum += d * d;
        }
        double std = Math.sqrt(varSum / deltas.length);
        double cv = Math.abs(mean) < 1e-12 ? 0.0 : std / Math.abs(mean);

        return new DeltaStats(mean, std, min, max, cv);
    }

    private static JumpStats computeJumpStats(int[] deltas, int threshold) {
        int largest = 0;
        int count = 0;
        for (int delta : deltas) {
            int abs = Math.abs(delta);
            if (abs > largest) {
                largest = abs;
            }
            if (abs > threshold) {
                count++;
            }
        }
        return new JumpStats(largest, threshold, count);
    }

    private static PlateauStats computePlateauStats(int[] counts) {
        if (counts.length == 0) {
            return new PlateauStats(0, 0);
        }

        int repeatedAdjacent = 0;
        int longest = 1;
        int current = 1;

        for (int z = 0; z < counts.length - 1; z++) {
            if (counts[z] == counts[z + 1]) {
                repeatedAdjacent++;
                current++;
                if (current > longest) {
                    longest = current;
                }
            } else {
                current = 1;
            }
        }

        return new PlateauStats(repeatedAdjacent, longest);
    }

    private static SymmetryStats computeSymmetryStats(VoxelGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int depth = grid.depth();

        int[] xPerLayer = new int[depth];
        int[] yPerLayer = new int[depth];
        int[] zPerLayer = new int[depth];

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width / 2; x++) {
                int mx = width - 1 - x;
                for (int y = 0; y < height; y++) {
                    if (grid.isFilled(x, y, z) != grid.isFilled(mx, y, z)) {
                        xPerLayer[z]++;
                    }
                }
            }

            for (int y = 0; y < height / 2; y++) {
                int my = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    if (grid.isFilled(x, y, z) != grid.isFilled(x, my, z)) {
                        yPerLayer[z]++;
                    }
                }
            }

            int mz = depth - 1 - z;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (grid.isFilled(x, y, z) != grid.isFilled(x, y, mz)) {
                        zPerLayer[z]++;
                    }
                }
            }
        }

        int xOverall = sum(xPerLayer);
        int yOverall = sum(yPerLayer);

        int zOverall = 0;
        for (int z = 0; z < depth / 2; z++) {
            int mz = depth - 1 - z;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (grid.isFilled(x, y, z) != grid.isFilled(x, y, mz)) {
                        zOverall++;
                    }
                }
            }
        }

        return new SymmetryStats(xOverall, yOverall, zOverall, xPerLayer, yPerLayer, zPerLayer);
    }

    private static int count3DConnectedComponents(VoxelGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int depth = grid.depth();
        boolean[][][] visited = new boolean[width][height][depth];

        int components = 0;
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    if (!grid.isFilled(x, y, z) || visited[x][y][z]) {
                        continue;
                    }

                    components++;
                    visited[x][y][z] = true;
                    queue.addLast(new int[] { x, y, z });

                    while (!queue.isEmpty()) {
                        int[] v = queue.removeFirst();
                        int vx = v[0];
                        int vy = v[1];
                        int vz = v[2];

                        for (int[] neighbor : NEIGHBORS_6) {
                            int nx = vx + neighbor[0];
                            int ny = vy + neighbor[1];
                            int nz = vz + neighbor[2];
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height || nz < 0 || nz >= depth) {
                                continue;
                            }
                            if (!visited[nx][ny][nz] && grid.isFilled(nx, ny, nz)) {
                                visited[nx][ny][nz] = true;
                                queue.addLast(new int[] { nx, ny, nz });
                            }
                        }
                    }
                }
            }
        }

        return components;
    }

    private static int[] count2DComponentsPerLayer(VoxelGrid grid) {
        int width = grid.width();
        int height = grid.height();
        int depth = grid.depth();
        int[] componentsPerLayer = new int[depth];

        for (int z = 0; z < depth; z++) {
            boolean[][] visited = new boolean[width][height];
            int components = 0;
            ArrayDeque<int[]> queue = new ArrayDeque<>();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!grid.isFilled(x, y, z) || visited[x][y]) {
                        continue;
                    }

                    components++;
                    visited[x][y] = true;
                    queue.addLast(new int[] { x, y });

                    while (!queue.isEmpty()) {
                        int[] v = queue.removeFirst();
                        int vx = v[0];
                        int vy = v[1];

                        for (int[] neighbor : NEIGHBORS_2D_4) {
                            int nx = vx + neighbor[0];
                            int ny = vy + neighbor[1];
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                                continue;
                            }
                            if (!visited[nx][ny] && grid.isFilled(nx, ny, z)) {
                                visited[nx][ny] = true;
                                queue.addLast(new int[] { nx, ny });
                            }
                        }
                    }
                }
            }

            componentsPerLayer[z] = components;
        }

        return componentsPerLayer;
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    public record AnalysisMetadata(
        String modelPath,
        int resolution,
        String voxelizerMode,
        String exportMode,
        String timestamp
    ) {}

    public record DeltaStats(
        double mean,
        double standardDeviation,
        int min,
        int max,
        double coefficientOfVariation
    ) {}

    public record JumpStats(
        int largestAbsoluteJump,
        int largeJumpThreshold,
        int largeJumpCount
    ) {}

    public record PlateauStats(
        int repeatedAdjacentLayers,
        int longestPlateauLength
    ) {}

    public record SymmetryStats(
        int xMismatchOverall,
        int yMismatchOverall,
        int zMismatchOverall,
        int[] xMismatchPerLayer,
        int[] yMismatchPerLayer,
        int[] zMismatchPerLayer
    ) {}

    public record ComponentStats(
        int overallFilledComponents,
        int overallSurfaceComponents,
        int[] filledComponentsPerLayer,
        int[] surfaceComponentsPerLayer
    ) {}

    public record VoxelSteppingMetrics(
        AnalysisMetadata metadata,
        int[] filledVoxelsPerLayer,
        int[] surfaceVoxelsPerLayer,
        int[] deltas,
        DeltaStats deltaStats,
        JumpStats jumpStats,
        PlateauStats plateauStats,
        SymmetryStats symmetryStats,
        ComponentStats componentStats
    ) {}

    public record SweepEntry(int resolution, VoxelSteppingMetrics metrics) {}

    public record ResolutionSweepResult(List<SweepEntry> entries) {}
}
