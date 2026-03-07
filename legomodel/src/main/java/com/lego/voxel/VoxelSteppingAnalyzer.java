package com.lego.voxel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.lego.mesh.MeshNormalizer;
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

    public static ResolutionSweepResult runResolutionSweep(
        Mesh mesh,
        Path modelPath,
        List<Integer> resolutions,
        VoxelizationStrategy voxelizationStrategy,
        String exportMode,
        int largeJumpThreshold
    ) {
        Objects.requireNonNull(mesh, "mesh must not be null");
        Objects.requireNonNull(resolutions, "resolutions must not be null");
        Objects.requireNonNull(voxelizationStrategy, "voxelizationStrategy must not be null");

        if (resolutions.isEmpty()) {
            throw new IllegalArgumentException("resolutions must not be empty");
        }

        List<Integer> normalizedResolutions = new ArrayList<>(resolutions.size());
        for (Integer resolution : resolutions) {
            if (resolution == null || resolution < 2) {
                throw new IllegalArgumentException("All resolutions must be >= 2");
            }
            normalizedResolutions.add(resolution);
        }

        Collections.sort(normalizedResolutions);

        List<SweepEntry> entries = new ArrayList<>();
        for (int resolution : normalizedResolutions) {
            Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
            VoxelGrid solid = Voxelizer.voxelize(normalized, resolution, voxelizationStrategy);
            VoxelGrid surface = voxelizationStrategy == VoxelizationStrategy.TOPOLOGICAL_SURFACE
                ? solid
                : SurfaceExtractor.extractSurface(solid);

            AnalysisMetadata metadata = new AnalysisMetadata(
                modelPath != null ? modelPath.toString() : "",
                resolution,
                voxelizationStrategy.cliValue(),
                exportMode,
                Instant.now().toString()
            );

            VoxelSteppingMetrics metrics = analyze(solid, surface, metadata, largeJumpThreshold);
            entries.add(new SweepEntry(resolution, metrics));
        }

        return new ResolutionSweepResult(entries);
    }

    public static void writeMetricsJson(VoxelSteppingMetrics metrics, Path filePath) throws IOException {
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, metricsToJson(metrics));
    }

    public static void writeLayersCsv(VoxelSteppingMetrics metrics, Path filePath) throws IOException {
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        Files.createDirectories(filePath.getParent());

        StringBuilder builder = new StringBuilder();
        builder.append("z,filled,surface,delta\n");

        int depth = metrics.surfaceVoxelsPerLayer().length;
        for (int z = 0; z < depth; z++) {
            int delta = z < metrics.deltas().length ? metrics.deltas()[z] : 0;
            builder.append(z)
                .append(',')
                .append(metrics.filledVoxelsPerLayer()[z])
                .append(',')
                .append(metrics.surfaceVoxelsPerLayer()[z])
                .append(',')
                .append(delta)
                .append('\n');
        }

        Files.writeString(filePath, builder.toString());
    }

    public static void writeSweepJson(ResolutionSweepResult sweepResult, Path filePath) throws IOException {
        Objects.requireNonNull(sweepResult, "sweepResult must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        Files.createDirectories(filePath.getParent());

        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"entries\": [\n");

        List<SweepEntry> entries = sweepResult.entries();
        for (int i = 0; i < entries.size(); i++) {
            SweepEntry entry = entries.get(i);
            VoxelSteppingMetrics metrics = entry.metrics();
            builder.append("    {\n")
                .append("      \"resolution\": ").append(entry.resolution()).append(",\n")
                .append("      \"surface_voxels\": ").append(sum(metrics.surfaceVoxelsPerLayer())).append(",\n")
                .append("      \"largest_abs_jump\": ").append(metrics.jumpStats().largestAbsoluteJump()).append(",\n")
                .append("      \"large_jump_count\": ").append(metrics.jumpStats().largeJumpCount()).append(",\n")
                .append("      \"delta_mean\": ").append(formatDouble(metrics.deltaStats().mean())).append(",\n")
                .append("      \"delta_stddev\": ").append(formatDouble(metrics.deltaStats().standardDeviation())).append(",\n")
                .append("      \"delta_cv\": ").append(formatDouble(metrics.deltaStats().coefficientOfVariation())).append(",\n")
                .append("      \"longest_plateau\": ").append(metrics.plateauStats().longestPlateauLength()).append('\n')
                .append("    }");
            if (i < entries.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }

        builder.append("  ]\n}\n");
        Files.writeString(filePath, builder.toString());
    }

    public static void writeSweepCsv(ResolutionSweepResult sweepResult, Path filePath) throws IOException {
        Objects.requireNonNull(sweepResult, "sweepResult must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        Files.createDirectories(filePath.getParent());

        StringBuilder builder = new StringBuilder();
        builder.append("resolution,surface_voxels,largest_abs_jump,large_jump_count,delta_mean,delta_stddev,delta_cv,longest_plateau,surface_components,x_mismatch,y_mismatch,z_mismatch\n");

        for (SweepEntry entry : sweepResult.entries()) {
            VoxelSteppingMetrics metrics = entry.metrics();
            builder.append(entry.resolution()).append(',')
                .append(sum(metrics.surfaceVoxelsPerLayer())).append(',')
                .append(metrics.jumpStats().largestAbsoluteJump()).append(',')
                .append(metrics.jumpStats().largeJumpCount()).append(',')
                .append(formatDouble(metrics.deltaStats().mean())).append(',')
                .append(formatDouble(metrics.deltaStats().standardDeviation())).append(',')
                .append(formatDouble(metrics.deltaStats().coefficientOfVariation())).append(',')
                .append(metrics.plateauStats().longestPlateauLength()).append(',')
                .append(metrics.componentStats().overallSurfaceComponents()).append(',')
                .append(metrics.symmetryStats().xMismatchOverall()).append(',')
                .append(metrics.symmetryStats().yMismatchOverall()).append(',')
                .append(metrics.symmetryStats().zMismatchOverall())
                .append('\n');
        }

        Files.writeString(filePath, builder.toString());
    }

    private static String metricsToJson(VoxelSteppingMetrics metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");

        AnalysisMetadata metadata = metrics.metadata();
        builder.append("  \"metadata\": {\n")
            .append("    \"model_path\": \"").append(escapeJson(metadata.modelPath())).append("\",\n")
            .append("    \"resolution\": ").append(metadata.resolution()).append(",\n")
            .append("    \"voxelizer_mode\": \"").append(escapeJson(metadata.voxelizerMode())).append("\",\n")
            .append("    \"export_mode\": \"").append(escapeJson(metadata.exportMode())).append("\",\n")
            .append("    \"timestamp\": \"").append(escapeJson(metadata.timestamp())).append("\"\n")
            .append("  },\n");

        builder.append("  \"layer_series\": {\n")
            .append("    \"filled_voxels\": ").append(intArrayToJson(metrics.filledVoxelsPerLayer())).append(",\n")
            .append("    \"surface_voxels\": ").append(intArrayToJson(metrics.surfaceVoxelsPerLayer())).append(",\n")
            .append("    \"delta\": ").append(intArrayToJson(metrics.deltas())).append('\n')
            .append("  },\n");

        DeltaStats deltaStats = metrics.deltaStats();
        builder.append("  \"delta_stats\": {\n")
            .append("    \"mean\": ").append(formatDouble(deltaStats.mean())).append(",\n")
            .append("    \"stddev\": ").append(formatDouble(deltaStats.standardDeviation())).append(",\n")
            .append("    \"min\": ").append(deltaStats.min()).append(",\n")
            .append("    \"max\": ").append(deltaStats.max()).append(",\n")
            .append("    \"coefficient_of_variation\": ").append(formatDouble(deltaStats.coefficientOfVariation())).append('\n')
            .append("  },\n");

        JumpStats jumpStats = metrics.jumpStats();
        builder.append("  \"jump_severity\": {\n")
            .append("    \"largest_abs_jump\": ").append(jumpStats.largestAbsoluteJump()).append(",\n")
            .append("    \"large_jump_threshold\": ").append(jumpStats.largeJumpThreshold()).append(",\n")
            .append("    \"large_jump_count\": ").append(jumpStats.largeJumpCount()).append('\n')
            .append("  },\n");

        PlateauStats plateauStats = metrics.plateauStats();
        builder.append("  \"plateaus\": {\n")
            .append("    \"repeated_adjacent_layers\": ").append(plateauStats.repeatedAdjacentLayers()).append(",\n")
            .append("    \"longest_plateau_length\": ").append(plateauStats.longestPlateauLength()).append('\n')
            .append("  },\n");

        SymmetryStats symmetryStats = metrics.symmetryStats();
        builder.append("  \"symmetry_mismatch\": {\n")
            .append("    \"x_overall\": ").append(symmetryStats.xMismatchOverall()).append(",\n")
            .append("    \"y_overall\": ").append(symmetryStats.yMismatchOverall()).append(",\n")
            .append("    \"z_overall\": ").append(symmetryStats.zMismatchOverall()).append(",\n")
            .append("    \"x_per_layer\": ").append(intArrayToJson(symmetryStats.xMismatchPerLayer())).append(",\n")
            .append("    \"y_per_layer\": ").append(intArrayToJson(symmetryStats.yMismatchPerLayer())).append(",\n")
            .append("    \"z_per_layer\": ").append(intArrayToJson(symmetryStats.zMismatchPerLayer())).append('\n')
            .append("  },\n");

        ComponentStats componentStats = metrics.componentStats();
        builder.append("  \"connectivity\": {\n")
            .append("    \"overall_filled_components\": ").append(componentStats.overallFilledComponents()).append(",\n")
            .append("    \"overall_surface_components\": ").append(componentStats.overallSurfaceComponents()).append(",\n")
            .append("    \"filled_components_per_layer\": ").append(intArrayToJson(componentStats.filledComponentsPerLayer())).append(",\n")
            .append("    \"surface_components_per_layer\": ").append(intArrayToJson(componentStats.surfaceComponentsPerLayer())).append('\n')
            .append("  }\n");

        builder.append("}\n");
        return builder.toString();
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

    private static String intArrayToJson(int[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < values.length; i++) {
            builder.append(values[i]);
            if (i < values.length - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static String escapeJson(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
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
