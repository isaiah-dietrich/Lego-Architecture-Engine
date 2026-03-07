package com.lego.color;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.lego.model.Brick;
import com.lego.model.ColorRgb;
import com.lego.model.Mesh;
import com.lego.model.Triangle;
import com.lego.model.Vector3;
import com.lego.voxel.VoxelGrid;

class ColorSamplerTest {

    @Test
    void remapColorsTransfersByIndex() {
        Triangle origTri = new Triangle(
            new Vector3(0, 0, 0), new Vector3(10, 0, 0), new Vector3(0, 10, 0)
        );
        Triangle normTri = new Triangle(
            new Vector3(0, 0, 0), new Vector3(1, 0, 0), new Vector3(0, 1, 0)
        );

        Mesh original = new Mesh(List.of(origTri));
        Mesh normalized = new Mesh(List.of(normTri));

        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        Map<Triangle, ColorRgb> colorMap = Map.of(origTri, red);

        Map<Triangle, ColorRgb> remapped = ColorSampler.remapColors(original, normalized, colorMap);

        assertEquals(1, remapped.size());
        assertEquals(red, remapped.get(normTri));
    }

    @Test
    void singleTriangleSingleBrickGetsColor() {
        // Create a triangle that spans voxel (0,0,0) — center (0.5, 0.5, 0.5)
        Triangle tri = new Triangle(
            new Vector3(0, 0, 0),
            new Vector3(2, 0, 0),
            new Vector3(0, 2, 0)
        );
        Mesh mesh = new Mesh(List.of(tri));

        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        colorMap.put(tri, red);

        // Mark voxel (0,0,0) as filled
        VoxelGrid surface = new VoxelGrid(4, 4, 4);
        surface.setFilled(0, 0, 0, true);

        // Brick covering voxel (0,0,0)
        Brick brick = new Brick(0, 0, 0, 1, 1, 1);

        Map<Brick, ColorRgb> result = ColorSampler.sampleBrickColors(
            mesh, mesh, colorMap, surface, List.of(brick), 4
        );

        assertEquals(1, result.size());
        assertEquals(red, result.get(brick));
    }

    @Test
    void brickWithNoOverlappingTrianglesGetsNoColor() {
        // Triangle far from the brick
        Triangle tri = new Triangle(
            new Vector3(10, 10, 10),
            new Vector3(12, 10, 10),
            new Vector3(10, 12, 10)
        );
        Mesh mesh = new Mesh(List.of(tri));

        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        colorMap.put(tri, red);

        VoxelGrid surface = new VoxelGrid(4, 4, 4);
        surface.setFilled(0, 0, 0, true);

        Brick brick = new Brick(0, 0, 0, 1, 1, 1);

        Map<Brick, ColorRgb> result = ColorSampler.sampleBrickColors(
            mesh, mesh, colorMap, surface, List.of(brick), 4
        );

        assertTrue(result.isEmpty() || result.get(brick) == null,
            "Brick should have no color when no triangles overlap its voxels");
    }

    @Test
    void averageColorAcrossMultipleTriangles() {
        // Two red triangles and one blue triangle all overlapping the same voxel
        Triangle t1 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(2, 0, 0), new Vector3(0, 2, 0)
        );
        Triangle t2 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(0, 0, 2), new Vector3(0, 2, 0)
        );
        Triangle t3 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(2, 0, 0), new Vector3(0, 0, 2)
        );
        Mesh mesh = new Mesh(List.of(t1, t2, t3));

        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        ColorRgb blue = new ColorRgb(0f, 0f, 1f);
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        colorMap.put(t1, red);
        colorMap.put(t2, red);
        colorMap.put(t3, blue);

        VoxelGrid surface = new VoxelGrid(4, 4, 4);
        surface.setFilled(0, 0, 0, true);

        Brick brick = new Brick(0, 0, 0, 1, 1, 1);

        Map<Brick, ColorRgb> result = ColorSampler.sampleBrickColors(
            mesh, mesh, colorMap, surface, List.of(brick), 4
        );

        ColorRgb color = result.get(brick);
        assertNotNull(color, "Brick should have a color");
        // Average of 2 red (1,0,0) + 1 blue (0,0,1) = (2/3, 0, 1/3)
        assertEquals(2f / 3f, color.r(), 0.01f, "Red channel should be ~0.667");
        assertEquals(0f, color.g(), 0.01f, "Green channel should be 0");
        assertEquals(1f / 3f, color.b(), 0.01f, "Blue channel should be ~0.333");
    }

    @Test
    void multipleBricksGetDistinctColorsFromDifferentTriangles() {
        // Two triangles in different spatial regions with different colors
        Triangle t1 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(2, 0, 0), new Vector3(0, 2, 0)
        );
        Triangle t2 = new Triangle(
            new Vector3(3, 0, 0), new Vector3(5, 0, 0), new Vector3(3, 2, 0)
        );
        Mesh mesh = new Mesh(List.of(t1, t2));

        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        ColorRgb blue = new ColorRgb(0f, 0f, 1f);
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        colorMap.put(t1, red);
        colorMap.put(t2, blue);

        VoxelGrid surface = new VoxelGrid(6, 6, 6);
        surface.setFilled(0, 0, 0, true);
        surface.setFilled(3, 0, 0, true);

        Brick brick1 = new Brick(0, 0, 0, 1, 1, 1);
        Brick brick2 = new Brick(3, 0, 0, 1, 1, 1);

        Map<Brick, ColorRgb> result = ColorSampler.sampleBrickColors(
            mesh, mesh, colorMap, surface, List.of(brick1, brick2), 6
        );

        assertEquals(red, result.get(brick1), "Brick1 should be red from t1");
        assertEquals(blue, result.get(brick2), "Brick2 should be blue from t2");
    }

    @Test
    void remapColorsSkipsTrianglesWithNoColorEntry() {
        Triangle origTri1 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(10, 0, 0), new Vector3(0, 10, 0)
        );
        Triangle origTri2 = new Triangle(
            new Vector3(1, 0, 0), new Vector3(11, 0, 0), new Vector3(1, 10, 0)
        );
        Triangle normTri1 = new Triangle(
            new Vector3(0, 0, 0), new Vector3(1, 0, 0), new Vector3(0, 1, 0)
        );
        Triangle normTri2 = new Triangle(
            new Vector3(0.1, 0, 0), new Vector3(1.1, 0, 0), new Vector3(0.1, 1, 0)
        );

        Mesh original = new Mesh(List.of(origTri1, origTri2));
        Mesh normalized = new Mesh(List.of(normTri1, normTri2));

        // Only map color for the first triangle
        ColorRgb red = new ColorRgb(1f, 0f, 0f);
        Map<Triangle, ColorRgb> colorMap = new HashMap<>();
        colorMap.put(origTri1, red);

        Map<Triangle, ColorRgb> remapped = ColorSampler.remapColors(original, normalized, colorMap);

        assertEquals(1, remapped.size(), "Only mapped triangle should be in result");
        assertEquals(red, remapped.get(normTri1));
        assertNull(remapped.get(normTri2), "Unmapped triangle should not be in result");
    }
}
