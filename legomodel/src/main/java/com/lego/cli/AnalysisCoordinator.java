package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;

import com.lego.model.Mesh;
import com.lego.voxel.VoxelGrid;
import com.lego.voxel.VoxelSteppingAnalyzer;
import com.lego.voxel.VoxelSteppingAnalyzer.AnalysisMetadata;
import com.lego.voxel.VoxelSteppingAnalyzer.ResolutionSweepResult;
import com.lego.voxel.VoxelSteppingAnalyzer.VoxelSteppingMetrics;

/**
 * Coordinates stepping analysis and resolution sweeps.
 * Extracted from Main to separate analysis dispatch from pipeline orchestration.
 */
final class AnalysisCoordinator {

    /** Non-instantiable utility class. */
    private AnalysisCoordinator() {}

    /**
     * Runs stepping analysis or resolution sweep as configured in the request.
     *
     * @param request the pipeline request
     * @param mesh    the original (pre-normalization) mesh
     * @param solid   the solid voxel grid
     * @param surface the surface voxel grid
     * @param out     output stream for success messages
     * @throws IOException if writing analysis files fails
     */
    static void runAnalysis(
        PipelineRequest request,
        Mesh mesh,
        VoxelGrid solid,
        VoxelGrid surface,
        PrintStream out
    ) throws IOException {
        Path analysisDir = resolveAnalysisDir(request.analysisDir(), request.outputPath());

        if (!request.sweepResolutions().isEmpty()) {
            ResolutionSweepResult sweepResult = VoxelSteppingAnalyzer.runResolutionSweep(
                mesh,
                request.modelPath(),
                request.sweepResolutions(),
                request.voxelizationStrategy(),
                request.exportMode(),
                request.largeJumpThreshold()
            );

            for (VoxelSteppingAnalyzer.SweepEntry entry : sweepResult.entries()) {
                Path perResolutionDir = analysisDir.resolve("resolution_" + entry.resolution());
                VoxelSteppingAnalyzer.writeMetricsJson(
                    entry.metrics(),
                    perResolutionDir.resolve("stepping_metrics.json")
                );
                VoxelSteppingAnalyzer.writeLayersCsv(
                    entry.metrics(),
                    perResolutionDir.resolve("stepping_layers.csv")
                );
            }

            VoxelSteppingAnalyzer.writeSweepJson(sweepResult, analysisDir.resolve("stepping_sweep.json"));
            VoxelSteppingAnalyzer.writeSweepCsv(sweepResult, analysisDir.resolve("stepping_sweep.csv"));
            out.println("Stepping analysis sweep exported: " + analysisDir.toAbsolutePath());
        } else {
            AnalysisMetadata metadata = new AnalysisMetadata(
                request.modelPath().toString(),
                request.resolution(),
                request.voxelizationStrategy().cliValue(),
                request.exportMode(),
                Instant.now().toString()
            );
            VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
                solid,
                surface,
                metadata,
                request.largeJumpThreshold()
            );

            VoxelSteppingAnalyzer.writeMetricsJson(metrics, analysisDir.resolve("stepping_metrics.json"));
            VoxelSteppingAnalyzer.writeLayersCsv(metrics, analysisDir.resolve("stepping_layers.csv"));
            out.println("Stepping analysis exported: " + analysisDir.toAbsolutePath());
        }
    }

    /** Resolves the analysis output directory, falling back to the parent of the output path. */
    private static Path resolveAnalysisDir(Path explicitAnalysisDir, Path outputPath) {
        if (explicitAnalysisDir != null) {
            return explicitAnalysisDir;
        }
        if (outputPath != null && outputPath.getParent() != null) {
            return outputPath.getParent();
        }
        return Path.of("output", "analysis");
    }
}
