package com.lego.cli;

import java.nio.file.Path;
import java.util.List;

/**
 * Parsed command-line options — the raw bag of flags. Shared between
 * CliOptionsParser and the validation step that turns these
 * into a PipelineRequest.
 */
record ParsedOptions(
    List<String> positionalArgs,
    boolean analyzeStepping,
    Path analysisDir,
    int largeJumpThreshold,
    List<Integer> sweepResolutions,
    String colorMode,
    int colorFallback,
    boolean colorList,
    String colorAlgorithm,
    String placementPolicy
) {}
