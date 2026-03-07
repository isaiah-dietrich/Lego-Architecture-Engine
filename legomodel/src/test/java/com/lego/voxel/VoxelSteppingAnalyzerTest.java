package com.lego.voxel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;
import com.lego.voxel.VoxelSteppingAnalyzer.AnalysisMetadata;
import com.lego.voxel.VoxelSteppingAnalyzer.ResolutionSweepResult;
import com.lego.voxel.VoxelSteppingAnalyzer.VoxelSteppingMetrics;

class VoxelSteppingAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void testDeterministicOutputForSameInput() {
        VoxelGrid grid = new VoxelGrid(4, 4, 4);
        fillCellsForLayerCount(grid, 0, 10);
        fillCellsForLayerCount(grid, 1, 8);
        fillCellsForLayerCount(grid, 2, 8);
        fillCellsForLayerCount(grid, 3, 5);

        AnalysisMetadata metadata = new AnalysisMetadata(
            "synthetic.obj",
            4,
            "legacy",
            "brick",
            "2026-03-04T00:00:00Z"
        );

        VoxelSteppingMetrics first = VoxelSteppingAnalyzer.analyze(grid, grid, metadata, 2);
        VoxelSteppingMetrics second = VoxelSteppingAnalyzer.analyze(grid, grid, metadata, 2);

        assertArrayEquals(first.filledVoxelsPerLayer(), second.filledVoxelsPerLayer());
        assertArrayEquals(first.surfaceVoxelsPerLayer(), second.surfaceVoxelsPerLayer());
        assertArrayEquals(first.deltas(), second.deltas());
        assertEquals(first.deltaStats(), second.deltaStats());
        assertEquals(first.jumpStats(), second.jumpStats());
        assertEquals(first.plateauStats(), second.plateauStats());
    }

    @Test
    void testKnownLayerProfileStats() {
        VoxelGrid grid = new VoxelGrid(4, 4, 4);
        fillCellsForLayerCount(grid, 0, 10);
        fillCellsForLayerCount(grid, 1, 8);
        fillCellsForLayerCount(grid, 2, 8);
        fillCellsForLayerCount(grid, 3, 5);

        VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
            grid,
            grid,
            new AnalysisMetadata("synthetic.obj", 4, "legacy", "brick", "2026-03-04T00:00:00Z"),
            2
        );

        assertArrayEquals(new int[] { 10, 8, 8, 5 }, metrics.surfaceVoxelsPerLayer());
        assertArrayEquals(new int[] { 2, 0, 3 }, metrics.deltas());

        assertEquals(0, metrics.deltaStats().min());
        assertEquals(3, metrics.deltaStats().max());
        assertEquals(3, metrics.jumpStats().largestAbsoluteJump());
        assertEquals(1, metrics.jumpStats().largeJumpCount());
        assertEquals(1, metrics.plateauStats().repeatedAdjacentLayers());
        assertEquals(2, metrics.plateauStats().longestPlateauLength());
    }

    @Test
    void testSymmetryMismatchForAsymmetricGrid() {
        VoxelGrid grid = new VoxelGrid(4, 4, 2);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(1, 2, 1, true);

        VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
            grid,
            grid,
            new AnalysisMetadata("asym.obj", 4, "legacy", "brick", "2026-03-04T00:00:00Z"),
            1
        );

        assertTrue(metrics.symmetryStats().xMismatchOverall() > 0);
        assertTrue(metrics.symmetryStats().yMismatchOverall() > 0);
    }

    @Test
    void testComponentCountSanity() {
        VoxelGrid grid = new VoxelGrid(4, 4, 1);
        grid.setFilled(0, 0, 0, true);
        grid.setFilled(3, 3, 0, true);

        VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
            grid,
            grid,
            new AnalysisMetadata("components.obj", 4, "legacy", "brick", "2026-03-04T00:00:00Z"),
            1
        );

        assertEquals(2, metrics.componentStats().overallFilledComponents());
        assertEquals(2, metrics.componentStats().overallSurfaceComponents());
        assertArrayEquals(new int[] { 2 }, metrics.componentStats().surfaceComponentsPerLayer());
    }

    @Test
    void testWritesJsonAndCsvArtifacts() throws IOException {
        VoxelGrid grid = new VoxelGrid(3, 3, 3);
        fillCellsForLayerCount(grid, 0, 4);
        fillCellsForLayerCount(grid, 1, 3);
        fillCellsForLayerCount(grid, 2, 2);

        VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
            grid,
            grid,
            new AnalysisMetadata("artifact.obj", 3, "legacy", "brick", "2026-03-04T00:00:00Z"),
            1
        );

        Path jsonPath = tempDir.resolve("stepping_metrics.json");
        Path csvPath = tempDir.resolve("stepping_layers.csv");

        VoxelSteppingAnalyzer.writeMetricsJson(metrics, jsonPath);
        VoxelSteppingAnalyzer.writeLayersCsv(metrics, csvPath);

        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(csvPath));

        String json = Files.readString(jsonPath);
        String csv = Files.readString(csvPath);
        assertTrue(json.contains("\"metadata\""));
        assertTrue(json.contains("\"layer_series\""));
        assertTrue(csv.contains("z,filled,surface,delta"));
    }

    @Test
    void testResolutionSweepProducesComparativeEntries() {
        Mesh mesh = createSingleTriangleMesh();

        ResolutionSweepResult sweep = VoxelSteppingAnalyzer.runResolutionSweep(
            mesh,
            Path.of("synthetic.obj"),
            List.of(10, 20),
            VoxelizationStrategy.LEGACY,
            "brick",
            5
        );

        assertEquals(2, sweep.entries().size());
        assertEquals(10, sweep.entries().get(0).resolution());
        assertEquals(20, sweep.entries().get(1).resolution());
    }

    private static Mesh createSingleTriangleMesh() {
        Triangle triangle = new Triangle(
            new Vector3(0, 0, 0),
            new Vector3(1, 0, 0),
            new Vector3(0, 1, 0)
        );
        return new Mesh(List.of(triangle));
    }

    private static void fillCellsForLayerCount(VoxelGrid grid, int z, int count) {
        int filled = 0;
        for (int x = 0; x < grid.width() && filled < count; x++) {
            for (int y = 0; y < grid.height() && filled < count; y++) {
                grid.setFilled(x, y, z, true);
                filled++;
            }
        }
    }
}
