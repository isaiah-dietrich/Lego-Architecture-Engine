package com.lego.voxel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.lego.voxel.VoxelSteppingAnalyzer.AnalysisMetadata;
import com.lego.voxel.VoxelSteppingAnalyzer.ComponentStats;
import com.lego.voxel.VoxelSteppingAnalyzer.DeltaStats;
import com.lego.voxel.VoxelSteppingAnalyzer.JumpStats;
import com.lego.voxel.VoxelSteppingAnalyzer.PlateauStats;
import com.lego.voxel.VoxelSteppingAnalyzer.ResolutionSweepResult;
import com.lego.voxel.VoxelSteppingAnalyzer.SweepEntry;
import com.lego.voxel.VoxelSteppingAnalyzer.SymmetryStats;
import com.lego.voxel.VoxelSteppingAnalyzer.VoxelSteppingMetrics;

/**
 * Writes stepping analysis results to JSON and CSV files.
 * Extracted from {@code VoxelSteppingAnalyzer} to separate I/O from computation.
 */
public final class SteppingAnalysisWriter {

    private SteppingAnalysisWriter() {}

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

    static String metricsToJson(VoxelSteppingMetrics metrics) {
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

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) s += v;
        return s;
    }

    private static String intArrayToJson(int[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < values.length; i++) {
            builder.append(values[i]);
            if (i < values.length - 1) builder.append(',');
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
}
