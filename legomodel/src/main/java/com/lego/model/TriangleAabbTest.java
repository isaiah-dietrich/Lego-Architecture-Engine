package com.lego.model;

/**
 * SAT (Separating Axis Theorem) test for triangle-AABB overlap.
 */
public final class TriangleAabbTest {

    /** Non-instantiable utility class. */
    private TriangleAabbTest() {}

    /**
     * Tests whether a triangle overlaps an axis-aligned bounding box.
     *
     * @param tri the triangle to test
     * @param cx  center X of the AABB
     * @param cy  center Y of the AABB
     * @param cz  center Z of the AABB
     * @param hx  half-extent X of the AABB
     * @param hy  half-extent Y of the AABB
     * @param hz  half-extent Z of the AABB
     * @return true if the triangle overlaps the AABB
     */
    public static boolean overlaps(
        Triangle tri,
        double cx, double cy, double cz,
        double hx, double hy, double hz
    ) {
        double v0x = tri.v1().x() - cx, v0y = tri.v1().y() - cy, v0z = tri.v1().z() - cz;
        double v1x = tri.v2().x() - cx, v1y = tri.v2().y() - cy, v1z = tri.v2().z() - cz;
        double v2x = tri.v3().x() - cx, v2y = tri.v3().y() - cy, v2z = tri.v3().z() - cz;

        if (separating(v0x, v1x, v2x, hx)) return false;
        if (separating(v0y, v1y, v2y, hy)) return false;
        if (separating(v0z, v1z, v2z, hz)) return false;

        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        double e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        double nx = e0y * (v2z - v0z) - e0z * (v2y - v0y);
        double ny = e0z * (v2x - v0x) - e0x * (v2z - v0z);
        double nz = e0x * (v2y - v0y) - e0y * (v2x - v0x);
        double d = nx * v0x + ny * v0y + nz * v0z;
        double rn = hx * Math.abs(nx) + hy * Math.abs(ny) + hz * Math.abs(nz);
        if (d > rn || d < -rn) return false;

        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e0y, e0z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e0x, e0z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e0x, e0y, hx, hy)) return false;
        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e1y, e1z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e1x, e1z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e1x, e1y, hx, hy)) return false;
        if (edgeCrossX(v0y, v0z, v1y, v1z, v2y, v2z, e2y, e2z, hy, hz)) return false;
        if (edgeCrossY(v0x, v0z, v1x, v1z, v2x, v2z, e2x, e2z, hx, hz)) return false;
        if (edgeCrossZ(v0x, v0y, v1x, v1y, v2x, v2y, e2x, e2y, hx, hy)) return false;

        return true;
    }

    /** Returns true if the three projections are separated from the half-extent range [-h, +h]. */
    private static boolean separating(double p0, double p1, double p2, double h) {
        return Math.min(p0, Math.min(p1, p2)) > h || Math.max(p0, Math.max(p1, p2)) < -h;
    }

    /** Edge-AABB cross-product test projected onto the X axis. */
    private static boolean edgeCrossX(
        double v0y, double v0z, double v1y, double v1z, double v2y, double v2z,
        double ey, double ez, double hy, double hz
    ) {
        double p0 = ez * v0y - ey * v0z;
        double p1 = ez * v1y - ey * v1z;
        double p2 = ez * v2y - ey * v2z;
        double r = hy * Math.abs(ez) + hz * Math.abs(ey);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    /** Edge-AABB cross-product test projected onto the Y axis. */
    private static boolean edgeCrossY(
        double v0x, double v0z, double v1x, double v1z, double v2x, double v2z,
        double ex, double ez, double hx, double hz
    ) {
        double p0 = -ez * v0x + ex * v0z;
        double p1 = -ez * v1x + ex * v1z;
        double p2 = -ez * v2x + ex * v2z;
        double r = hx * Math.abs(ez) + hz * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }

    /** Edge-AABB cross-product test projected onto the Z axis. */
    private static boolean edgeCrossZ(
        double v0x, double v0y, double v1x, double v1y, double v2x, double v2y,
        double ex, double ey, double hx, double hy
    ) {
        double p0 = ey * v0x - ex * v0y;
        double p1 = ey * v1x - ex * v1y;
        double p2 = ey * v2x - ex * v2y;
        double r = hx * Math.abs(ey) + hy * Math.abs(ex);
        return Math.min(p0, Math.min(p1, p2)) > r || Math.max(p0, Math.max(p1, p2)) < -r;
    }
}
