package com.lego.cli;

/**
 * Metrics reported for placement-policy A/B comparisons.
 */
record PlacementBenchmarkMetrics(
    String policyName,
    String maskSource,
    int collisionCount,
    int uncoveredRequiredCount,
    int outsideTargetCoverageCount,
    int shellLeakCount,
    int slopePlacementCount,
    int flatSlopeErrorCount,
    double slopeFacingConsistency,
    int overlapPlacementCount,
    int outsideCoveragePlacementCount,
    int slopeAngleMismatchPlacementCount,
    int slopeFacingMismatchPlacementCount,
    int slopeMissingNormalPlacementCount,
    int slopeShadowIntrusionPlacementCount,
    int slopeAdjacentTallFlatConflictPlacementCount,
    int shellLeakResidualVoxelCount,
    int pieceCount,
    long runtimeMs,
    int peakCandidateCount,
    double meanDeltaE,
    double p95DeltaE
) {
    static final String UNIT_PLACEMENTS = "placements";
    static final String UNIT_VOXELS = "voxels";

    static String csvHeader() {
        return "policy,maskSource,collisionCount,uncoveredRequiredCount,outsideTargetCoverageCount,shellLeakCount,"
            + "slopePlacementCount,flatSlopeErrorCount,slopeFacingConsistency,"
            + "overlapPlacementCount,outsideCoveragePlacementCount,slopeAngleMismatchPlacementCount,"
            + "slopeFacingMismatchPlacementCount,slopeMissingNormalPlacementCount,"
            + "slopeShadowIntrusionPlacementCount,slopeAdjacentTallFlatConflictPlacementCount,"
            + "shellLeakResidualVoxelCount,pieceCount,runtimeMs,"
            + "peakCandidateCount,meanDeltaE,p95DeltaE\n";
    }

    String toCsvRow() {
        return policyName + ","
            + maskSource + ","
            + collisionCount + ","
            + uncoveredRequiredCount + ","
            + outsideTargetCoverageCount + ","
            + shellLeakCount + ","
            + slopePlacementCount + ","
            + flatSlopeErrorCount + ","
            + slopeFacingConsistency + ","
            + overlapPlacementCount + ","
            + outsideCoveragePlacementCount + ","
            + slopeAngleMismatchPlacementCount + ","
            + slopeFacingMismatchPlacementCount + ","
            + slopeMissingNormalPlacementCount + ","
            + slopeShadowIntrusionPlacementCount + ","
            + slopeAdjacentTallFlatConflictPlacementCount + ","
            + shellLeakResidualVoxelCount + ","
            + pieceCount + ","
            + runtimeMs + ","
            + peakCandidateCount + ","
            + meanDeltaE + ","
            + p95DeltaE + "\n";
    }

    boolean typedHardGatesZero() {
        return overlapPlacementCount == 0
            && outsideCoveragePlacementCount == 0
            && slopeAngleMismatchPlacementCount == 0
            && slopeFacingMismatchPlacementCount == 0
            && slopeMissingNormalPlacementCount == 0
            && slopeShadowIntrusionPlacementCount == 0
            && slopeAdjacentTallFlatConflictPlacementCount == 0
            && shellLeakResidualVoxelCount == 0;
    }
}
