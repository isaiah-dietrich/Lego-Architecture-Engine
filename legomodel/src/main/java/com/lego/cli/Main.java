package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lego.export.BrickObjExporter;
import com.lego.export.LDrawExporter;
import com.lego.export.VoxelObjExporter;
import com.lego.mesh.MeshNormalizer;
import com.lego.mesh.ObjLoader;
import com.lego.model.Brick;
import com.lego.model.Mesh;
import com.lego.optimize.AllowedBrickDimensions;
import com.lego.optimize.BrickPlacer;
import com.lego.voxel.SurfaceExtractor;
import com.lego.voxel.VoxelGrid;
import com.lego.voxel.VoxelSteppingAnalyzer;
import com.lego.voxel.VoxelSteppingAnalyzer.AnalysisMetadata;
import com.lego.voxel.VoxelSteppingAnalyzer.ResolutionSweepResult;
import com.lego.voxel.VoxelSteppingAnalyzer.VoxelSteppingMetrics;
import com.lego.voxel.VoxelizationStrategy;
import com.lego.voxel.Voxelizer;

/**
 * Command-line entry point for the LEGO Architecture Engine.
 */
public final class Main {

    private Main() {
        // Utility class, prevent instantiation
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, null);
    }

    /**
     * Internal overload for testing: accepts optional catalog base directory.
     * When baseDir is null, uses default catalog location.
     * 
     * @param args command-line arguments
     * @param out output stream for normal messages
     * @param err output stream for errors
     * @param catalogBaseDir optional base directory for catalog loading (test-only)
     * @return exit code
     */
    static int run(String[] args, PrintStream out, PrintStream err, Path catalogBaseDir) {
        if (args == null) {
            printUsage(err);
            return 1;
        }

        ParsedOptions parsedOptions;
        try {
            parsedOptions = parseCliOptions(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            printUsage(err);
            return 1;
        }

        List<String> positional = parsedOptions.positionalArgs();
        if (positional.size() < 2 || positional.size() > 5) {
            printUsage(err);
            return 1;
        }

        Path objPath = Path.of(positional.get(0));
        Path outputObjPath = positional.size() >= 3 ? Path.of(positional.get(2)) : null;

        String exportMode = "brick";
        String voxelizerModeArg = "topological";

        if (positional.size() >= 4) {
            String arg3 = positional.get(3);
            if (arg3.equals("legacy") || arg3.equals("topological")) {
                voxelizerModeArg = arg3;
            } else {
                exportMode = arg3;
            }
        }

        if (positional.size() == 5) {
            voxelizerModeArg = positional.get(4);
        }

        int resolution;
        try {
            resolution = Integer.parseInt(positional.get(1));
        } catch (NumberFormatException e) {
            err.println("Error: resolution must be an integer.");
            printUsage(err);
            return 1;
        }

        if (resolution < 2) {
            err.println("Error: resolution must be >= 2.");
            printUsage(err);
            return 1;
        }

        if (!exportMode.equals("brick")
            && !exportMode.equals("voxel-surface")
            && !exportMode.equals("voxel-solid")
            && !exportMode.equals("ldraw")) {
            err.println("Error: export mode must be 'brick', 'voxel-surface', 'voxel-solid', or 'ldraw'.");
            printUsage(err);
            return 1;
        }

        VoxelizationStrategy voxelizationStrategy;
        try {
            voxelizationStrategy = VoxelizationStrategy.fromCliValue(voxelizerModeArg);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            printUsage(err);
            return 1;
        }

        try {
            Mesh mesh = ObjLoader.load(objPath);
            Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
            VoxelGrid solid = Voxelizer.voxelize(normalized, resolution, voxelizationStrategy);

            // Topological mode produces surface-only grid; legacy mode requires surface extraction.
            VoxelGrid surface = (voxelizationStrategy == VoxelizationStrategy.TOPOLOGICAL_SURFACE)
                ? solid
                : SurfaceExtractor.extractSurface(solid);
            
            // Load dimensions from catalog (test-friendly with optional base dir)
            List<Brick> bricks = catalogBaseDir != null
                ? BrickPlacer.placeBricks(surface, AllowedBrickDimensions.loadFromCatalog(catalogBaseDir))
                : BrickPlacer.placeBricks(surface);

            int triangleCount = mesh.triangleCount();
            int totalVoxels = resolution * resolution * resolution;
            int surfaceVoxels = surface.countFilledVoxels();
            int brickCount = bricks.size();

            out.println("Triangles: " + triangleCount);
            out.println("Resolution: " + resolution + "x" + resolution + "x" + resolution);
            out.println("Total voxels: " + totalVoxels);
            out.println("Filled voxels (solid): " + solid.countFilledVoxels());
            out.println("Surface voxels: " + surfaceVoxels);
            out.println("Bricks generated: " + brickCount);

            // Print block type summary
            printBlockTypeSummary(bricks, out);

            if (surfaceVoxels > 0) {
                double reductionPercent = 100.0 * (surfaceVoxels - brickCount) / surfaceVoxels;
                out.printf("Reduction: %.1f%% (%d voxels -> %d bricks)%n",
                    reductionPercent, surfaceVoxels, brickCount);
            }

            if (outputObjPath != null) {
                try {
                    switch (exportMode) {
                        case "brick":
                            BrickObjExporter.export(bricks, outputObjPath);
                            out.println("Visual OBJ exported (brick): " + outputObjPath.toAbsolutePath());
                            break;
                        case "voxel-surface":
                            VoxelObjExporter.export(surface, outputObjPath);
                            out.println("Visual OBJ exported (voxel-surface): " + outputObjPath.toAbsolutePath());
                            break;
                        case "voxel-solid":
                            VoxelObjExporter.export(solid, outputObjPath);
                            out.println("Visual OBJ exported (voxel-solid): " + outputObjPath.toAbsolutePath());
                            break;
                        case "ldraw":
                            LDrawExporter.export(bricks, outputObjPath, catalogBaseDir);
                            out.println("LDraw exported: " + outputObjPath.toAbsolutePath());
                            break;
                    }
                } catch (IOException e) {
                    err.println("Error: failed to write output file: " + e.getMessage());
                    return 1;
                }
            }

            if (parsedOptions.analyzeStepping()) {
                Path analysisDir = resolveAnalysisDir(parsedOptions.analysisDir(), outputObjPath);
                try {
                    if (!parsedOptions.sweepResolutions().isEmpty()) {
                        ResolutionSweepResult sweepResult = VoxelSteppingAnalyzer.runResolutionSweep(
                            mesh,
                            objPath,
                            parsedOptions.sweepResolutions(),
                            voxelizationStrategy,
                            exportMode,
                            parsedOptions.largeJumpThreshold()
                        );

                        for (VoxelSteppingAnalyzer.SweepEntry entry : sweepResult.entries()) {
                            Path perResolutionDir = analysisDir.resolve("resolution_" + entry.resolution());
                            VoxelSteppingAnalyzer.writeMetricsJson(
                                entry.metrics(),
                                perResolutionDir.resolve("stepping_metrics.json")
                            );
                            VoxelSteppingAnalyzer.writeLayersCsv(
                                entry.metrics(),
                                perResolutionDir.resolve("stepping_layers.csv")
                            );
                        }

                        VoxelSteppingAnalyzer.writeSweepJson(sweepResult, analysisDir.resolve("stepping_sweep.json"));
                        VoxelSteppingAnalyzer.writeSweepCsv(sweepResult, analysisDir.resolve("stepping_sweep.csv"));
                        out.println("Stepping analysis sweep exported: " + analysisDir.toAbsolutePath());
                    } else {
                        AnalysisMetadata metadata = new AnalysisMetadata(
                            objPath.toString(),
                            resolution,
                            voxelizationStrategy.cliValue(),
                            exportMode,
                            Instant.now().toString()
                        );
                        VoxelSteppingMetrics metrics = VoxelSteppingAnalyzer.analyze(
                            solid,
                            surface,
                            metadata,
                            parsedOptions.largeJumpThreshold()
                        );

                        VoxelSteppingAnalyzer.writeMetricsJson(metrics, analysisDir.resolve("stepping_metrics.json"));
                        VoxelSteppingAnalyzer.writeLayersCsv(metrics, analysisDir.resolve("stepping_layers.csv"));
                        out.println("Stepping analysis exported: " + analysisDir.toAbsolutePath());
                    }
                } catch (IOException e) {
                    err.println("Error: failed to write stepping analysis files: " + e.getMessage());
                    return 1;
                }
            }

            return 0;
        } catch (IOException e) {
            err.println("Error: failed to read OBJ file: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } catch (UnsupportedOperationException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: java -jar legomodel.jar <objPath> <resolution> [outputObjPath] [exportMode] [voxelizerMode] [options]");
        err.println("  exportMode: 'brick' (default), 'voxel-surface', 'voxel-solid', or 'ldraw'");
        err.println("  voxelizerMode: 'topological' (default) or 'legacy'");
        err.println("  options:");
        err.println("    --analyze-stepping             Write stepping analysis files");
        err.println("    --analysis-dir=<path>          Output directory for analysis artifacts");
        err.println("    --jump-threshold=<int>         Large jump threshold (default: 25)");
        err.println("    --sweep=<r1,r2,...>            Analyze multiple resolutions (e.g., 10,20,30)");
    }

    private static ParsedOptions parseCliOptions(String[] args) {
        List<String> positional = new ArrayList<>();
        boolean analyzeStepping = false;
        Path analysisDir = null;
        int jumpThreshold = 25;
        List<Integer> sweepResolutions = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--analyze-stepping".equals(arg)) {
                analyzeStepping = true;
            } else if (arg.startsWith("--analysis-dir=")) {
                analysisDir = Path.of(arg.substring("--analysis-dir=".length()));
            } else if ("--analysis-dir".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--analysis-dir requires a value");
                }
                analysisDir = Path.of(args[++i]);
            } else if (arg.startsWith("--jump-threshold=")) {
                String value = arg.substring("--jump-threshold=".length());
                jumpThreshold = parseNonNegativeInt(value, "jump-threshold");
            } else if (arg.startsWith("--sweep=")) {
                String value = arg.substring("--sweep=".length());
                sweepResolutions = parseSweepResolutions(value);
            } else {
                positional.add(arg);
            }
        }

        return new ParsedOptions(positional, analyzeStepping, analysisDir, jumpThreshold, sweepResolutions);
    }

    private static Path resolveAnalysisDir(Path explicitAnalysisDir, Path outputObjPath) {
        if (explicitAnalysisDir != null) {
            return explicitAnalysisDir;
        }
        if (outputObjPath != null && outputObjPath.getParent() != null) {
            return outputObjPath.getParent();
        }
        return Path.of("output", "analysis");
    }

    private static int parseNonNegativeInt(String value, String fieldName) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        if (parsed < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return parsed;
    }

    private static List<Integer> parseSweepResolutions(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("sweep resolutions must not be empty");
        }

        String[] parts = csv.split(",");
        List<Integer> resolutions = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            int resolution = parseNonNegativeInt(trimmed, "sweep resolution");
            if (resolution < 2) {
                throw new IllegalArgumentException("sweep resolution must be >= 2");
            }
            resolutions.add(resolution);
        }
        return resolutions;
    }

    private record ParsedOptions(
        List<String> positionalArgs,
        boolean analyzeStepping,
        Path analysisDir,
        int largeJumpThreshold,
        List<Integer> sweepResolutions
    ) {}

    /**
     * Prints a summary of block types used, grouped by dimensions and sorted by volume.
     *
     * @param bricks the list of bricks generated
     * @param out    the output stream to print to
     */
    static void printBlockTypeSummary(List<Brick> bricks, PrintStream out) {
        // Group bricks by their dimensions (studX, studY, heightUnits)
        Map<String, Integer> blockTypeCounts = new HashMap<>();
        for (Brick brick : bricks) {
            String blockType = brick.studX() + "x" + brick.studY() + "x" + brick.heightUnits();
            blockTypeCounts.put(blockType, blockTypeCounts.getOrDefault(blockType, 0) + 1);
        }

        // Sort the block types by volume (descending), then by studX (descending),
        // then by studY (descending), then by heightUnits (descending)
        List<String> sortedBlockTypes = blockTypeCounts.keySet().stream()
            .sorted((a, b) -> {
                int[] dimsA = parseDimensions(a);
                int[] dimsB = parseDimensions(b);
                
                int volumeA = dimsA[0] * dimsA[1] * dimsA[2];
                int volumeB = dimsB[0] * dimsB[1] * dimsB[2];
                
                if (volumeA != volumeB) {
                    return Integer.compare(volumeB, volumeA); // descending
                }
                if (dimsA[0] != dimsB[0]) {
                    return Integer.compare(dimsB[0], dimsA[0]); // studX descending
                }
                if (dimsA[1] != dimsB[1]) {
                    return Integer.compare(dimsB[1], dimsA[1]); // studY descending
                }
                return Integer.compare(dimsB[2], dimsA[2]); // heightUnits descending
            })
            .collect(Collectors.toList());

        // Print the block type summary
        out.println("Block types used:");
        for (String blockType : sortedBlockTypes) {
            int count = blockTypeCounts.get(blockType);
            out.println(blockType + ": " + count);
        }
    }

    /**
     * Parses a block type string (e.g., "2x4x1") into an array of dimensions.
     *
     * @param blockType the block type string
     * @return an array of {studX, studY, heightUnits}
     */
    static int[] parseDimensions(String blockType) {
        String[] parts = blockType.split("x");
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
    }
}
