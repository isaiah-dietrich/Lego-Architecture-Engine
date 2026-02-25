package com.lego.mesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;

/**
 * Unit tests for ObjLoader.
 */
class ObjLoaderTest {

    @TempDir
    Path tempDir;

    /**
     * Tests that a simple OBJ file with one triangle is loaded correctly.
     * Verifies that the mesh contains exactly one triangle with the correct vertex coordinates.
     */
    @Test
    void testLoadValidTriangle() throws IOException {
        // Create a simple OBJ with one triangle
        String obj = """
            # Simple triangle
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            f 1 2 3
            """;

        Path path = tempDir.resolve("triangle.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(1, mesh.triangleCount());

        Triangle tri = mesh.triangles().get(0);
        assertEquals(new Vector3(0.0, 0.0, 0.0), tri.v1());
        assertEquals(new Vector3(1.0, 0.0, 0.0), tri.v2());
        assertEquals(new Vector3(0.0, 1.0, 0.0), tri.v3());
    }

    /**
     * Tests that an OBJ file with multiple triangles (quad split into two triangles)
     * is loaded correctly. Verifies the total triangle count.
     */
    @Test
    void testLoadMultipleTriangles() throws IOException {
        String obj = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            v 0.0 1.0 0.0
            f 1 2 3
            f 1 3 4
            """;

        Path path = tempDir.resolve("quad.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(2, mesh.triangleCount());
    }

    /**
     * Tests that the loader correctly ignores comment lines (starting with #)
     * and blank lines while still parsing the geometry correctly.
     */
    @Test
    void testIgnoresCommentsAndBlankLines() throws IOException {
        String obj = """
            # This is a comment
            
            v 0.0 0.0 0.0
            # Another comment
            v 1.0 0.0 0.0
            
            v 0.0 1.0 0.0
            
            # Face definition
            f 1 2 3
            """;

        Path path = tempDir.resolve("comments.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(1, mesh.triangleCount());
    }

    /**
     * Tests that the loader correctly ignores unsupported OBJ features including:
     * materials (mtllib, usemtl), object/group names (o, g), normals (vn),
     * texture coordinates (vt), and smoothing groups (s).
     */
    @Test
    void testIgnoresUnsupportedObjFeatures() throws IOException {
        String obj = """
            # OBJ with features we ignore
            mtllib materials.mtl
            o ObjectName
            g GroupName
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            vn 0.0 0.0 1.0
            vt 0.0 0.0
            usemtl Material
            s 1
            f 1 2 3
            """;

        Path path = tempDir.resolve("features.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(1, mesh.triangleCount());
    }

    /**
     * Tests that the loader correctly extracts vertex indices from face definitions
     * that include texture and normal indices (format: f v/vt/vn).
     * Verifies that texture and normal indices are ignored while vertex positions are preserved.
     */
    @Test
    void testFaceWithTextureAndNormalIndices() throws IOException {
        // Face format: f v/vt/vn
        String obj = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            vt 0.0 0.0
            vt 1.0 0.0
            vt 0.0 1.0
            vn 0.0 0.0 1.0
            f 1/1/1 2/2/1 3/3/1
            """;

        Path path = tempDir.resolve("texnorm.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(1, mesh.triangleCount());
        
        // Verify vertices are correctly extracted
        Triangle tri = mesh.triangles().get(0);
        assertEquals(new Vector3(0.0, 0.0, 0.0), tri.v1());
        assertEquals(new Vector3(1.0, 0.0, 0.0), tri.v2());
        assertEquals(new Vector3(0.0, 1.0, 0.0), tri.v3());
    }

    /**
     * Tests that the loader rejects faces with more than 3 vertices (quads, polygons).
     * Verifies that an IllegalArgumentException is thrown with an appropriate error message.
     */
    @Test
    void testQuadFaceThrowsException() throws IOException {
        String obj = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 1.0 1.0 0.0
            v 0.0 1.0 0.0
            f 1 2 3 4
            """;

        Path path = tempDir.resolve("quad.obj");
        Files.writeString(path, obj);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ObjLoader.load(path)
        );

        assertTrue(ex.getMessage().contains("Only triangulated faces"));
        assertTrue(ex.getMessage().contains("4 vertices"));
    }

    /**
     * Tests that the loader rejects face definitions with vertex indices that exceed
     * the number of defined vertices. Verifies appropriate exception is thrown.
     */
    @Test
    void testOutOfRangeIndexThrowsException() throws IOException {
        String obj = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            f 1 2 5
            """;

        Path path = tempDir.resolve("bad_index.obj");
        Files.writeString(path, obj);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ObjLoader.load(path)
        );

        assertTrue(ex.getMessage().contains("out of range"));
    }

    /**
     * Tests that the loader rejects face definitions with zero or negative vertex indices.
     * OBJ format uses 1-based indexing, so zero is invalid.
     */
    @Test
    void testZeroIndexThrowsException() throws IOException {
        String obj = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            f 0 1 2
            """;

        Path path = tempDir.resolve("zero_index.obj");
        Files.writeString(path, obj);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ObjLoader.load(path)
        );

        assertTrue(ex.getMessage().contains("must be positive"));
    }

    /**
     * Tests that vertices with negative coordinates are accepted as valid.
     * Negative coordinates are common in centered or normalized meshes.
     */
    @Test
    void testNegativeCoordinatesAreValid() throws IOException {
        String obj = """
            v -1.0 -2.0 -3.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            f 1 2 3
            """;

        Path path = tempDir.resolve("negative.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(1, mesh.triangleCount());
        Triangle tri = mesh.triangles().get(0);
        assertEquals(new Vector3(-1.0, -2.0, -3.0), tri.v1());
    }

    /**
     * Tests that the loader rejects vertex lines with non-numeric coordinate values.
     * Verifies that an IllegalArgumentException is thrown with an appropriate error message.
     */
    @Test
    void testInvalidVertexFormatThrowsException() throws IOException {
        String obj = """
            v not_a_number 0.0 0.0
            """;

        Path path = tempDir.resolve("bad_vertex.obj");
        Files.writeString(path, obj);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ObjLoader.load(path)
        );

        assertTrue(ex.getMessage().contains("Invalid number"));
    }

    /**
     * Tests that an OBJ file with no geometry (only comments and blank lines)
     * produces an empty but valid mesh with zero triangles.
     */
    @Test
    void testEmptyFileProducesEmptyMesh() throws IOException {
        String obj = """
            # Only comments
            
            """;

        Path path = tempDir.resolve("empty.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(0, mesh.triangleCount());
    }

    /**
     * Tests loading a more complex mesh (unit cube) with 8 vertices and 12 triangles.
     * Verifies that all faces are parsed correctly and the triangle count is accurate.
     */
    @Test
    void testCubeWithEightVertices() throws IOException {
        String obj = """
            # Unit cube centered at origin
            v -0.5 -0.5 -0.5
            v  0.5 -0.5 -0.5
            v  0.5  0.5 -0.5
            v -0.5  0.5 -0.5
            v -0.5 -0.5  0.5
            v  0.5 -0.5  0.5
            v  0.5  0.5  0.5
            v -0.5  0.5  0.5
            
            # 12 triangles (2 per face, 6 faces)
            f 1 2 3
            f 1 3 4
            f 5 6 7
            f 5 7 8
            f 1 2 6
            f 1 6 5
            f 2 3 7
            f 2 7 6
            f 3 4 8
            f 3 8 7
            f 4 1 5
            f 4 5 8
            """;

        Path path = tempDir.resolve("cube.obj");
        Files.writeString(path, obj);

        Mesh mesh = ObjLoader.load(path);

        assertEquals(12, mesh.triangleCount());
    }
}
