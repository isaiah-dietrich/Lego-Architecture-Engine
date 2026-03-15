package com.lego.voxel;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.lego.mesh.MeshNormalizer;
import com.lego.model.Mesh;
import com.lego.voxel.VoxelSteppingAnalyzer.AnalysisMetadata;
import com.lego.voxel.VoxelSteppingAnalyzer.ResolutionSweepResult;
import com.lego.voxel.VoxelSteppingAnalyzer.SweepEntry;
import com.lego.voxel.VoxelSteppingAnalyzer.VoxelSteppingMetrics;

/**
 * Runs stepping analysis at multiple resolutions.
 * Extracted from {@code VoxelSteppingAnalyzer} to separate sweep orchestration
 * from pure analysis computation.
 */
public final class ResolutionSweepRunner {

    private ResolutionSweepRunner() {}

    /** Runs stepping analysis at each resolution and returns aggregated results. */
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

            VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(solid, surface, metadata, largeJumpThreshold);
            entries.add(new SweepEntry(resolution, metrics));
        }

        return new ResolutionSweepResult(entries);
    }
}
