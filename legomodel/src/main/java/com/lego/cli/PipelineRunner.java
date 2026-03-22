package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

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
import com.lego.optimize.GreedyAreaPolicy;
import com.lego.optimize.PlacementFeatureGrid;
import com.lego.optimize.PlacementPolicy;
import com.lego.optimize.ScoringPlacementPolicy;
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
            LoadedModel loaded = loader.load(request.modelPath());
            Mesh mesh = loaded.mesh();
            Mesh normalized = MeshNormalizer.normalize(mesh, request.resolution());
            VoxelGrid solid = Voxelizer.voxelize(normalized, request.resolution(), request.voxelizationStrategy());

            VoxelGrid surface = (request.voxelizationStrategy() == VoxelizationStrategy.TOPOLOGICAL_SURFACE)
                ? solid
                : SurfaceExtractor.extractSurface(solid);

            PlacementPolicy placementPolicy = resolvePolicy(request.placementPolicy());

            // Color-aware scoring: sample voxel colors before placement
            if (placementPolicy instanceof ScoringPlacementPolicy
                    && "glb-color".equals(request.colorMode())
                    && loaded.colorMap().isPresent()) {
                ColorRgb[][][] voxelColors = ColorSampler.sampleVoxelColorGridDominant(
                    mesh, normalized, loaded.colorMap().get(), surface, request.resolution());
                LegoPaletteMapper paletteForVariance = paletteRepository.loadPalette();
                PlacementFeatureGrid featureGrid = ColorFeatureGridFactory.create(voxelColors, paletteForVariance);
                placementPolicy = new ScoringPlacementPolicy(featureGrid);
            }

            var allowedDims = AllowedBrickDimensions.loadFromRepository(catalogRepository);
            List<Brick> bricks = BrickPlacer.placeBricks(surface, allowedDims, placementPolicy);

            // Colorize bricks if in LDraw + glb-color mode
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

            OutputReporter.printSummary(result, out);

            if (request.outputPath() != null) {
                try {
                    if ("ldraw".equals(request.exportMode()) && brickColorCodes != null) {
                        OutputReporter.printColorInfo(result, out);
                    }
                    ExportCoordinator.export(request, result, solid, surface, palette, catalogRepository, out);
                } catch (IOException e) {
                    err.println("Error: failed to write output file: " + e.getMessage());
                    return 1;
                }
            }

            if (request.analyzeStepping()) {
                try {
                    AnalysisCoordinator.runAnalysis(request, mesh, solid, surface, out);
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

    /** Resolves a PlacementPolicy by name. */
    private static PlacementPolicy resolvePolicy(String name) {
        return switch (name.toLowerCase()) {
            case "scoring" -> new ScoringPlacementPolicy();
            case "greedy-area" -> new GreedyAreaPolicy();
            default -> throw new IllegalArgumentException(
                "Unknown placement policy: '" + name + "'. Use 'scoring' or 'greedy-area'."
            );
        };
    }
}
