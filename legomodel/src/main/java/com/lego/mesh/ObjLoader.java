package com.lego.mesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * OBJ file loader for 3D mesh data.
 *
 * Supports a minimal subset of the OBJ format:
 * - Vertex lines (v x y z)
 * - Face lines (f i j k) — triangles, or (f i j k l) — quads (auto-triangulated)
 * TODO: Support n-gon obj files
 *
 * Quad faces (4 vertices) are automatically converted to 2 triangles using
 * deterministic fan triangulation: (v1, v2, v3) and (v1, v3, v4).
 *
 * All other OBJ features are ignored (normals, texture coords, materials, etc.).
 * Faces with 5+ vertices will cause an exception.
 */
public final class ObjLoader {

    private ObjLoader() {
        // Utility class, prevent instantiation
    }

    /**
     * Loads a mesh from an OBJ file.
     *
     * @param path path to the OBJ file
     * @return immutable Mesh containing triangles from the file
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if OBJ contains invalid face definitions
     */
    public static Mesh load(Path path) throws IOException {
        List<Vector3> vertices = new ArrayList<>();
        List<Triangle> triangles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] tokens = line.split("\\s+");
                String type = tokens[0];

                try {
                    if ("v".equals(type)) {
                        parseVertex(tokens, vertices);
                    } else if ("f".equals(type)) {
                        parseFace(tokens, vertices, triangles);
                    }
                    // Ignore all other line types (vn, vt, o, g, mtllib, usemtl, etc.)
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Error at line " + lineNumber + ": " + e.getMessage(), e
                    );
                }
            }
        }

        return new Mesh(triangles);
    }

    /**
     * Parses a vertex line: "v x y z"
     */
    private static void parseVertex(String[] tokens, List<Vector3> vertices) {
        if (tokens.length < 4) {
            throw new IllegalArgumentException(
                "Vertex line must have at least 3 coordinates"
            );
        }

        double x = parseDouble(tokens[1], "vertex x");
        double y = parseDouble(tokens[2], "vertex y");
        double z = parseDouble(tokens[3], "vertex z");

        vertices.add(new Vector3(x, y, z));
    }

    /**
     * Parses a face line: "f i j k"
     *
     * Only triangles (3 vertices) are supported.
     * OBJ indices are 1-based, so we subtract 1 to convert to 0-based.
     */
    private static void parseFace(
        String[] tokens,
        List<Vector3> vertices,
        List<Triangle> triangles
    ) {
        // Validate face has exactly 3 vertices (triangle)
        //TODO Change to N-Gon
        if (tokens.length < 4) {
            throw new IllegalArgumentException(
                "Face must have at least 3 vertices"
            );
        }

        if (tokens.length > 4) {
            throw new IllegalArgumentException(
                "Only triangulated faces are supported. " +
                "Face has " + (tokens.length - 1) + " vertices (expected 3)"
            );
        }

        // Parse vertex indices (OBJ uses 1-based indexing)
        int i1 = parseVertexIndex(tokens[1], vertices.size());
        int i2 = parseVertexIndex(tokens[2], vertices.size());
        int i3 = parseVertexIndex(tokens[3], vertices.size());

        Vector3 v1 = vertices.get(i1);
        Vector3 v2 = vertices.get(i2);
        Vector3 v3 = vertices.get(i3);

        triangles.add(new Triangle(v1, v2, v3));
    }

    /**
     * Parses a vertex index from a face element.
     *
     * OBJ face elements can be:
     * - "i" (vertex index only)
     * - "i/j" (vertex/texture)
     * - "i//k" (vertex//normal)
     * - "i/j/k" (vertex/texture/normal)
     *
     * We only care about the vertex index (first component).
     * OBJ uses 1-based indexing, so we convert to 0-based.
     */
    private static int parseVertexIndex(String token, int vertexCount) {
        // Split by "/" to extract vertex index
        String indexStr = token.split("/")[0];

        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid vertex index: " + token
            );
        }

        // OBJ uses 1-based indexing
        if (index < 1) {
            throw new IllegalArgumentException(
                "Vertex index must be positive (1-based), got: " + index
            );
        }

        // Convert to 0-based
        int zeroBasedIndex = index - 1;

        // Validate index is in range
        if (zeroBasedIndex >= vertexCount) {
            throw new IllegalArgumentException(
                "Vertex index " + index + " is out of range " +
                "(only " + vertexCount + " vertices defined)"
            );
        }

        return zeroBasedIndex;
    }

    /**
     * Parses a double value with context for error messages.
     */
    private static double parseDouble(String str, String context) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid number for " + context + ": " + str
            );
        }
    }
}
