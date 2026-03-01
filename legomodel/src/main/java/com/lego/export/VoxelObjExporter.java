package com.lego.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lego.voxel.VoxelGrid;

/**
 * Exports voxel grids as triangulated OBJ meshes for visualization.
 * Each filled voxel is exported as a unit cuboid.
 */
public final class VoxelObjExporter {

    private VoxelObjExporter() {
        // Utility class, prevent instantiation
    }

    /**
     * Exports a voxel grid as a triangulated OBJ file.
     * Each filled voxel at (x,y,z) is exported as a unit cube [x,x+1] × [y,y+1] × [z,z+1].
     *
     * @param grid voxel grid (must be non-null)
     * @param outputPath destination OBJ path (must be non-null)
     * @throws IOException if writing fails
     */
    public static void export(VoxelGrid grid, Path outputPath) throws IOException {
        if (grid == null) {
            throw new IllegalArgumentException("grid must not be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        StringBuilder obj = new StringBuilder();
        obj.append("# LEGO Architecture Engine voxel export\n");
        obj.append("# voxel_count ").append(grid.countFilledVoxels()).append('\n');

        int vertexOffset = 1;
        int index = 0;
        for (int x = 0; x < grid.width(); x++) {
            for (int y = 0; y < grid.height(); y++) {
                for (int z = 0; z < grid.depth(); z++) {
                    if (grid.isFilled(x, y, z)) {
                        appendVoxel(obj, x, y, z, index, vertexOffset);
                        vertexOffset += 8;
                        index++;
                    }
                }
            }
        }

        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }

    private static void appendVoxel(StringBuilder obj, int x, int y, int z, int index, int vertexOffset) {
        double x0 = x;
        double y0 = y;
        double z0 = z;
        double x1 = x + 1.0;
        double y1 = y + 1.0;
        double z1 = z + 1.0;

        obj.append("\n");
        obj.append("o voxel_").append(index).append('\n');

        // 8 cuboid vertices
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z0).append('\n'); // 1
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z0).append('\n'); // 2
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z0).append('\n'); // 3
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z0).append('\n'); // 4
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z1).append('\n'); // 5
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z1).append('\n'); // 6
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z1).append('\n'); // 7
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z1).append('\n'); // 8

        // 12 triangles (2 per face, outward-facing winding)
        // Bottom face (Z=z0, normal -Z)
        writeTri(obj, vertexOffset, 1, 3, 2);
        writeTri(obj, vertexOffset, 1, 4, 3);

        // Top face (Z=z1, normal +Z)
        writeTri(obj, vertexOffset, 5, 6, 7);
        writeTri(obj, vertexOffset, 5, 7, 8);

        // Front face (Y=y0, normal -Y)
        writeTri(obj, vertexOffset, 1, 5, 6);
        writeTri(obj, vertexOffset, 1, 6, 2);

        // Right face (X=x1, normal +X)
        writeTri(obj, vertexOffset, 2, 6, 7);
        writeTri(obj, vertexOffset, 2, 7, 3);

        // Back face (Y=y1, normal +Y)
        writeTri(obj, vertexOffset, 3, 7, 8);
        writeTri(obj, vertexOffset, 3, 8, 4);

        // Left face (X=x0, normal -X)
        writeTri(obj, vertexOffset, 4, 1, 5);
        writeTri(obj, vertexOffset, 4, 5, 8);
    }

    private static void writeTri(StringBuilder obj, int vertexOffset, int a, int b, int c) {
        obj.append("f ")
            .append(vertexOffset + a - 1).append(' ')
            .append(vertexOffset + b - 1).append(' ')
            .append(vertexOffset + c - 1).append('\n');
    }
}
