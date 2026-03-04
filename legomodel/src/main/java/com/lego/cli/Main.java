package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lego.export.BrickObjExporter;
import com.lego.export.VoxelObjExporter;
import com.lego.mesh.MeshNormalizer;
import com.lego.mesh.ObjLoader;
import com.lego.model.Brick;
import com.lego.model.Mesh;
import com.lego.optimize.AllowedBrickDimensions;
import com.lego.optimize.BrickPlacer;
import com.lego.voxel.SurfaceExtractor;
import com.lego.voxel.VoxelGrid;
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
        if (args == null || (args.length != 2 && args.length != 3 && args.length != 4 && args.length != 5)) {
            printUsage(err);
            return 1;
        }

        Path objPath = Path.of(args[0]);
        Path outputObjPath = args.length >= 3 ? Path.of(args[2]) : null;
        
        // Smart argument parsing: detect if arg is exportMode or voxelizerMode
        String exportMode = "brick";
        String voxelizerModeArg = "legacy";
        
        if (args.length >= 4) {
            String arg3 = args[3];
            // Check if arg3 is a voxelizer mode (not an export mode)
            if (arg3.equals("legacy") || arg3.equals("topological")) {
                voxelizerModeArg = arg3;
                // Keep exportMode as default "brick"
            } else {
                // arg3 is export mode
                exportMode = arg3;
            }
        }
        
        if (args.length == 5) {
            voxelizerModeArg = args[4];
        }
        
        int resolution;
        try {
            resolution = Integer.parseInt(args[1]);
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

        if (!exportMode.equals("brick") && !exportMode.equals("voxel-surface") && !exportMode.equals("voxel-solid")) {
            err.println("Error: export mode must be 'brick', 'voxel-surface', or 'voxel-solid'.");
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
                    }
                } catch (IOException e) {
                    err.println("Error: failed to write output OBJ file: " + e.getMessage());
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
        err.println("Usage: java -jar legomodel.jar <objPath> <resolution> [outputObjPath] [exportMode] [voxelizerMode]");
        err.println("  exportMode: 'brick' (default), 'voxel-surface', or 'voxel-solid'");
        err.println("  voxelizerMode: 'legacy' (default) or 'topological'");
    }

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
