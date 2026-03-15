package com.lego.color;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.lego.data.PaletteRepository;
import com.lego.mesh.LoadedModel;
import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.voxel.VoxelGrid;

/**
 * Top-level colorization service that hides strategy dispatch, sampling,
 * fallback filling, and smoothing behind a single method call.
 *
 * <p>Replaces the {@code instanceof}-based dispatch that previously lived
 * inside the pipeline orchestrator.</p>
 */
public final class BrickColorizer {

    private final ColorStrategyRegistry strategyRegistry;
    private final PaletteRepository paletteRepository;

    public BrickColorizer(ColorStrategyRegistry strategyRegistry,
                          PaletteRepository paletteRepository) {
        this.strategyRegistry = strategyRegistry;
        this.paletteRepository = paletteRepository;
    }

    /**
     * Colorizes placed bricks using the requested algorithm.
     *
     * @param loaded         the loaded model (provides color map and textured triangles)
     * @param mesh           original mesh
     * @param normalized     mesh normalized to voxel space
     * @param surface        surface voxel grid
     * @param bricks         placed bricks
     * @param resolution     voxel grid resolution
     * @param colorAlgorithm name of the color algorithm to use
     * @param colorFallback  fallback LDraw color code, or negative to skip
     * @return colorization result with assignments, palette, and counts
     * @throws IOException if palette loading fails
     */
    public ColorizationResult colorize(
        LoadedModel loaded,
        Mesh mesh,
        Mesh normalized,
        VoxelGrid surface,
        List<Brick> bricks,
        int resolution,
        String colorAlgorithm,
        int colorFallback
    ) throws IOException {

        Map<Triangle, ColorRgb> triColorMap = loaded.colorMap()
            .orElseThrow(() -> new IllegalStateException("No color map available"));

        LegoPaletteMapper palette = paletteRepository.loadPalette();
        ColorStrategy strategy = strategyRegistry.get(colorAlgorithm);

        Map<Brick, Integer> brickColorCodes;
        int coloredCount;

        if (strategy instanceof SupersampledVoxelColorPipeline supersampledPipeline
                && loaded.texturedTriangles().isPresent()) {
            brickColorCodes = supersampledPipeline.colorize(
                normalized, loaded.texturedTriangles().get(),
                surface, bricks, resolution, palette, 64);
            coloredCount = brickColorCodes.size();
        } else if (strategy instanceof DominantVoteStrategy dominantStrategy) {
            Map<Brick, List<ColorRgb>> brickVoxelColors =
                ColorSampler.sampleBrickVoxelColors(
                    mesh, normalized, triColorMap, surface, bricks, resolution
                );
            brickColorCodes = dominantStrategy.applyWithVoxelColors(brickVoxelColors, palette);
            coloredCount = brickVoxelColors.size();
        } else {
            Map<Brick, ColorRgb> brickRgbColors = ColorSampler.sampleBrickColors(
                mesh, normalized, triColorMap, surface, bricks, resolution
            );
            brickColorCodes = strategy.apply(brickRgbColors, palette);
            coloredCount = brickRgbColors.size();
        }

        // Fallback filling
        if (colorFallback >= 0) {
            for (Brick brick : bricks) {
                brickColorCodes.putIfAbsent(brick, colorFallback);
            }
        }

        // Smoothing (skip for direct strategy which uses raw nearest-match)
        int smoothedCount = 0;
        if (!"direct".equals(strategy.name())) {
            smoothedCount = ColorSmoother.smoothIterative(brickColorCodes, bricks, 3, palette);
        }

        return new ColorizationResult(brickColorCodes, palette, coloredCount, smoothedCount);
    }
}
