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
            appendBrick(obj, brick, index, vertexOffset);
            vertexOffset += 8;
            index++;
        }

        Files.writeString(outputPath, obj.toString(), StandardCharsets.UTF_8);
    }

    private static void appendBrick(StringBuilder obj, Brick brick, int index, int vertexOffset) {
        double x0 = brick.x();
        double y0 = brick.y();
        double z0 = brick.z();
        double x1 = brick.maxX();
        double y1 = brick.maxY();
        double z1 = brick.maxZ();

        obj.append("\n");
        obj.append("o brick_").append(index).append('\n');

        // 8 cuboid vertices
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z0).append('\n'); // 1
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z0).append('\n'); // 2
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z0).append('\n'); // 3
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z0).append('\n'); // 4
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z1).append('\n'); // 5
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z1).append('\n'); // 6
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z1).append('\n'); // 7
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z1).append('\n'); // 8

        // 12 triangles (2 per face)
        writeTri(obj, vertexOffset, 1, 2, 3);
        writeTri(obj, vertexOffset, 1, 3, 4);

        writeTri(obj, vertexOffset, 5, 7, 6);
        writeTri(obj, vertexOffset, 5, 8, 7);

        writeTri(obj, vertexOffset, 1, 6, 2);
        writeTri(obj, vertexOffset, 1, 5, 6);

        writeTri(obj, vertexOffset, 2, 7, 3);
        writeTri(obj, vertexOffset, 2, 6, 7);

        writeTri(obj, vertexOffset, 3, 8, 4);
        writeTri(obj, vertexOffset, 3, 7, 8);

        writeTri(obj, vertexOffset, 4, 5, 1);
        writeTri(obj, vertexOffset, 4, 8, 5);
    }

    private static void writeTri(StringBuilder obj, int vertexOffset, int a, int b, int c) {
        obj.append("f ")
            .append(vertexOffset + a - 1).append(' ')
            .append(vertexOffset + b - 1).append(' ')
            .append(vertexOffset + c - 1).append('\n');
    }
}
