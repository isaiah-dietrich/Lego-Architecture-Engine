package com.lego.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.lego.color.BrickColorizer;
import com.lego.color.ColorFeatureGridFactory;
import com.lego.color.ColorSampler;
import com.lego.color.ColorStrategyRegistry;
import com.lego.color.ColorizationResult;
import com.lego.color.LegoPaletteMapper;
import com.lego.data.CatalogPartRepository;
import com.lego.data.PaletteRepository;
import com.lego.mesh.LoadedModel;
import com.lego.mesh.MeshNormalizer;
import com.lego.mesh.ModelLoader;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.optimize.AllowedBrickDimensions;
import com.lego.optimize.BrickPlacer;
import com.lego.optimize.GeometryPartMaskProvider;
import com.lego.optimize.MaskPlacementPolicy;
import com.lego.optimize.PartMaskProvider;
import com.lego.optimize.PlacementFeatureGrid;
import com.lego.optimize.PlacementStatsProvider;
import com.lego.voxel.SurfaceExtractor;
import com.lego.voxel.VoxelGrid;
import com.lego.voxel.VoxelizationStrategy;
import com.lego.voxel.Voxelizer;

/**
 * Orchestrates the end-to-end LEGO pipeline: load → voxelize → place → color → export.
 * Extracted from Main so the pipeline can be driven without CLI wiring.
 */
public final class PipelineRunner {

    /** Non-instantiable utility class. */
    private PipelineRunner() {}

    /**
     * Full result of the core pipeline, before printing or export.
     * Carries all intermediate state needed by both the CLI reporter and API layer.
     */
    private record CoreResult(
        PipelineResult pipelineResult,
        VoxelGrid solid,
        VoxelGrid surface,
        LegoPaletteMapper palette,
        Mesh mesh,
        PlacementFeatureGrid featureGrid,
        List<AllowedBrickDimensions.BrickSpec> allowedDims,
        String placementPolicyName,
        long placementRuntimeMs,
        int peakCandidateCount,
        PartMaskProvider maskProvider
    ) {}

