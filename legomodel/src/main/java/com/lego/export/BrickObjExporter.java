package com.lego.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.lego.model.Brick;

/**
 * Exports placed bricks as a triangulated OBJ mesh for visualization.
 */
public final class BrickObjExporter {

    private BrickObjExporter() {
        // Utility class, prevent instantiation
    }

    /**
     * Exports bricks as a triangulated OBJ file.
     *
     * @param bricks list of bricks (must be non-null)
     * @param outputPath destination OBJ path (must be non-null)
     * @throws IOException if writing fails
     */
    public static void export(List<Brick> bricks, Path outputPath) throws IOException {
        if (bricks == null) {
            throw new IllegalArgumentException("bricks must not be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath must not be null");
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        StringBuilder obj = new StringBuilder();
        obj.append("# LEGO Architecture Engine brick export\n");
        obj.append("# brick_count ").append(bricks.size()).append('\n');

        int vertexOffset = 1;
        int index = 0;
        for (Brick brick : bricks) {
            if (brick == null) {
                throw new IllegalArgumentException(
                    "brick at index " + index + " must not be null"
                );
            }
            ObjCuboidWriter.appendCuboid(obj, "brick_" + index,
                brick.x(), brick.y(), brick.z(),
                brick.maxX(), brick.maxY(), brick.maxZ(),
                vertexOffset);
            vertexOffset += ObjCuboidWriter.VERTICES_PER_CUBOID;
            index++;
        }

        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }
}
