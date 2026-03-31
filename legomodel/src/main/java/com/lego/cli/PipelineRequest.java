package com.lego.cli;

import java.nio.file.Path;
import java.util.List;

import com.lego.voxel.VoxelizationStrategy;

/**
 * Validated, immutable description of a single pipeline execution.
 * Built from ParsedOptions after all validation has passed.
 */
public record PipelineRequest(
    Path modelPath,
    int resolution,
    Path outputPath,
    String exportMode,
    VoxelizationStrategy voxelizationStrategy,
    String colorMode,
    int colorFallback,
    boolean colorList,
    String colorAlgorithm,
    boolean analyzeStepping,
    Path analysisDir,
    int largeJumpThreshold,
    List<Integer> sweepResolutions,
    boolean benchmarkAb,
    Path benchmarkDir,
    Path ldrawLibraryDir,
    Path geometryMaskCacheDir
) {}
