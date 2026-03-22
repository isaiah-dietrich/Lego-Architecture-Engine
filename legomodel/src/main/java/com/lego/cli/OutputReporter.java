package com.lego.cli;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lego.color.LegoPaletteMapper;
import com.lego.model.Brick;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;

/**
 * Formats and prints pipeline summary output.
 * Extracted from Main to separate display from orchestration.
 */
final class OutputReporter {

    /** Non-instantiable utility class. */
    private OutputReporter() {}

    /** Prints the pipeline result summary (voxels, bricks, reduction). */
    static void printSummary(PipelineResult result, PrintStream out) {
        out.println("Triangles: " + result.triangleCount());
        out.println("Resolution: " + result.resolution() + "x" + result.resolution() + "x" + result.resolution());
        out.println("Total voxels: " + result.totalVoxels());
        out.println("Filled voxels (solid): " + result.solidVoxels());
        out.println("Surface voxels: " + result.surfaceVoxels());
        out.println("Bricks generated: " + result.bricks().size() + " (policy=" + result.placementPolicyName() + ")");

        printBlockTypeSummary(result.bricks(), result.allowedSpecs(), out);

        if (result.surfaceVoxels() > 0) {
            double reductionPercent = 100.0 * (result.surfaceVoxels() - result.bricks().size()) / result.surfaceVoxels();
            out.printf("Reduction: %.1f%% (%d voxels -> %d bricks)%n",
                reductionPercent, result.surfaceVoxels(), result.bricks().size());
        }
    }

    /** Prints color mode information for the pipeline result. */
    static void printColorInfo(PipelineResult result, PrintStream out) {
        if (result.brickColorCodes() != null) {
            out.println("Color mode: glb-color (" + result.coloredBrickCount()
                + "/" + result.bricks().size() + " bricks colored, "
                + result.opaquePaletteEntries() + " opaque palette entries"
                + ", algorithm=" + result.colorAlgorithmName()
                + (result.smoothedCount() > 0 ? ", " + result.smoothedCount() + " smoothed" : "") + ")");
        }
    }

    /** Prints a sorted list of unique LDraw color codes and names used in the export. */
    static void printColorList(Map<Brick, Integer> brickColorCodes, LegoPaletteMapper palette, PrintStream out) {
        if (brickColorCodes == null || brickColorCodes.isEmpty()) {
            out.println("Color list: (no colors - using default color 16)");
            return;
        }

        List<Integer> uniqueColors = brickColorCodes.values().stream()
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        out.println("Color list (" + uniqueColors.size() + " unique colors):");
        for (int color : uniqueColors) {
            long count = brickColorCodes.values().stream().filter(c -> c == color).count();
            String colorName = palette != null ? palette.getColorName(color) : "Unknown";
            out.printf("  %3d %-25s (%d bricks)%n", color, colorName, count);
        }
    }

    /** Prints a breakdown of block types and their counts. */
    static void printBlockTypeSummary(List<Brick> bricks, List<BrickSpec> allowedSpecs, PrintStream out) {
        Map<String, String> partNames = new HashMap<>();
        for (BrickSpec spec : allowedSpecs) {
            partNames.putIfAbsent(spec.partId(), spec.name());
        }

        Map<String, Integer> partCounts = new HashMap<>();
        for (Brick brick : bricks) {
            partCounts.merge(brick.partId(), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> sorted = partCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toList());

        out.println("Block types used:");
        for (Map.Entry<String, Integer> entry : sorted) {
            String partId = entry.getKey();
            int count = entry.getValue();
            String name = partNames.getOrDefault(partId, partId);
            out.printf("  %-6s %-30s x%d%n", partId, name, count);
        }
    }

    /** Prints command-line usage instructions. */
    static void printUsage(PrintStream stream) {
        stream.println("Usage: java -jar legomodel.jar <modelPath> <resolution> [outputObjPath] [exportMode] [voxelizerMode] [options]");
        stream.println("  modelPath: path to a .obj or .glb model file");
        stream.println("  resolution: voxel grid resolution (integer >= 2)");
        stream.println("  outputObjPath: path for the exported output file");
        stream.println("  exportMode: 'brick' (default), 'voxel-surface', 'voxel-solid', 'voxel-slope-surface', or 'ldraw'");
        stream.println("  voxelizerMode: 'topological' (default) or 'legacy'");
        stream.println("  options:");
        stream.println("    -h, --help                     Show this help message and exit");
        stream.println("    --analyze-stepping             Write stepping analysis files");
        stream.println("    --analysis-dir=<path>          Output directory for analysis artifacts");
        stream.println("    --jump-threshold=<int>         Large jump threshold (default: 25)");
        stream.println("    --sweep=<r1,r2,...>            Analyze multiple resolutions (e.g., 10,20,30)");
        stream.println("    --color-mode=<mode>            Color mode: 'none' (default) or 'glb-color'");
        stream.println("    --color-fallback=<code>        LDraw color code for bricks without sampled color");
        stream.println("    --color-list                   Output list of unique color codes used in LDraw export");
        stream.println("    --color-algorithm=<name>       Color mapping algorithm (default: direct). Use 'list' to see all.");
        stream.println("    --placement-policy=<name>      Brick placement policy: 'scoring' (default) or 'greedy-area'");
    }
}
