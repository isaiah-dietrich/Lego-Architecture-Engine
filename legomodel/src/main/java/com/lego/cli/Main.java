package com.lego.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import com.lego.color.ColorStrategyRegistry;
import com.lego.data.CatalogPartRepository;
import com.lego.data.CsvCatalogPartRepository;
import com.lego.data.CsvPaletteRepository;
import com.lego.data.PaletteRepository;
import com.lego.mesh.GlbLoader;
import com.lego.mesh.ModelLoader;
import com.lego.mesh.ObjModelLoader;
import com.lego.voxel.VoxelizationStrategy;

/**
 * Command-line entry point for the LEGO Architecture Engine.
 *
 * This class is a thin composition root: it parses arguments, validates them,
 * builds a PipelineRequest, and delegates execution to
 * PipelineRunner.
 */
public final class Main {

    /** Non-instantiable entry-point class. */
    private Main() {}

    /** Application entry point. */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Runs the pipeline, returning the process exit code. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, null);
    }

    /** Runs the pipeline with an optional catalog base directory override. */
    static int run(String[] args, PrintStream out, PrintStream err, Path catalogBaseDir) {
        if (args == null) {
            OutputReporter.printUsage(err);
            return 1;
        }

        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                OutputReporter.printUsage(out);
                return 0;
            }
        }

        ParsedOptions parsedOptions;
        try {
            parsedOptions = CliOptionsParser.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            OutputReporter.printUsage(err);
            return 1;
        }

        ColorStrategyRegistry strategyRegistry = ColorStrategyRegistry.createDefault();
        if ("list".equals(parsedOptions.colorAlgorithm())) {
            out.println("Available color algorithms:");
            for (var entry : strategyRegistry.all().entrySet()) {
                String marker = entry.getKey().equals(strategyRegistry.defaultName()) ? " (default)" : "";
                out.printf("  %-20s %s%s%n", entry.getKey(), entry.getValue().description(), marker);
            }
            return 0;
        }

        // Validate positional arguments and build PipelineRequest
        PipelineRequest request;
        try {
            request = buildRequest(parsedOptions, strategyRegistry, catalogBaseDir, err);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            OutputReporter.printUsage(err);
            return 1;
        }
        if (request == null) {
            return 1; // error already printed
        }

        ModelLoader loader = resolveLoader(request.modelPath());
        CatalogPartRepository catalogRepository = catalogBaseDir != null
            ? new CsvCatalogPartRepository(catalogBaseDir)
            : new CsvCatalogPartRepository();
        PaletteRepository paletteRepository = catalogBaseDir != null
            ? new CsvPaletteRepository(catalogBaseDir.resolve("raw/rebrickable/colors.csv"))
            : new CsvPaletteRepository();
        return PipelineRunner.run(request, loader, strategyRegistry, catalogRepository, paletteRepository, out, err);
    }

    /** Builds a validated PipelineRequest from parsed CLI options. */
    private static PipelineRequest buildRequest(
        ParsedOptions opts,
        ColorStrategyRegistry strategyRegistry,
        Path catalogBaseDir,
        PrintStream err
    ) {
        List<String> positional = opts.positionalArgs();
        if (positional.size() < 2 || positional.size() > 5) {
            OutputReporter.printUsage(err);
            return null;
        }

        Path modelPath = Path.of(positional.get(0));
        Path outputPath = positional.size() >= 3 ? Path.of(positional.get(2)) : null;

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
            OutputReporter.printUsage(err);
            return null;
        }
        if (resolution < 2) {
            err.println("Error: resolution must be >= 2.");
            OutputReporter.printUsage(err);
            return null;
        }

        if (!exportMode.equals("brick")
            && !exportMode.equals("voxel-surface")
            && !exportMode.equals("voxel-solid")
            && !exportMode.equals("voxel-slope-surface")
            && !exportMode.equals("voxel-surface-combined")
            && !exportMode.equals("voxel-slope-placed")
            && !exportMode.equals("ldraw")) {
            err.println("Error: export mode must be 'brick', 'voxel-surface', 'voxel-solid', 'voxel-slope-surface', 'voxel-surface-combined', 'voxel-slope-placed', or 'ldraw'.");
            OutputReporter.printUsage(err);
            return null;
        }

        VoxelizationStrategy voxelizationStrategy;
        try {
            voxelizationStrategy = VoxelizationStrategy.fromCliValue(voxelizerModeArg);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            OutputReporter.printUsage(err);
            return null;
        }
        if (voxelizationStrategy == VoxelizationStrategy.LEGACY && !isLegacyAvailable()) {
            err.println("Error: legacy features are not available in this build. Rebuild with -Plegacy (or -Pfull).");
            return null;
        }
        if (usesToolingFeatures(opts) && !isToolingAvailable()) {
            err.println("Error: tooling features are not available in this build. Rebuild with -Ptooling (or -Pfull).");
            return null;
        }

        String colorAlgorithm = opts.colorAlgorithm();
        if (!strategyRegistry.availableNames().contains(colorAlgorithm.toLowerCase())) {
            err.println("Error: Unknown color algorithm: '" + colorAlgorithm
                + "'. Available: " + strategyRegistry.availableNames());
            return null;
        }

        String colorMode = opts.colorMode();
        if ("glb-color".equals(colorMode)) {
            String filename = modelPath.getFileName().toString().toLowerCase();
            if (filename.endsWith(".obj") && opts.colorModeExplicit()) {
                err.println("Error: --color-mode=glb-color is not supported with .obj input. OBJ files have no color channel.");
                return null;
            }
            if (filename.endsWith(".obj")) {
                colorMode = "none";
            }
        }

        return new PipelineRequest(
            modelPath,
            resolution,
            outputPath,
            exportMode,
            voxelizationStrategy,
            colorMode,
            opts.colorFallback(),
            opts.colorList(),
            colorAlgorithm,
            opts.analyzeStepping(),
            opts.analysisDir(),
            opts.largeJumpThreshold(),
            opts.sweepResolutions(),
            opts.benchmarkAb(),
            opts.benchmarkDir(),
            opts.ldrawLibraryDir(),
            opts.geometryMaskCacheDir()
        );
    }

    private static boolean usesToolingFeatures(ParsedOptions opts) {
        return opts.analyzeStepping()
            || opts.analysisDir() != null
            || opts.largeJumpThreshold() != 25
            || !opts.sweepResolutions().isEmpty()
            || opts.benchmarkAb()
            || opts.benchmarkDir() != null;
    }

    private static boolean isToolingAvailable() {
        try {
            Class.forName("com.lego.cli.PolicyBenchmarkRunner");
            Class.forName("com.lego.cli.AnalysisCoordinator");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isLegacyAvailable() {
        try {
            Class.forName("com.lego.voxel.LegacyVoxelizer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Selects the appropriate ModelLoader implementation based on the file extension. */
    private static ModelLoader resolveLoader(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".gltf")) {
            throw new IllegalArgumentException(
                "Unsupported format: .gltf files are not accepted. Convert to .glb first."
            );
        }
        if (name.endsWith(".glb")) {
            return new GlbLoader();
        }
        return new ObjModelLoader();
    }
}
