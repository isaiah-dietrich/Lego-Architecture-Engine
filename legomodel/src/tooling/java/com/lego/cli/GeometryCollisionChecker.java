package com.lego.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lego.data.CatalogPartRepository;
import com.lego.ldraw.GeometryTransform;
import com.lego.ldraw.PartGeometry;
import com.lego.ldraw.PartGeometryRepository;
import com.lego.model.Brick;
import com.lego.model.CatalogPart;
import com.lego.model.Facing;
import com.lego.model.Triangle;
import com.lego.model.TriangleAabbTest;
import com.lego.model.Vector3;

/**
 * Checks for geometry-space collisions between placed bricks by operating in LDraw units (LDU).
 *
 * Previous voxel-space checkers found no collisions because the voxel grid guarantees no two
 * bricks share the same cell. This checker operates on the actual LDraw triangle geometry at
 * world LDU coordinates — detecting overlaps invisible to the voxel model, such as a slope's
 * angled face physically intersecting an adjacent flat brick's body.
 *
 * <p>Strategy:
 * <ol>
 *   <li>For each brick, load its LDraw triangles, apply the same rotation + translation used
 *       by LDrawExporter, and compute a tight world-space AABB.</li>
 *   <li>Find candidate pairs whose world AABBs overlap beyond the touch-shrink threshold.</li>
 *   <li>For each candidate, sample the overlap region at {@value #SAMPLE_CELL} LDU resolution.
 *       A cell is "occupied" by a brick if any of its triangles overlap the cell (surface check)
 *       or if the cell centre is inside the closed mesh (interior ray-cast). A cell hit by both
 *       bricks is a collision.</li>
 * </ol>
 */
public final class GeometryCollisionChecker {

    private static final double STUD_LDU   = 20.0;
    private static final double HEIGHT_LDU =  8.0;

    // Sampling resolution: 4 LDU ≈ ⅕ stud — fine enough to catch real overlaps,
    // coarse enough that runtime stays tractable on a full model.
    private static final double SAMPLE_CELL = 4.0;

    // Faces that merely share a boundary are not collisions.
    // Shrink each world AABB by this amount before overlap testing.
    private static final double TOUCH_SHRINK = 1.0;

    // Ray-cast epsilon (matches RaycastGeometryRasterizer)
    private static final double EPS = 1e-9;

    public record Collision(Brick a, Brick b, double overlapDepthLdu) {
        @Override
        public String toString() {
            return String.format(
                "COLLISION  A=[%s %s voxel(%d,%d,%d) %dx%dx%d]  B=[%s %s voxel(%d,%d,%d) %dx%dx%d]  depth≈%.1f LDU",
                a.partId(), a.facing(), a.x(), a.y(), a.z(), a.studX(), a.studY(), a.heightUnits(),
                b.partId(), b.facing(), b.x(), b.y(), b.z(), b.studX(), b.studY(), b.heightUnits(),
                overlapDepthLdu
            );
        }
    }

    private final PartGeometryRepository geometryRepo;
    private final Map<String, CatalogPart> catalogById;

    public GeometryCollisionChecker(PartGeometryRepository geometryRepo, CatalogPartRepository catalog) {
        this.geometryRepo = geometryRepo;
        this.catalogById  = new HashMap<>();
        for (CatalogPart part : catalog.findActiveParts()) {
            catalogById.put(part.partId(), part);
        }
    }

