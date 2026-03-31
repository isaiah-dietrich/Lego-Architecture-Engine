package com.lego.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CoreDependencyBoundaryTest {

    private static final String[] FORBIDDEN_IMPORTS = {
        "import com.lego.cli.AnalysisCoordinator;",
        "import com.lego.cli.PlacementBenchmarkCalculator;",
        "import com.lego.cli.PlacementBenchmarkMetrics;",
        "import com.lego.cli.PolicyBenchmarkRunner;",
        "import com.lego.cli.VoxelDiagnostic;",
        "import com.lego.diag.TextureAnalyzer;",
        "import com.lego.voxel.LegacyVoxelizer;",
        "import com.lego.voxel.ResolutionSweepRunner;",
        "import com.lego.voxel.SteppingAnalysisWriter;",
        "import com.lego.voxel.VoxelSteppingAnalyzer;"
    };

    @Test
    void coreMainSourceDoesNotImportToolingOrLegacyClasses() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        Path mainJava = root.resolve("src/main/java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(mainJava)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> recordViolations(path, violations));
        }

        assertTrue(
            violations.isEmpty(),
            "Core boundary violations found:\n" + String.join("\n", violations)
        );
    }

    private static void recordViolations(Path sourceFile, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(sourceFile);
            for (String forbiddenImport : FORBIDDEN_IMPORTS) {
                if (lines.contains(forbiddenImport)) {
                    violations.add(sourceFile + " imports " + forbiddenImport);
                }
            }
        } catch (IOException e) {
            violations.add(sourceFile + " could not be read: " + e.getMessage());
        }
    }
}
