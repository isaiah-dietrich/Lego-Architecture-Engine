package com.lego.ldraw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lego.model.Facing;
import com.lego.model.Triangle;
import com.lego.model.TriangleAabbTest;
import com.lego.model.Vector3;
import com.lego.optimize.AllowedBrickDimensions.BrickSpec;
import com.lego.optimize.PartMask;
import com.lego.optimize.PartMask.VoxelOffset;

/**
 * Ray-cast occupancy rasterizer for LDraw triangle meshes.
 */
public final class RaycastGeometryRasterizer implements GeometryRasterizer {

    private static final double EPS = 1e-9;
    private static final double CELL_X = 20.0;
    private static final double CELL_Y = 8.0;
    private static final double CELL_Z = 20.0;

    private final MaskNormalizer normalizer;

    public RaycastGeometryRasterizer() {
        this(new StandardMaskNormalizer());
    }

    public RaycastGeometryRasterizer(MaskNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public PartMask rasterize(PartGeometry geometry, BrickSpec spec, Facing facing, int studX, int studY) {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (facing == null) {
            throw new IllegalArgumentException("facing must not be null");
        }
        if (studX <= 0 || studY <= 0) {
            throw new IllegalArgumentException("studX/studY must be positive");
        }

        Rotation rotation = selectRotation(spec, facing, studX, studY);
        List<Triangle> oriented = rotateTriangles(geometry.triangles(), rotation);
        BBox box = BBox.fromTriangles(oriented);

        int nx = Math.max(1, (int) Math.ceil((box.maxX - box.minX) / CELL_X));
        int ny = Math.max(1, (int) Math.ceil((box.maxY - box.minY) / CELL_Y));
        int nz = Math.max(1, (int) Math.ceil((box.maxZ - box.minZ) / CELL_Z));

        List<VoxelOffset> occupied = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int ix = 0; ix < nx; ix++) {
            double cx = box.minX + (ix + 0.5) * CELL_X;
            for (int iy = 0; iy < ny; iy++) {
                double cy = box.minY + (iy + 0.5) * CELL_Y;
                for (int iz = 0; iz < nz; iz++) {
                    double cz = box.minZ + (iz + 0.5) * CELL_Z;
                    boolean occupiedBySurface = overlapsAnyTriangle(cx, cy, cz, oriented);
                    boolean occupiedByInterior = insideMesh(cx, cy, cz, oriented);
                    if (occupiedBySurface || occupiedByInterior) {
                        String key = ix + "," + iy + "," + iz;
                        if (seen.add(key)) {
                            occupied.add(new VoxelOffset(ix, iy, iz));
                        }
                    }
                }
            }
        }

        if (occupied.isEmpty()) {
            throw new LDrawException("Geometry rasterizer produced empty occupancy for part " + spec.partId());
        }

        List<VoxelOffset> normalized = normalizer.normalize(occupied);
        List<VoxelOffset> bounded = constrainToPlacementEnvelope(normalized, studX, spec.heightUnits(), studY);
        bounded = normalizer.normalize(bounded);
        validateExtents(spec, bounded, studX, studY);

        return new PartMask(bounded, bounded);
    }

    private static List<VoxelOffset> constrainToPlacementEnvelope(List<VoxelOffset> offsets,
                                                                  int maxX,
                                                                  int maxH,
                                                                  int maxY) {
        List<VoxelOffset> out = new ArrayList<>(offsets.size());
        for (VoxelOffset offset : offsets) {
            if (offset.dx() < 0 || offset.dy() < 0 || offset.dz() < 0) {
                continue;
            }
            if (offset.dx() >= maxX || offset.dy() >= maxH || offset.dz() >= maxY) {
                continue;
            }
            out.add(offset);
        }
        if (out.isEmpty()) {
            throw new LDrawException("Geometry occupancy outside placement envelope");
        }
        return out;
    }

    private static void validateExtents(BrickSpec spec, List<VoxelOffset> offsets, int expectedX, int expectedY) {
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (VoxelOffset offset : offsets) {
            maxX = Math.max(maxX, offset.dx());
            maxY = Math.max(maxY, offset.dy());
            maxZ = Math.max(maxZ, offset.dz());
        }

        int actualX = maxX + 1;
        int actualH = maxY + 1;
        int actualY = maxZ + 1;

        if (actualX > expectedX || actualY > expectedY) {
            throw new LDrawException("Footprint mismatch for part " + spec.partId()
                + ": expected <= " + expectedX + "x" + expectedY
                + ", got " + actualX + "x" + actualY);
        }
        if (actualH > spec.heightUnits()) {
            throw new LDrawException("Height mismatch for part " + spec.partId()
                + ": expected <=" + spec.heightUnits() + ", got " + actualH);
        }
    }

