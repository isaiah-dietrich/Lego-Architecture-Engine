package com.lego.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import com.lego.export.BrickObjExporter;
import com.lego.mesh.MeshNormalizer;
import com.lego.mesh.ObjLoader;
import com.lego.model.Brick;
import com.lego.model.Mesh;
import com.lego.optimize.BrickPlacer;
import com.lego.voxel.SurfaceExtractor;
import com.lego.voxel.VoxelGrid;
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
        if (args == null || (args.length != 2 && args.length != 3)) {
            printUsage(err);
            return 1;
        }

        Path objPath = Path.of(args[0]);
        Path outputObjPath = args.length == 3 ? Path.of(args[2]) : null;
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

        try {
            Mesh mesh = ObjLoader.load(objPath);
            Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
            VoxelGrid solid = Voxelizer.voxelize(normalized, resolution);
            VoxelGrid surface = SurfaceExtractor.extractSurface(solid);
            List<Brick> bricks = BrickPlacer.placeBricks(surface);

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

            if (surfaceVoxels > 0) {
                double reductionPercent = 100.0 * (surfaceVoxels - brickCount) / surfaceVoxels;
                out.printf("Reduction: %.1f%% (%d voxels -> %d bricks)%n",
                    reductionPercent, surfaceVoxels, brickCount);
            }

            if (outputObjPath != null) {
                try {
                    BrickObjExporter.export(bricks, outputObjPath);
                    out.println("Visual OBJ exported: " + outputObjPath.toAbsolutePath());
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
        }
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: java -jar legomodel.jar <objPath> <resolution> [outputObjPath]");
    }
}
