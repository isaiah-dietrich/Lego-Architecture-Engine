package com.lego.export;

/**
 * Shared OBJ cuboid-writing primitives used by brick and voxel exporters.
 *
 * Appends 8 vertices and 12 triangulated faces for an axis-aligned cuboid
 * to a StringBuilder. Callers provide the object label and bounds.
 */
final class ObjCuboidWriter {

    /** Non-instantiable utility class. */
    private ObjCuboidWriter() {}

    /**
     * Appends a named cuboid object to the OBJ buffer.
     *
     * @param obj          output buffer
     * @param objectLabel  OBJ object name (e.g. "brick_0", "voxel_3")
     * @param x0           min X
     * @param y0           min Y
     * @param z0           min Z
     * @param x1           max X
     * @param y1           max Y
     * @param z1           max Z
     * @param vertexOffset 1-based vertex index offset for face references
     */
    static void appendCuboid(StringBuilder obj, String objectLabel,
                              double x0, double y0, double z0,
                              double x1, double y1, double z1,
                              int vertexOffset) {
        obj.append("\n");
        obj.append("o ").append(objectLabel).append('\n');

        // 8 cuboid vertices
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z0).append('\n');
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z0).append('\n');
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z0).append('\n');
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z0).append('\n');
        obj.append("v ").append(x0).append(' ').append(y0).append(' ').append(z1).append('\n');
        obj.append("v ").append(x1).append(' ').append(y0).append(' ').append(z1).append('\n');
        obj.append("v ").append(x1).append(' ').append(y1).append(' ').append(z1).append('\n');
        obj.append("v ").append(x0).append(' ').append(y1).append(' ').append(z1).append('\n');

        // 12 triangles (2 per face, outward-facing winding)
        writeTri(obj, vertexOffset, 1, 3, 2);  // Bottom (-Z)
        writeTri(obj, vertexOffset, 1, 4, 3);
        writeTri(obj, vertexOffset, 5, 6, 7);  // Top (+Z)
        writeTri(obj, vertexOffset, 5, 7, 8);
        writeTri(obj, vertexOffset, 1, 5, 6);  // Front (-Y)
        writeTri(obj, vertexOffset, 1, 6, 2);
        writeTri(obj, vertexOffset, 2, 6, 7);  // Right (+X)
        writeTri(obj, vertexOffset, 2, 7, 3);
        writeTri(obj, vertexOffset, 3, 7, 8);  // Back (+Y)
        writeTri(obj, vertexOffset, 3, 8, 4);
        writeTri(obj, vertexOffset, 4, 1, 5);  // Left (-X)
        writeTri(obj, vertexOffset, 4, 5, 8);
    }

    /** Vertices per cuboid. */
    static final int VERTICES_PER_CUBOID = 8;

    /** Writes a single triangle face to the OBJ output. */
    private static void writeTri(StringBuilder obj, int vertexOffset, int a, int b, int c) {
        obj.append("f ")
            .append(vertexOffset + a - 1).append(' ')
            .append(vertexOffset + b - 1).append(' ')
            .append(vertexOffset + c - 1).append('\n');
    }
}
