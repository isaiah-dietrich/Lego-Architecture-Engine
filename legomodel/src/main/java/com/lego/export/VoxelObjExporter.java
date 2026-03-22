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

    /** Non-instantiable utility class. */
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
        appendGrid(obj, grid, "voxel", 1);
        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Exports two voxel grids into a single OBJ for overlay/inspection.
     *
     * @param surface full surface voxel grid
     * @param slopeMask slope-eligible voxel mask (same dimensions as surface)
     * @param outputPath destination OBJ path
     * @throws IOException if writing fails
     */
    public static void exportCombined(VoxelGrid surface, VoxelGrid slopeMask, Path outputPath) throws IOException {
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }
        if (slopeMask == null) {
            throw new IllegalArgumentException("slopeMask must not be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }
        if (surface.width() != slopeMask.width()
                || surface.height() != slopeMask.height()
                || surface.depth() != slopeMask.depth()) {
            throw new IllegalArgumentException("surface and slopeMask dimensions must match");
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        StringBuilder obj = new StringBuilder();
        obj.append("# LEGO Architecture Engine combined voxel export\n");
        obj.append("# surface_voxel_count ").append(surface.countFilledVoxels()).append('\n');
        obj.append("# slope_voxel_count ").append(slopeMask.countFilledVoxels()).append('\n');

        int nextVertexOffset = appendGrid(obj, surface, "surface_voxel", 1);
        appendGrid(obj, slopeMask, "slope_voxel", nextVertexOffset);

        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }

    private static int appendGrid(StringBuilder obj, VoxelGrid grid, String objectPrefix, int startVertexOffset) {
        int vertexOffset = startVertexOffset;
        int index = 0;
        for (int x = 0; x < grid.width(); x++) {
            for (int y = 0; y < grid.height(); y++) {
                for (int z = 0; z < grid.depth(); z++) {
                    if (grid.isFilled(x, y, z)) {
                        ObjCuboidWriter.appendCuboid(obj, objectPrefix + "_" + index,
                            x, y, z, x + 1.0, y + 1.0, z + 1.0, vertexOffset);
                        vertexOffset += ObjCuboidWriter.VERTICES_PER_CUBOID;
                        index++;
                    }
                }
            }
        }
        return vertexOffset;
    }
}
