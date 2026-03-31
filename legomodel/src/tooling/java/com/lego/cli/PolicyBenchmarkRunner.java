package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.BrickPlacer;
import com.lego.optimize.GreedyAreaPolicy;
import com.lego.optimize.PartMaskProvider;
import com.lego.optimize.PlacementFeatureGrid;
import com.lego.optimize.PlacementPolicy;
import com.lego.optimize.PlacementStatsProvider;
import com.lego.optimize.ScoringPlacementPolicy;
import com.lego.voxel.VoxelGrid;

/**
 * Executes A/B placement-policy benchmarks and writes report artifacts.
 */
final class PolicyBenchmarkRunner {

    private static final DateTimeFormatter RUN_ID_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private PolicyBenchmarkRunner() {}

    static void runAndWrite(
        PipelineRequest request,
        PrintStream out,
        VoxelGrid surface,
        List<BrickSpec> allowedSpecs,
        PlacementFeatureGrid featureGrid,
        String selectedPolicyName,
        List<Brick> selectedBricks,
        long selectedRuntimeMs,
        int selectedPeakCandidateCount,
        PartMaskProvider maskProvider,
        MaskSource maskSource
    ) throws IOException {
        PlacementBenchmarkMetrics selectedMetrics = PlacementBenchmarkCalculator.compute(
            selectedPolicyName,
            surface,
            allowedSpecs,
            selectedBricks,
            featureGrid,
            selectedRuntimeMs,
            selectedPeakCandidateCount,
            maskProvider,
            maskSource.cliLabel()
        );

        PlacementBenchmarkMetrics baselineMetrics;
        if ("scoring".equalsIgnoreCase(selectedPolicyName)) {
            baselineMetrics = selectedMetrics;
        } else {
            PlacementPolicy baselinePolicy = new ScoringPlacementPolicy(featureGrid);
            long baselineStart = System.nanoTime();
            List<Brick> baselineBricks = BrickPlacer.placeBricks(surface, allowedSpecs, baselinePolicy);
            long baselineRuntimeMs = (System.nanoTime() - baselineStart) / 1_000_000L;
            int baselinePeak = (baselinePolicy instanceof PlacementStatsProvider statsProvider)
                ? statsProvider.peakCandidateCount()
                : 0;
            baselineMetrics = PlacementBenchmarkCalculator.compute(
                "scoring",
                surface,
                allowedSpecs,
                baselineBricks,
                featureGrid,
                baselineRuntimeMs,
                baselinePeak,
                maskProvider,
                maskSource.cliLabel()
            );
        }

        Path benchmarkDir = resolveBenchmarkDir(request);
        Files.createDirectories(benchmarkDir);

        writeMetricsJson(benchmarkDir.resolve("scoring_metrics.json"), baselineMetrics);
        writeMetricsJson(benchmarkDir.resolve(selectedPolicyName + "_metrics.json"), selectedMetrics);
        String csv = PlacementBenchmarkMetrics.csvHeader()
            + baselineMetrics.toCsvRow()
            + selectedMetrics.toCsvRow();
        Files.writeString(benchmarkDir.resolve("aggregate.csv"), csv, StandardCharsets.UTF_8);

        BenchmarkDelta delta = BenchmarkDelta.from(baselineMetrics, selectedMetrics);
        Files.writeString(benchmarkDir.resolve("summary.json"), toSummaryJson(delta), StandardCharsets.UTF_8);

        printReport(out, benchmarkDir, baselineMetrics, selectedMetrics, delta);
    }

    private static Path resolveBenchmarkDir(PipelineRequest request) {
        if (request.benchmarkDir() != null) {
            return request.benchmarkDir();
        }
        String runId = "run_" + LocalDateTime.now().format(RUN_ID_FORMAT);
        return Path.of("output", "benchmarks", runId);
    }

