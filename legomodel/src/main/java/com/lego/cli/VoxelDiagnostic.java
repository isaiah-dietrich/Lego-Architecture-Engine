package com.lego.cli;

import java.nio.file.Path;

import com.lego.mesh.MeshNormalizer;
import com.lego.mesh.ObjLoader;
import com.lego.model.Mesh;
import com.lego.voxel.SurfaceExtractor;
import com.lego.voxel.VoxelGrid;
import com.lego.voxel.Voxelizer;

/**
 * Diagnostic tool to analyze voxel patterns on specific layers.
 */
public final class VoxelDiagnostic {

    private VoxelDiagnostic() {
        // Utility class
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java VoxelDiagnostic <objPath> <resolution>");
            System.exit(1);
        }

        Path objPath = Path.of(args[0]);
        int resolution = Integer.parseInt(args[1]);

        Mesh mesh = ObjLoader.load(objPath);
        Mesh normalized = MeshNormalizer.normalize(mesh, resolution);
        VoxelGrid solid = Voxelizer.voxelize(normalized, resolution);
        VoxelGrid surface = SurfaceExtractor.extractSurface(solid);

        System.out.println("Resolution: " + resolution);
        System.out.println("Solid voxels: " + solid.countFilledVoxels());
        System.out.println("Surface voxels: " + surface.countFilledVoxels());
        System.out.println();

        // Show first and last Z layers (triangular ends)
        System.out.println("=== SURFACE VOXELS AT Z=0 (first triangular end) ===");
        printLayer(surface, 0);
        
        System.out.println("\n=== SURFACE VOXELS AT Z=" + (resolution - 1) + " (last triangular end) ===");
        printLayer(surface, resolution - 1);

        // Show middle layer for comparison
        System.out.println("\n=== SURFACE VOXELS AT Z=" + (resolution / 2) + " (middle) ===");
        printLayer(surface, resolution / 2);
    }

    private static void printLayer(VoxelGrid grid, int z) {
        int width = grid.width();
        int height = grid.height();
        
        // Print Y from top to bottom for visual clarity
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                System.out.print(grid.isFilled(x, y, z) ? "█" : "·");
            }
            System.out.println();
        }
    }
}
