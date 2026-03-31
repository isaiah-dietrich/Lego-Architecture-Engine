package com.lego.ldraw;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lego.model.Facing;
import com.lego.model.Triangle;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.GeometryPartMaskProvider;
import com.lego.optimize.PartMask;

class LDrawGeometryModuleTest {

    @TempDir
    Path tempDir;

    @Test
    void strictParser_parsesSubfileReferencesAndTracksDependencies() throws Exception {
        Path library = tempDir.resolve("ldraw");
        Files.createDirectories(library.resolve("parts"));
        Files.createDirectories(library.resolve("p"));

        Files.writeString(library.resolve("parts/3005.dat"), """
            0 Brick 1 x 1
            0 BFC CERTIFY CCW
            1 16 0 0 0 1 0 0 0 1 0 0 0 1 cube.dat
            """);
        Files.writeString(library.resolve("p/cube.dat"), cubeDat(0, 0, 0, 20, 24, 20));

        DatParser parser = new StrictDatParser(new FilesystemPartFileResolver(library));
        PartGeometry geometry = parser.parse("3005.dat");

        assertTrue(geometry.triangles().size() >= 12);
        assertTrue(geometry.dependencyFiles().stream().anyMatch(path -> path.endsWith("3005.dat")));
        assertTrue(geometry.dependencyFiles().stream().anyMatch(path -> path.endsWith("cube.dat")));
    }

    @Test
    void strictParser_rejectsMalformedNumbers() throws Exception {
        Path library = tempDir.resolve("ldraw");
        Files.createDirectories(library.resolve("parts"));

        Files.writeString(library.resolve("parts/bad.dat"), """
            0 malformed
            3 16 a 0 0 0 0 0 0 0 0
            """);

        DatParser parser = new StrictDatParser(new FilesystemPartFileResolver(library));
        LDrawException error = assertThrows(LDrawException.class, () -> parser.parse("bad.dat"));
        assertTrue(error.getMessage().contains("bad.dat"));
        assertTrue(error.getMessage().contains("Invalid number"));
    }

    @Test
    void strictParser_detectsIncludeCycles() throws Exception {
        Path library = tempDir.resolve("ldraw");
        Files.createDirectories(library.resolve("parts"));

        Files.writeString(library.resolve("parts/a.dat"), "1 16 0 0 0 1 0 0 0 1 0 0 0 1 b.dat\n");
        Files.writeString(library.resolve("parts/b.dat"), "1 16 0 0 0 1 0 0 0 1 0 0 0 1 a.dat\n");

        DatParser parser = new StrictDatParser(new FilesystemPartFileResolver(library));
        LDrawException error = assertThrows(LDrawException.class, () -> parser.parse("a.dat"));
        assertTrue(error.getMessage().contains("cycle"));
    }

    @Test
    void rasterizer_generatesExpectedFootprintForUnitBrick() {
        PartGeometry geometry = new PartGeometry(cuboidTriangles(0, 0, 0, 20, 24, 20), Set.of(Path.of("cube.dat")));
        BrickSpec spec = new BrickSpec(1, 1, 3, "Bricks", "3005");

        PartMask mask = new RaycastGeometryRasterizer().rasterize(geometry, spec, Facing.NONE, 1, 1);
        assertFalse(mask.solidOccupancyMask().isEmpty());

        int[] extents = extents(mask);
        assertEquals(1, extents[0]);
        assertEquals(1, extents[2]);
        assertTrue(extents[1] <= 3);
    }

    @Test
    void rasterizer_handlesSlopeFacingRotation() {
        PartGeometry geometry = new PartGeometry(cuboidTriangles(0, 0, 0, 20, 24, 40), Set.of(Path.of("slope.dat")));
        BrickSpec slope = new BrickSpec(2, 1, 3, "Bricks Sloped", "3040b", "Slope 45 2x1", 45.0);

        PartMask north = new RaycastGeometryRasterizer().rasterize(geometry, slope, Facing.NORTH, 1, 2);
        PartMask east = new RaycastGeometryRasterizer().rasterize(geometry, slope, Facing.EAST, 2, 1);

        int[] northExtents = extents(north);
        int[] eastExtents = extents(east);

        assertEquals(1, northExtents[0]);
        assertEquals(2, northExtents[2]);
        assertEquals(2, eastExtents[0]);
        assertEquals(1, eastExtents[2]);
    }

    @Test
    void layeredCacheStore_readsDiskAndBackfillsMemory() {
        MemoryMaskCacheStore memory = new MemoryMaskCacheStore();
        DiskMaskCacheStore disk = new DiskMaskCacheStore(tempDir.resolve("cache"));
        LayeredMaskCacheStore layered = new LayeredMaskCacheStore(memory, disk);

        PartMask mask = new PartMask(List.of(new PartMask.VoxelOffset(0, 0, 0)), List.of(new PartMask.VoxelOffset(0, 0, 0)));
        layered.put("abc", mask);

        assertTrue(layered.get("abc").isPresent());
        assertTrue(memory.get("abc").isPresent());
        assertTrue(disk.get("abc").isPresent());
    }

