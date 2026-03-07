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
    void majorityVoteAcrossMultipleTriangles() {
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

        assertEquals(red, result.get(brick), "Red should win by majority vote (2 vs 1)");
    }
}
