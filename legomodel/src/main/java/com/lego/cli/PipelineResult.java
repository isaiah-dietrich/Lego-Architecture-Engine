package com.lego.cli;

import java.util.List;
import java.util.Map;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Summary of a completed pipeline execution.
 * Carries all computed data so reporters and coordinators can format output
 * without re-computing anything.
 */
public record PipelineResult(
    int triangleCount,
    int resolution,
    int totalVoxels,
    int solidVoxels,
    int surfaceVoxels,
    List<Brick> bricks,
    String placementPolicyName,
    List<BrickSpec> allowedSpecs,
    Map<Brick, Integer> brickColorCodes,
    int coloredBrickCount,
    int opaquePaletteEntries,
    String colorAlgorithmName,
    int smoothedCount
) {}