    /**
     * Checks all pairs of bricks for geometry-space collisions.
     *
     * @param bricks placed brick list
     * @return detected collisions; empty list if none found
     */
    public List<Collision> check(List<Brick> bricks) {
        List<WorldBrick> world = new ArrayList<>(bricks.size());
        for (Brick brick : bricks) {
            WorldBrick wb = toWorldBrick(brick);
            if (wb != null) {
                world.add(wb);
            }
        }

        List<Collision> results = new ArrayList<>();
        for (int i = 0; i < world.size(); i++) {
            for (int j = i + 1; j < world.size(); j++) {
                WorldBrick a = world.get(i);
                WorldBrick b = world.get(j);
                if (!aabbOverlaps(a.aabb, b.aabb)) {
                    continue;
                }
                double depth = sampledOverlapDepth(a, b);
                if (depth > 0) {
                    results.add(new Collision(a.brick, b.brick, depth));
                }
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // World geometry construction
    // -------------------------------------------------------------------------

    private WorldBrick toWorldBrick(Brick brick) {
        if (Brick.UNKNOWN_PART_ID.equals(brick.partId())) {
            return null;
        }
        PartGeometry geometry;
        try {
            geometry = geometryRepo.load(brick.partId() + ".dat");
        } catch (Exception e) {
            return null; // part not in LDraw library
        }

        GeometryTransform rotation    = resolveRotation(brick);
        GeometryTransform translation = resolveTranslation(brick);
        // Apply rotation first, then translate: translation.compose(rotation).apply(v)
        GeometryTransform transform   = translation.compose(rotation);

        List<Triangle> worldTris = new ArrayList<>(geometry.triangles().size());
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (Triangle t : geometry.triangles()) {
            Vector3 w1 = transform.apply(t.v1());
            Vector3 w2 = transform.apply(t.v2());
            Vector3 w3 = transform.apply(t.v3());
            worldTris.add(new Triangle(w1, w2, w3));
            minX = Math.min(minX, Math.min(w1.x(), Math.min(w2.x(), w3.x())));
            minY = Math.min(minY, Math.min(w1.y(), Math.min(w2.y(), w3.y())));
            minZ = Math.min(minZ, Math.min(w1.z(), Math.min(w2.z(), w3.z())));
            maxX = Math.max(maxX, Math.max(w1.x(), Math.max(w2.x(), w3.x())));
            maxY = Math.max(maxY, Math.max(w1.y(), Math.max(w2.y(), w3.y())));
            maxZ = Math.max(maxZ, Math.max(w1.z(), Math.max(w2.z(), w3.z())));
        }

        return new WorldBrick(brick, worldTris, new Aabb(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Resolves the rotation transform, matching LDrawExporter's convention exactly:
     * <ul>
     *   <li>Slopes: NORTH=identity, EAST=Y270, SOUTH=Y180, WEST=Y90</li>
     *   <li>Flat: Y90 when catalog (studX,studY) == placed (studX,studY), identity otherwise</li>
     * </ul>
     */
    private GeometryTransform resolveRotation(Brick brick) {
        if (brick.facing() != Facing.NONE) {
            return switch (brick.facing()) {
                case NORTH -> GeometryTransform.identity();
                // Y270: X→-Z, Z→X  (LDrawExporter rotateY270)
                case EAST  -> new GeometryTransform( 0, 0,-1, 0,  0, 1, 0, 0,  1, 0, 0, 0);
                // Y180: X→-X, Z→-Z (LDrawExporter rotateY180)
                case SOUTH -> new GeometryTransform(-1, 0, 0, 0,  0, 1, 0, 0,  0, 0,-1, 0);
                // Y90:  X→Z,  Z→-X (LDrawExporter rotateY90)
                case WEST  -> new GeometryTransform( 0, 0, 1, 0,  0, 1, 0, 0, -1, 0, 0, 0);
                default    -> GeometryTransform.identity();
            };
        }

        CatalogPart catalog = catalogById.get(brick.partId());
        if (catalog != null
                && catalog.studX() == brick.studX()
                && catalog.studY() == brick.studY()) {
            // LDrawExporter uses Y90 in this case
            return new GeometryTransform(0, 0, 1, 0,  0, 1, 0, 0,  -1, 0, 0, 0);
        }
        return GeometryTransform.identity();
    }

    /** Reproduces LDrawExporter's world position as a pure translation transform. */
    private static GeometryTransform resolveTranslation(Brick brick) {
        double tx, tz;
        if (brick.facing() == Facing.NORTH || brick.facing() == Facing.SOUTH) {
            tx = (brick.x() + (brick.studX() - 1) / 2.0) * STUD_LDU; // width — centred
            tz = brick.z() * STUD_LDU;                                  // depth — no offset
        } else if (brick.facing() == Facing.EAST || brick.facing() == Facing.WEST) {
            tx = brick.x() * STUD_LDU;                                  // depth — no offset
            tz = (brick.z() + (brick.studY() - 1) / 2.0) * STUD_LDU;  // width — centred
        } else {
            tx = (brick.x() + (brick.studX() - 1) / 2.0) * STUD_LDU;
            tz = (brick.z() + (brick.studY() - 1) / 2.0) * STUD_LDU;
        }
        double ty = -(brick.y() * HEIGHT_LDU + brick.heightUnits() * HEIGHT_LDU);
        return new GeometryTransform(1, 0, 0, tx,  0, 1, 0, ty,  0, 0, 1, tz);
    }

    // -------------------------------------------------------------------------
    // Collision detection
    // -------------------------------------------------------------------------

    private static boolean aabbOverlaps(Aabb a, Aabb b) {
        return a.minX + TOUCH_SHRINK < b.maxX && a.maxX - TOUCH_SHRINK > b.minX
            && a.minY + TOUCH_SHRINK < b.maxY && a.maxY - TOUCH_SHRINK > b.minY
            && a.minZ + TOUCH_SHRINK < b.maxZ && a.maxZ - TOUCH_SHRINK > b.minZ;
    }

    /**
     * Samples the overlap region at SAMPLE_CELL resolution.
     *
     * @return the deepest penetration depth found across all overlapping cells, or 0 if none
     */
    private static double sampledOverlapDepth(WorldBrick a, WorldBrick b) {
        double ox1 = Math.max(a.aabb.minX, b.aabb.minX) + TOUCH_SHRINK;
        double oy1 = Math.max(a.aabb.minY, b.aabb.minY) + TOUCH_SHRINK;
        double oz1 = Math.max(a.aabb.minZ, b.aabb.minZ) + TOUCH_SHRINK;
        double ox2 = Math.min(a.aabb.maxX, b.aabb.maxX) - TOUCH_SHRINK;
        double oy2 = Math.min(a.aabb.maxY, b.aabb.maxY) - TOUCH_SHRINK;
        double oz2 = Math.min(a.aabb.maxZ, b.aabb.maxZ) - TOUCH_SHRINK;

        if (ox1 >= ox2 || oy1 >= oy2 || oz1 >= oz2) {
            return 0;
        }

        double hc = SAMPLE_CELL / 2.0;
        double maxDepth = 0;

        for (double cx = ox1 + hc; cx < ox2; cx += SAMPLE_CELL) {
            for (double cy = oy1 + hc; cy < oy2; cy += SAMPLE_CELL) {
                for (double cz = oz1 + hc; cz < oz2; cz += SAMPLE_CELL) {
                    if (cellOccupied(cx, cy, cz, hc, a.triangles)
                            && cellOccupied(cx, cy, cz, hc, b.triangles)) {
                        // Approximate depth = distance from the cell centre to the nearest face
                        // of the overlap AABB (conservative minimum penetration distance).
                        double depth = Math.min(
                            Math.min(cx - ox1, ox2 - cx),
                            Math.min(Math.min(cy - oy1, oy2 - cy),
                                     Math.min(cz - oz1, oz2 - cz))
                        );
                        maxDepth = Math.max(maxDepth, depth);
                    }
                }
            }
        }
        return maxDepth;
    }

    /**
     * Returns true if the given AABB cell is occupied by the brick's geometry.
     *
     * Combines two tests so that both boundary triangles and solid interiors are detected:
     * <ol>
     *   <li>Surface test: any triangle overlaps the cell (SAT via TriangleAabbTest).</li>
     *   <li>Interior test: the cell centre is inside the closed mesh (odd ray-hit count).</li>
     * </ol>
     */
    private static boolean cellOccupied(double cx, double cy, double cz,
                                        double h, List<Triangle> triangles) {
        // Surface test
        for (Triangle tri : triangles) {
            if (TriangleAabbTest.overlaps(tri, cx, cy, cz, h, h, h)) {
                return true;
            }
        }
        // Interior test: cast a +X ray from (cx, cy, cz) and count triangle hits
        int hits = 0;
        for (Triangle tri : triangles) {
            if (rayHitsTrianglePlusX(cx, cy, cz, tri)) {
                hits++;
            }
        }
        return (hits % 2) == 1;
    }

    /**
     * Möller–Trumbore ray-triangle intersection for a +X ray from (px, py, pz).
     * Reproduces the logic in RaycastGeometryRasterizer (which is package-private).
     */
    private static boolean rayHitsTrianglePlusX(double px, double py, double pz, Triangle tri) {
        double ax = tri.v1().x(), ay = tri.v1().y(), az = tri.v1().z();
        double e1x = tri.v2().x() - ax, e1y = tri.v2().y() - ay, e1z = tri.v2().z() - az;
        double e2x = tri.v3().x() - ax, e2y = tri.v3().y() - ay, e2z = tri.v3().z() - az;

        // h = rayDir × e2, where rayDir = (1,0,0)
        double hx = 0.0, hy = -e2z, hz = e2y;
        double det = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(det) < EPS) return false;

        double invDet = 1.0 / det;
        double sx = px - ax, sy = py - ay, sz = pz - az;
        double u = invDet * (sx * hx + sy * hy + sz * hz);
        if (u < -EPS || u > 1.0 + EPS) return false;

        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = invDet * qx; // rayDir · q = q.x
        if (v < -EPS || u + v > 1.0 + EPS) return false;

        double t = invDet * (e2x * qx + e2y * qy + e2z * qz);
        return t > EPS; // intersection must be in front of the ray origin
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    private record WorldBrick(Brick brick, List<Triangle> triangles, Aabb aabb) {}

    private record Aabb(double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {}
}