    /**
     * Runs the full pipeline for the given request.
     *
     * @param request           validated pipeline request
     * @param loader            model loader for the file format
     * @param strategyRegistry  color strategy registry
     * @param catalogRepository catalog part data source
     * @param paletteRepository palette data source
     * @param out               output stream for progress/summary messages
     * @param err               error stream
     * @return 0 on success, non-zero on failure
     */
    public static int run(
        PipelineRequest request,
        ModelLoader loader,
        ColorStrategyRegistry strategyRegistry,
        CatalogPartRepository catalogRepository,
        PaletteRepository paletteRepository,
        PrintStream out,
        PrintStream err
    ) {
        try {
            CoreResult core = runCore(request, loader, strategyRegistry, catalogRepository, paletteRepository);
            PipelineResult result = core.pipelineResult();

            OutputReporter.printSummary(result, out);

            if (request.outputPath() != null) {
                try {
                    if ("ldraw".equals(request.exportMode()) && result.brickColorCodes() != null) {
                        OutputReporter.printColorInfo(result, out);
                    }
                    ExportCoordinator.export(request, result, core.solid(), core.surface(), core.palette(), catalogRepository, out);
                } catch (IOException e) {
                    err.println("Error: failed to write output file: " + e.getMessage());
                    return 1;
                }
            }

            if (request.analyzeStepping()) {
                try {
                    runToolingAnalysis(request, core.mesh(), core.solid(), core.surface(), out);
                } catch (IOException e) {
                    err.println("Error: failed to write stepping analysis files: " + e.getMessage());
                    return 1;
                }
            }

            if (request.benchmarkAb()) {
                try {
                    runToolingBenchmark(
                        request,
                        out,
                        core.surface(),
                        core.allowedDims(),
                        core.featureGrid(),
                        core.placementPolicyName(),
                        result.bricks(),
                        core.placementRuntimeMs(),
                        core.peakCandidateCount(),
                        core.maskProvider(),
                        MaskSource.GEOMETRY
                    );
                } catch (IOException e) {
                    err.println("Error: failed to write benchmark files: " + e.getMessage());
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

    /**
     * Runs the pipeline for programmatic (API) use.
     * Executes the full pipeline including export, and returns the result directly.
     * The request must have {@code outputPath} set to the desired output file path.
     *
     * @param request           validated pipeline request (must include outputPath)
     * @param loader            model loader for the file format
     * @param strategyRegistry  color strategy registry
     * @param catalogRepository catalog part data source
     * @param paletteRepository palette data source
     * @param stageCallback     called with a stage label as the pipeline progresses
     * @return the completed pipeline result
     * @throws IOException              on file read/write failure
     * @throws IllegalArgumentException on invalid input
     */
    public static PipelineResult runForApi(
        PipelineRequest request,
        ModelLoader loader,
        ColorStrategyRegistry strategyRegistry,
        CatalogPartRepository catalogRepository,
        PaletteRepository paletteRepository,
        Consumer<String> stageCallback
    ) throws IOException {
        stageCallback.accept("loading");
        CoreResult core = runCore(request, loader, strategyRegistry, catalogRepository, paletteRepository);
        stageCallback.accept("exporting");
        PrintStream nullOut = new PrintStream(OutputStream.nullOutputStream());
        ExportCoordinator.export(request, core.pipelineResult(), core.solid(), core.surface(), core.palette(), catalogRepository, nullOut);
        return core.pipelineResult();
    }

    /**
     * Core pipeline: load → voxelize → place → colorize.
     * Does not print or export. Throws on failure.
     */
    private static CoreResult runCore(
        PipelineRequest request,
        ModelLoader loader,
        ColorStrategyRegistry strategyRegistry,
        CatalogPartRepository catalogRepository,
        PaletteRepository paletteRepository
    ) throws IOException {
        LoadedModel loaded = loader.load(request.modelPath());
        Mesh mesh = loaded.mesh();
        Mesh normalized = MeshNormalizer.normalize(mesh, request.resolution());
        VoxelGrid solid = Voxelizer.voxelize(normalized, request.resolution(), request.voxelizationStrategy());

        VoxelGrid surface = (request.voxelizationStrategy() == VoxelizationStrategy.TOPOLOGICAL_SURFACE)
            ? solid
            : SurfaceExtractor.extractSurface(solid);

        PlacementFeatureGrid featureGrid = null;
        if ("glb-color".equals(request.colorMode()) && loaded.colorMap().isPresent()) {
            ColorRgb[][][] voxelColors = ColorSampler.sampleVoxelColorGridDominant(
                mesh, normalized, loaded.colorMap().get(), surface, request.resolution());
            LegoPaletteMapper paletteForVariance = paletteRepository.loadPalette();
            featureGrid = ColorFeatureGridFactory.create(voxelColors, paletteForVariance);
        }

        PartMaskProvider maskProvider = new GeometryPartMaskProvider(
            request.ldrawLibraryDir(), request.geometryMaskCacheDir());

        MaskPlacementPolicy placementPolicy = new MaskPlacementPolicy(featureGrid, maskProvider);
        var allowedDims = AllowedBrickDimensions.loadFromRepository(catalogRepository);
        long placementStartNanos = System.nanoTime();
        List<Brick> bricks = BrickPlacer.placeBricks(surface, allowedDims, placementPolicy);
        long placementRuntimeMs = (System.nanoTime() - placementStartNanos) / 1_000_000L;
        int peakCandidateCount = placementPolicy.peakCandidateCount();

        Map<Brick, Integer> brickColorCodes = null;
        LegoPaletteMapper palette = null;
        int coloredCount = 0;
        int smoothedCount = 0;

        if ("ldraw".equals(request.exportMode())
                && "glb-color".equals(request.colorMode())
                && loaded.colorMap().isPresent()) {
            BrickColorizer colorizer = new BrickColorizer(strategyRegistry, paletteRepository);
            ColorizationResult colorResult = colorizer.colorize(
                loaded, mesh, normalized, surface, bricks,
                request.resolution(), request.colorAlgorithm(), request.colorFallback());
            brickColorCodes = colorResult.brickColorCodes();
            palette = colorResult.palette();
            coloredCount = colorResult.coloredCount();
            smoothedCount = colorResult.smoothedCount();
        }

        PipelineResult result = new PipelineResult(
            mesh.triangleCount(),
            request.resolution(),
            request.resolution() * request.resolution() * request.resolution(),
            solid.countFilledVoxels(),
            surface.countFilledVoxels(),
            bricks,
            placementPolicy.name(),
            allowedDims,
            brickColorCodes,
            coloredCount,
            palette != null ? palette.opaqueEntryCount() : 0,
            request.colorAlgorithm(),
            smoothedCount
        );

        return new CoreResult(
            result, solid, surface, palette, mesh, featureGrid,
            allowedDims, placementPolicy.name(), placementRuntimeMs, peakCandidateCount, maskProvider
        );
    }

    private static void runToolingAnalysis(PipelineRequest request,
                                           Mesh mesh,
                                           VoxelGrid solid,
                                           VoxelGrid surface,
                                           PrintStream out) throws IOException {
        invokeToolingMethod(
            "com.lego.cli.AnalysisCoordinator",
            "runAnalysis",
            new Class<?>[] { PipelineRequest.class, Mesh.class, VoxelGrid.class, VoxelGrid.class, PrintStream.class },
            new Object[] { request, mesh, solid, surface, out }
        );
    }

    private static void runToolingBenchmark(PipelineRequest request,
                                            PrintStream out,
                                            VoxelGrid surface,
                                            List<AllowedBrickDimensions.BrickSpec> allowedDims,
                                            PlacementFeatureGrid featureGrid,
                                            String selectedPolicyName,
                                            List<Brick> selectedBricks,
                                            long selectedRuntimeMs,
                                            int selectedPeakCandidateCount,
                                            PartMaskProvider maskProvider,
                                            MaskSource maskSource) throws IOException {
        invokeToolingMethod(
            "com.lego.cli.PolicyBenchmarkRunner",
            "runAndWrite",
            new Class<?>[] {
                PipelineRequest.class, PrintStream.class, VoxelGrid.class,
                List.class, PlacementFeatureGrid.class, String.class, List.class,
                long.class, int.class, PartMaskProvider.class, MaskSource.class
            },
            new Object[] {
                request, out, surface, allowedDims, featureGrid, selectedPolicyName, selectedBricks,
                selectedRuntimeMs, selectedPeakCandidateCount, maskProvider, maskSource
            }
        );
    }

    private static void invokeToolingMethod(String className,
                                            String methodName,
                                            Class<?>[] parameterTypes,
                                            Object[] args) throws IOException {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Tooling features are not available in this build. Rebuild with -Ptooling.");
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Tooling feature '" + methodName + "' is incompatible: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Tooling feature '" + methodName + "' failed: " + cause.getMessage(), cause);
        }
    }
}