    private static void writeMetricsJson(Path path, PlacementBenchmarkMetrics metrics) throws IOException {
        String json = """
            {
              "policy": "%s",
              "maskSource": "%s",
              "quality": {
                "collisionCount": %d,
                "uncoveredRequiredCount": %d,
                "outsideTargetCoverageCount": %d,
                "shellLeakCount": %d,
                "typedCollisionCounts": {
                  "overlapPlacementCount": %d,
                  "outsideCoveragePlacementCount": %d,
                  "slopeAngleMismatchPlacementCount": %d,
                  "slopeFacingMismatchPlacementCount": %d,
                  "slopeMissingNormalPlacementCount": %d,
                  "slopeShadowIntrusionPlacementCount": %d,
                  "slopeAdjacentTallFlatConflictPlacementCount": %d,
                  "shellLeakResidualVoxelCount": %d
                },
                "typedCollisionUnits": {
                  "overlapPlacementCount": "%s",
                  "outsideCoveragePlacementCount": "%s",
                  "slopeAngleMismatchPlacementCount": "%s",
                  "slopeFacingMismatchPlacementCount": "%s",
                  "slopeMissingNormalPlacementCount": "%s",
                  "slopeShadowIntrusionPlacementCount": "%s",
                  "slopeAdjacentTallFlatConflictPlacementCount": "%s",
                  "shellLeakResidualVoxelCount": "%s"
                }
              },
              "slopeBehavior": {
                "slopePlacementCount": %d,
                "flatSlopeErrorCount": %d,
                "slopeFacingConsistency": %.6f
              },
              "efficiency": {
                "pieceCount": %d,
                "runtimeMs": %d,
                "peakCandidateCount": %d
              },
              "color": {
                "meanDeltaE": %.6f,
                "p95DeltaE": %.6f
              }
            }
            """.formatted(
            metrics.policyName(),
            metrics.maskSource(),
            metrics.collisionCount(),
            metrics.uncoveredRequiredCount(),
            metrics.outsideTargetCoverageCount(),
            metrics.shellLeakCount(),
            metrics.overlapPlacementCount(),
            metrics.outsideCoveragePlacementCount(),
            metrics.slopeAngleMismatchPlacementCount(),
            metrics.slopeFacingMismatchPlacementCount(),
            metrics.slopeMissingNormalPlacementCount(),
            metrics.slopeShadowIntrusionPlacementCount(),
            metrics.slopeAdjacentTallFlatConflictPlacementCount(),
            metrics.shellLeakResidualVoxelCount(),
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_PLACEMENTS,
            PlacementBenchmarkMetrics.UNIT_VOXELS,
            metrics.slopePlacementCount(),
            metrics.flatSlopeErrorCount(),
            metrics.slopeFacingConsistency(),
            metrics.pieceCount(),
            metrics.runtimeMs(),
            metrics.peakCandidateCount(),
            metrics.meanDeltaE(),
            metrics.p95DeltaE()
        );
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static String toSummaryJson(BenchmarkDelta delta) {
        return """
            {
              "selectedPolicy": "%s",
              "maskSource": "%s",
              "baselinePolicy": "scoring",
              "deltaVsBaseline": {
                "collisionCount": %d,
                "uncoveredRequiredCount": %d,
                "outsideTargetCoverageCount": %d,
                "shellLeakCount": %d,
                "flatSlopeErrorCount": %d,
                "typedCollisionDeltas": {
                  "overlapPlacementCount": %d,
                  "outsideCoveragePlacementCount": %d,
                  "slopeAngleMismatchPlacementCount": %d,
                  "slopeFacingMismatchPlacementCount": %d,
                  "slopeMissingNormalPlacementCount": %d,
                  "slopeShadowIntrusionPlacementCount": %d,
                  "slopeAdjacentTallFlatConflictPlacementCount": %d,
                  "shellLeakResidualVoxelCount": %d
                },
                "pieceCount": %d,
                "runtimeMs": %d,
                "meanDeltaE": %.6f,
                "p95DeltaE": %.6f
              },
              "worksBetterCriteria": {
                "hardGatesZero": %s,
                "typedHardGatesZero": %s,
                "flatSlopeErrorLowerThanScoring": %s,
                "shellLeakLowerThanScoring": %s,
                "pieceCountLEWhenHardGatesMet": %s,
                "meanDeltaELEWhenColorEnabled": %s,
                "runtimeWithin4xBudget": %s
              }
            }
            """.formatted(
            delta.selectedPolicy(),
            delta.maskSource(),
            delta.collisionDelta(),
            delta.uncoveredDelta(),
            delta.outsideCoverageDelta(),
            delta.shellLeakDelta(),
            delta.flatSlopeErrorDelta(),
            delta.overlapPlacementDelta(),
            delta.outsideCoveragePlacementDelta(),
            delta.slopeAngleMismatchPlacementDelta(),
            delta.slopeFacingMismatchPlacementDelta(),
            delta.slopeMissingNormalPlacementDelta(),
            delta.slopeShadowIntrusionPlacementDelta(),
            delta.slopeAdjacentTallFlatConflictPlacementDelta(),
            delta.shellLeakResidualVoxelDelta(),
            delta.pieceCountDelta(),
            delta.runtimeDeltaMs(),
            delta.meanDeltaEDelta(),
            delta.p95DeltaEDelta(),
            delta.hardGatesZero(),
            delta.typedHardGatesZero(),
            delta.flatSlopeErrorLower(),
            delta.shellLeakLower(),
            delta.pieceCountLeWhenHardGates(),
            delta.meanDeltaELeWhenAvailable(),
            delta.runtimeWithin4x()
        );
    }

    private static void printReport(PrintStream out,
                                    Path dir,
                                    PlacementBenchmarkMetrics baseline,
                                    PlacementBenchmarkMetrics selected,
                                    BenchmarkDelta delta) {
        out.println("Benchmark A/B (baseline=scoring, selected=" + selected.policyName() + ")");
        out.println("Mask source: " + selected.maskSource());
        out.println("Quality (Hard Gates): collisions=" + selected.collisionCount()
            + ", uncovered=" + selected.uncoveredRequiredCount()
            + ", outsideCoverage=" + selected.outsideTargetCoverageCount()
            + ", shellLeak=" + selected.shellLeakCount());
        out.println("Slope Behavior: slopePlacements=" + selected.slopePlacementCount()
            + ", flatSlopeErrors=" + selected.flatSlopeErrorCount()
            + ", facingConsistency=" + String.format(Locale.ROOT, "%.3f", selected.slopeFacingConsistency()));
        out.println("Typed Collisions: overlap=" + selected.overlapPlacementCount()
            + ", outsideCoverage=" + selected.outsideCoveragePlacementCount()
            + ", angleMismatch=" + selected.slopeAngleMismatchPlacementCount()
            + ", facingMismatch=" + selected.slopeFacingMismatchPlacementCount()
            + ", missingNormal=" + selected.slopeMissingNormalPlacementCount()
            + ", shadowIntrusion=" + selected.slopeShadowIntrusionPlacementCount()
            + ", tallFlatAdjacent=" + selected.slopeAdjacentTallFlatConflictPlacementCount()
            + ", shellLeakResidualVoxels=" + selected.shellLeakResidualVoxelCount());
        out.println("Efficiency: pieceCount=" + selected.pieceCount()
            + ", runtimeMs=" + selected.runtimeMs()
            + ", peakCandidates=" + selected.peakCandidateCount());
        out.println("Color: meanDeltaE=" + String.format(Locale.ROOT, "%.3f", selected.meanDeltaE())
            + ", p95DeltaE=" + String.format(Locale.ROOT, "%.3f", selected.p95DeltaE()));
        out.println("Delta vs scoring: pieceCount=" + delta.pieceCountDelta()
            + ", runtimeMs=" + delta.runtimeDeltaMs()
            + ", shellLeak=" + delta.shellLeakDelta()
            + ", flatSlopeErrors=" + delta.flatSlopeErrorDelta());
        out.println("Typed delta vs scoring:"
            + " overlap=" + delta.overlapPlacementDelta()
            + ", outsideCoverage=" + delta.outsideCoveragePlacementDelta()
            + ", angleMismatch=" + delta.slopeAngleMismatchPlacementDelta()
            + ", facingMismatch=" + delta.slopeFacingMismatchPlacementDelta()
            + ", missingNormal=" + delta.slopeMissingNormalPlacementDelta()
            + ", shadowIntrusion=" + delta.slopeShadowIntrusionPlacementDelta()
            + ", tallFlatAdjacent=" + delta.slopeAdjacentTallFlatConflictPlacementDelta()
            + ", shellLeakResidualVoxels=" + delta.shellLeakResidualVoxelDelta());
        out.println("Works-better criteria:"
            + " hardGatesZero=" + delta.hardGatesZero()
            + ", typedHardGatesZero=" + delta.typedHardGatesZero()
            + ", flatSlopeErrorLower=" + delta.flatSlopeErrorLower()
            + ", shellLeakLower=" + delta.shellLeakLower()
            + ", pieceCountLE=" + delta.pieceCountLeWhenHardGates()
            + ", meanDeltaELE=" + delta.meanDeltaELeWhenAvailable()
            + ", runtimeWithin4x=" + delta.runtimeWithin4x());
        out.println("Benchmark artifacts: " + dir.toAbsolutePath());
    }

    private record BenchmarkDelta(
        String selectedPolicy,
        String maskSource,
        int collisionDelta,
        int uncoveredDelta,
        int outsideCoverageDelta,
        int shellLeakDelta,
        int flatSlopeErrorDelta,
        int overlapPlacementDelta,
        int outsideCoveragePlacementDelta,
        int slopeAngleMismatchPlacementDelta,
        int slopeFacingMismatchPlacementDelta,
        int slopeMissingNormalPlacementDelta,
        int slopeShadowIntrusionPlacementDelta,
        int slopeAdjacentTallFlatConflictPlacementDelta,
        int shellLeakResidualVoxelDelta,
        int pieceCountDelta,
        long runtimeDeltaMs,
        double meanDeltaEDelta,
        double p95DeltaEDelta,
        boolean hardGatesZero,
        boolean typedHardGatesZero,
        boolean flatSlopeErrorLower,
        boolean shellLeakLower,
        boolean pieceCountLeWhenHardGates,
        boolean meanDeltaELeWhenAvailable,
        boolean runtimeWithin4x
    ) {
        static BenchmarkDelta from(PlacementBenchmarkMetrics baseline, PlacementBenchmarkMetrics selected) {
            boolean hardGatesZero = selected.collisionCount() == 0
                && selected.uncoveredRequiredCount() == 0
                && selected.outsideTargetCoverageCount() == 0
                && selected.shellLeakCount() == 0;
            boolean typedHardGatesZero = selected.typedHardGatesZero();
            boolean flatSlopeLower = selected.flatSlopeErrorCount() < baseline.flatSlopeErrorCount();
            boolean shellLeakLower = selected.shellLeakCount() < baseline.shellLeakCount();
            boolean pieceCountLe = !hardGatesZero || selected.pieceCount() <= baseline.pieceCount();
            boolean meanDeltaLe;
            if (baseline.meanDeltaE() < 0 || selected.meanDeltaE() < 0) {
                meanDeltaLe = true;
            } else {
                meanDeltaLe = selected.meanDeltaE() <= baseline.meanDeltaE();
            }
            boolean runtime4x = baseline.runtimeMs() == 0 || selected.runtimeMs() <= baseline.runtimeMs() * 4L;

            return new BenchmarkDelta(
                selected.policyName(),
                selected.maskSource(),
                selected.collisionCount() - baseline.collisionCount(),
                selected.uncoveredRequiredCount() - baseline.uncoveredRequiredCount(),
                selected.outsideTargetCoverageCount() - baseline.outsideTargetCoverageCount(),
                selected.shellLeakCount() - baseline.shellLeakCount(),
                selected.flatSlopeErrorCount() - baseline.flatSlopeErrorCount(),
                selected.overlapPlacementCount() - baseline.overlapPlacementCount(),
                selected.outsideCoveragePlacementCount() - baseline.outsideCoveragePlacementCount(),
                selected.slopeAngleMismatchPlacementCount() - baseline.slopeAngleMismatchPlacementCount(),
                selected.slopeFacingMismatchPlacementCount() - baseline.slopeFacingMismatchPlacementCount(),
                selected.slopeMissingNormalPlacementCount() - baseline.slopeMissingNormalPlacementCount(),
                selected.slopeShadowIntrusionPlacementCount() - baseline.slopeShadowIntrusionPlacementCount(),
                selected.slopeAdjacentTallFlatConflictPlacementCount() - baseline.slopeAdjacentTallFlatConflictPlacementCount(),
                selected.shellLeakResidualVoxelCount() - baseline.shellLeakResidualVoxelCount(),
                selected.pieceCount() - baseline.pieceCount(),
                selected.runtimeMs() - baseline.runtimeMs(),
                selected.meanDeltaE() - baseline.meanDeltaE(),
                selected.p95DeltaE() - baseline.p95DeltaE(),
                hardGatesZero,
                typedHardGatesZero,
                flatSlopeLower,
                shellLeakLower,
                pieceCountLe,
                meanDeltaLe,
                runtime4x
            );
        }
    }
}
