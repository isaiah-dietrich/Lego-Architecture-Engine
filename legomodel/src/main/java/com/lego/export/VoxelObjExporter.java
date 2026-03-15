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

        int vertexOffset = 1;
        int index = 0;
        for (int x = 0; x < grid.width(); x++) {
            for (int y = 0; y < grid.height(); y++) {
                for (int z = 0; z < grid.depth(); z++) {
                    if (grid.isFilled(x, y, z)) {
                        ObjCuboidWriter.appendCuboid(obj, "voxel_" + index,
                            x, y, z, x + 1.0, y + 1.0, z + 1.0, vertexOffset);
                        vertexOffset += ObjCuboidWriter.VERTICES_PER_CUBOID;
                        index++;
                    }
                }
            }
        }

        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }
}