    @Test
    void geometryPartMaskProvider_loadsFromLocalDatLibrary() throws Exception {
        Path library = tempDir.resolve("ldraw");
        Files.createDirectories(library.resolve("parts"));
        Files.writeString(library.resolve("parts/3005.dat"), cubeDat(0, 0, 0, 20, 24, 20));

        GeometryPartMaskProvider provider = new GeometryPartMaskProvider(library, tempDir.resolve("cache"));
        BrickSpec spec = new BrickSpec(1, 1, 3, "Bricks", "3005");

        PartMask first = provider.getMask(spec, Facing.NONE, 1, 1);
        PartMask second = provider.getMask(spec, Facing.NONE, 1, 1);

        assertNotNull(first);
        assertEquals(first.solidOccupancyMask().size(), second.solidOccupancyMask().size());
        assertTrue(provider.isGeometryBacked());
    }

    private static int[] extents(PartMask mask) {
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (PartMask.VoxelOffset offset : mask.solidOccupancyMask()) {
            maxX = Math.max(maxX, offset.dx());
            maxY = Math.max(maxY, offset.dy());
            maxZ = Math.max(maxZ, offset.dz());
        }
        return new int[] { maxX + 1, maxY + 1, maxZ + 1 };
    }

    private static String cubeDat(double minX, double minY, double minZ, double sizeX, double sizeY, double sizeZ) {
        double maxX = minX + sizeX;
        double maxY = minY + sizeY;
        double maxZ = minZ + sizeZ;

        StringBuilder out = new StringBuilder();
        out.append("0 cube\n");
        out.append("0 BFC CERTIFY CCW\n");

        // bottom
        out.append(tri(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ));
        out.append(tri(minX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ));

        // top
        out.append(tri(minX, maxY, minZ, maxX, maxY, maxZ, maxX, maxY, minZ));
        out.append(tri(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ));

        // front
        out.append(tri(minX, minY, minZ, maxX, maxY, minZ, maxX, minY, minZ));
        out.append(tri(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ));

        // back
        out.append(tri(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ));
        out.append(tri(minX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ));

        // left
        out.append(tri(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ));
        out.append(tri(minX, minY, minZ, minX, maxY, maxZ, minX, maxY, minZ));

        // right
        out.append(tri(maxX, minY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ));
        out.append(tri(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ));
        return out.toString();
    }

    private static String tri(double x1, double y1, double z1,
                              double x2, double y2, double z2,
                              double x3, double y3, double z3) {
        return "3 16 " + x1 + " " + y1 + " " + z1 + " "
            + x2 + " " + y2 + " " + z2 + " "
            + x3 + " " + y3 + " " + z3 + "\n";
    }

    private static List<Triangle> cuboidTriangles(double minX, double minY, double minZ,
                                                   double sizeX, double sizeY, double sizeZ) {
        double maxX = minX + sizeX;
        double maxY = minY + sizeY;
        double maxZ = minZ + sizeZ;
        return List.of(
            // bottom
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(maxX, minY, minZ), new Vector3(maxX, minY, maxZ)),
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(maxX, minY, maxZ), new Vector3(minX, minY, maxZ)),
            // top
            new Triangle(new Vector3(minX, maxY, minZ), new Vector3(maxX, maxY, maxZ), new Vector3(maxX, maxY, minZ)),
            new Triangle(new Vector3(minX, maxY, minZ), new Vector3(minX, maxY, maxZ), new Vector3(maxX, maxY, maxZ)),
            // front
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(maxX, maxY, minZ), new Vector3(maxX, minY, minZ)),
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(minX, maxY, minZ), new Vector3(maxX, maxY, minZ)),
            // back
            new Triangle(new Vector3(minX, minY, maxZ), new Vector3(maxX, minY, maxZ), new Vector3(maxX, maxY, maxZ)),
            new Triangle(new Vector3(minX, minY, maxZ), new Vector3(maxX, maxY, maxZ), new Vector3(minX, maxY, maxZ)),
            // left
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(minX, minY, maxZ), new Vector3(minX, maxY, maxZ)),
            new Triangle(new Vector3(minX, minY, minZ), new Vector3(minX, maxY, maxZ), new Vector3(minX, maxY, minZ)),
            // right
            new Triangle(new Vector3(maxX, minY, minZ), new Vector3(maxX, maxY, maxZ), new Vector3(maxX, minY, maxZ)),
            new Triangle(new Vector3(maxX, minY, minZ), new Vector3(maxX, maxY, minZ), new Vector3(maxX, maxY, maxZ))
        );
    }
}