    private static Rotation selectRotation(BrickSpec spec, Facing facing, int studX, int studY) {
        if (spec.isSlope()) {
            return switch (facing) {
                case NORTH, NONE -> Rotation.IDENTITY;
                case EAST -> Rotation.Y270;
                case SOUTH -> Rotation.Y180;
                case WEST -> Rotation.Y90;
            };
        }

        if (spec.studX() == spec.studY()) {
            return Rotation.IDENTITY;
        }

        if (spec.studX() == studY && spec.studY() == studX) {
            return Rotation.IDENTITY;
        }
        if (spec.studX() == studX && spec.studY() == studY) {
            return Rotation.Y90;
        }

        throw new LDrawException("Cannot resolve non-slope rotation for part " + spec.partId()
            + " with oriented dimensions " + studX + "x" + studY);
    }

    private static List<Triangle> rotateTriangles(List<Triangle> triangles, Rotation rotation) {
        if (rotation == Rotation.IDENTITY) {
            return triangles;
        }
        List<Triangle> out = new ArrayList<>(triangles.size());
        for (Triangle t : triangles) {
            out.add(new Triangle(
                rotation.apply(t.v1()),
                rotation.apply(t.v2()),
                rotation.apply(t.v3())
            ));
        }
        return out;
    }

    private static boolean insideMesh(double px, double py, double pz, List<Triangle> triangles) {
        int hits = 0;
        for (Triangle tri : triangles) {
            if (rayHitsTriangle(px, py, pz, tri)) {
                hits++;
            }
        }
        return (hits % 2) == 1;
    }

    private static boolean overlapsAnyTriangle(double cx, double cy, double cz, List<Triangle> triangles) {
        double hx = CELL_X / 2.0;
        double hy = CELL_Y / 2.0;
        double hz = CELL_Z / 2.0;
        for (Triangle tri : triangles) {
            if (TriangleAabbTest.overlaps(tri, cx, cy, cz, hx, hy, hz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rayHitsTriangle(double px, double py, double pz, Triangle tri) {
        Vector3 a = tri.v1();
        Vector3 b = tri.v2();
        Vector3 c = tri.v3();

        double e1x = b.x() - a.x();
        double e1y = b.y() - a.y();
        double e1z = b.z() - a.z();

        double e2x = c.x() - a.x();
        double e2y = c.y() - a.y();
        double e2z = c.z() - a.z();

        // Ray direction: +X
        double hx = 0.0;
        double hy = -e2z;
        double hz = e2y;

        double det = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(det) < EPS) {
            return false;
        }

        double invDet = 1.0 / det;
        double sx = px - a.x();
        double sy = py - a.y();
        double sz = pz - a.z();

        double u = invDet * (sx * hx + sy * hy + sz * hz);
        if (u < -EPS || u > 1.0 + EPS) {
            return false;
        }

        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;

        double v = invDet * qx;
        if (v < -EPS || u + v > 1.0 + EPS) {
            return false;
        }

        double t = invDet * (e2x * qx + e2y * qy + e2z * qz);
        return t > EPS;
    }

    private enum Rotation {
        IDENTITY {
            @Override
            Vector3 apply(Vector3 in) {
                return in;
            }
        },
        Y90 {
            @Override
            Vector3 apply(Vector3 in) {
                return new Vector3(0 * in.x() + 0 * in.y() + 1 * in.z(), in.y(), -1 * in.x() + 0 * in.y() + 0 * in.z());
            }
        },
        Y180 {
            @Override
            Vector3 apply(Vector3 in) {
                return new Vector3(-in.x(), in.y(), -in.z());
            }
        },
        Y270 {
            @Override
            Vector3 apply(Vector3 in) {
                return new Vector3(0 * in.x() + 0 * in.y() -1 * in.z(), in.y(), 1 * in.x() + 0 * in.y() + 0 * in.z());
            }
        };

        abstract Vector3 apply(Vector3 in);
    }

    private static final class BBox {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        private BBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static BBox fromTriangles(List<Triangle> triangles) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (Triangle tri : triangles) {
                minX = Math.min(minX, Math.min(tri.v1().x(), Math.min(tri.v2().x(), tri.v3().x())));
                minY = Math.min(minY, Math.min(tri.v1().y(), Math.min(tri.v2().y(), tri.v3().y())));
                minZ = Math.min(minZ, Math.min(tri.v1().z(), Math.min(tri.v2().z(), tri.v3().z())));
                maxX = Math.max(maxX, Math.max(tri.v1().x(), Math.max(tri.v2().x(), tri.v3().x())));
                maxY = Math.max(maxY, Math.max(tri.v1().y(), Math.max(tri.v2().y(), tri.v3().y())));
                maxZ = Math.max(maxZ, Math.max(tri.v1().z(), Math.max(tri.v2().z(), tri.v3().z())));
            }
            return new BBox(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
