package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

import com.lego.color.LegoPaletteMapper;
import com.lego.data.CatalogPartRepository;
import com.lego.export.BrickObjExporter;
import com.lego.export.LDrawExporter;
import com.lego.export.VoxelObjExporter;
import com.lego.model.Brick;
import com.lego.voxel.VoxelGrid;

/**
 * Routes export requests to the appropriate exporter based on the export mode.
 * Extracted from {@code Main} to separate export dispatch from pipeline orchestration.
 */
final class ExportCoordinator {

    private ExportCoordinator() {}

    /**
     * Runs the appropriate export for the given mode.
     *
     * @param request           the pipeline request
     * @param result            the pipeline result
     * @param solid             the solid voxel grid
     * @param surface           the surface voxel grid
     * @param palette           palette mapper (may be null for non-LDraw exports)
     * @param catalogRepository catalog part data source for LDraw export
     * @param out               output stream for success messages
     * @throws IOException      if writing fails
     */
    static void export(
        PipelineRequest request,
        PipelineResult result,
        VoxelGrid solid,
        VoxelGrid surface,
        LegoPaletteMapper palette,
        CatalogPartRepository catalogRepository,
        PrintStream out
    ) throws IOException {
        Path outputPath = request.outputPath();

        switch (request.exportMode()) {
            case "brick" -> {
                BrickObjExporter.export(result.bricks(), outputPath);
                out.println("Visual OBJ exported (brick): " + outputPath.toAbsolutePath());
            }
            case "voxel-surface" -> {
                VoxelObjExporter.export(surface, outputPath);
                out.println("Visual OBJ exported (voxel-surface): " + outputPath.toAbsolutePath());
            }
            case "voxel-solid" -> {
                VoxelObjExporter.export(solid, outputPath);
                out.println("Visual OBJ exported (voxel-solid): " + outputPath.toAbsolutePath());
            }
            case "ldraw" -> {
                LDrawExporter.export(result.bricks(), outputPath, catalogRepository, result.brickColorCodes());
                out.println("LDraw exported: " + outputPath.toAbsolutePath());

                if (request.colorList()) {
                    OutputReporter.printColorList(result.brickColorCodes(), palette, out);
                }
            }
        }
    }
}
