package com.lego.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PipelineBoundaryLayoutTest {

    @Test
    void toolingAndLegacyClassesAreNotInDefaultMainSourceSet() throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path mainJava = projectRoot.resolve("src/main/java");

        List<String> forbidden = List.of(
            "com/lego/cli/AnalysisCoordinator.java",
            "com/lego/cli/PolicyBenchmarkRunner.java",
            "com/lego/cli/PlacementBenchmarkCalculator.java",
            "com/lego/cli/PlacementBenchmarkMetrics.java",
            "com/lego/cli/VoxelDiagnostic.java",
            "com/lego/diag/TextureAnalyzer.java",
            "com/lego/voxel/ResolutionSweepRunner.java",
            "com/lego/voxel/SteppingAnalysisWriter.java",
            "com/lego/voxel/VoxelSteppingAnalyzer.java",
            "com/lego/voxel/LegacyVoxelizer.java"
        );

        for (String rel : forbidden) {
            assertTrue(
                !Files.exists(mainJava.resolve(rel)),
                "Expected non-core class to be outside src/main/java: " + rel
            );
        }
    }
}
