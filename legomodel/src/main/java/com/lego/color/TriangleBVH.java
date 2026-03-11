package com.lego.color;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import com.lego.model.Triangle;

/**
 * Bounding Volume Hierarchy for nearest-surface-point queries on a triangle mesh.
 *
 * <p>Construction: top-down median split along the longest AABB axis.
 * Query: iterative DFS with distance-based pruning, visiting the closer
 * child first to maximize early termination.
 *
 * <p>Closest-point-on-triangle uses the Voronoi region algorithm from
 * Ericson, "Real-Time Collision Detection" (2004), Section 5.1.5.
 */
public final class TriangleBVH {

    private static final int MAX_LEAF_SIZE = 4;

    // ---- AABB ----

    record AABB(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {

        double distanceSquared(double px, double py, double pz) {
            double dx = Math.max(minX - px, Math.max(0, px - maxX));
            double dy = Math.max(minY - py, Math.max(0, py - maxY));
            double dz = Math.max(minZ - pz, Math.max(0, pz - maxZ));
            return dx * dx + dy * dy + dz * dz;
        }

        static AABB fromTriangle(Triangle t) {
            return new AABB(
                Math.min(t.v1().x(), Math.min(t.v2().x(), t.v3().x())),
                Math.min(t.v1().y(), Math.min(t.v2().y(), t.v3().y())),
                Math.min(t.v1().z(), Math.min(t.v2().z(), t.v3().z())),
                Math.max(t.v1().x(), Math.max(t.v2().x(), t.v3().x())),
                Math.max(t.v1().y(), Math.max(t.v2().y(), t.v3().y())),
                Math.max(t.v1().z(), Math.max(t.v2().z(), t.v3().z()))
            );
        }

        AABB merge(AABB o) {
            return new AABB(
                Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ)
            );
        }
    }

    // ---- Hit result ----

    /**
     * Result of a nearest-surface-point query.
     *
     * @param triangleIndex index into the triangle list
     * @param px closest point X
     * @param py closest point Y
     * @param pz closest point Z
     * @param bary0 barycentric weight for vertex 0
     * @param bary1 barycentric weight for vertex 1
     * @param bary2 barycentric weight for vertex 2
     * @param distSq squared distance from query point to closest point
     */
    public record Hit(int triangleIndex,
                      double px, double py, double pz,
                      double bary0, double bary1, double bary2,
                      double distSq) {}

    // ---- BVH node ----

    private static final class Node {
        final AABB bounds;
        final Node left, right;
        final int[] triangleIndices; // non-null only for leaves

        /** Internal node. */
        Node(AABB bounds, Node left, Node right) {
            this.bounds = bounds;
            this.left = left;
            this.right = right;
            this.triangleIndices = null;
        }

        /** Leaf node. */
        Node(AABB bounds, int[] triangleIndices) {
            this.bounds = bounds;
            this.left = null;
            this.right = null;
            this.triangleIndices = triangleIndices;
        }

        boolean isLeaf() { return triangleIndices != null; }
    }

    // ---- Fields ----

    private final Node root;
    private final List<Triangle> triangles;

    private TriangleBVH(Node root, List<Triangle> triangles) {
        this.root = root;
        this.triangles = triangles;
    }

    // ---- Build ----

    /**
     * Builds a BVH from the given triangle list.
     * Triangle indices in query results correspond to positions in this list.
     */
    public static TriangleBVH build(List<Triangle> triangles) {
        if (triangles.isEmpty()) {
            throw new IllegalArgumentException("Cannot build BVH from empty triangle list");
        }

        int n = triangles.size();
        int[] indices = new int[n];
        double[] centroids = new double[n * 3];
        AABB[] boxes = new AABB[n];

        for (int i = 0; i < n; i++) {
            indices[i] = i;
            Triangle t = triangles.get(i);
            centroids[i * 3]     = (t.v1().x() + t.v2().x() + t.v3().x()) / 3.0;
            centroids[i * 3 + 1] = (t.v1().y() + t.v2().y() + t.v3().y()) / 3.0;
            centroids[i * 3 + 2] = (t.v1().z() + t.v2().z() + t.v3().z()) / 3.0;
            boxes[i] = AABB.fromTriangle(t);
        }

        Node root = buildNode(indices, 0, n, centroids, boxes);
        return new TriangleBVH(root, triangles);
    }

    private static Node buildNode(int[] indices, int start, int end,
                                   double[] centroids, AABB[] boxes) {
        AABB bounds = boxes[indices[start]];
        for (int i = start + 1; i < end; i++) {
            bounds = bounds.merge(boxes[indices[i]]);
        }

        int count = end - start;
        if (count <= MAX_LEAF_SIZE) {
            return new Node(bounds, Arrays.copyOfRange(indices, start, end));
        }

        // Split along longest axis
        double spanX = bounds.maxX - bounds.minX;
        double spanY = bounds.maxY - bounds.minY;
        double spanZ = bounds.maxZ - bounds.minZ;
        int axis;
        if (spanX >= spanY && spanX >= spanZ) axis = 0;
        else if (spanY >= spanZ) axis = 1;
        else axis = 2;

        // Sort subrange by centroid on chosen axis
        final int sortAxis = axis;
        Integer[] sub = new Integer[count];
        for (int i = 0; i < count; i++) sub[i] = indices[start + i];
        Arrays.sort(sub, (a, b) ->
            Double.compare(centroids[a * 3 + sortAxis], centroids[b * 3 + sortAxis]));
        for (int i = 0; i < count; i++) indices[start + i] = sub[i];

        int mid = start + count / 2;
        Node left  = buildNode(indices, start, mid, centroids, boxes);
        Node right = buildNode(indices, mid,   end, centroids, boxes);
        return new Node(bounds, left, right);
    }

    // ---- Query ----

    /**
     * Finds the nearest point on the mesh surface to the given query point.
     *
     * @return the hit result, or null if the mesh is empty
     */
    public Hit nearestSurfacePoint(double px, double py, double pz) {
        double bestDistSq = Double.MAX_VALUE;
        Hit bestHit = null;

        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node node = stack.pop();

            if (node.bounds.distanceSquared(px, py, pz) > bestDistSq) {
                continue;
            }

            if (node.isLeaf()) {
                for (int idx : node.triangleIndices) {
                    double[] r = closestPointOnTriangle(triangles.get(idx), px, py, pz);
                    if (r[3] < bestDistSq) {
                        bestDistSq = r[3];
                        bestHit = new Hit(idx, r[0], r[1], r[2], r[4], r[5], r[6], r[3]);
                    }
                }
            } else {
                double ld = node.left.bounds.distanceSquared(px, py, pz);
                double rd = node.right.bounds.distanceSquared(px, py, pz);

                // Push farther child first so closer child is popped first
                if (ld < rd) {
                    if (rd <= bestDistSq) stack.push(node.right);
                    if (ld <= bestDistSq) stack.push(node.left);
                } else {
                    if (ld <= bestDistSq) stack.push(node.left);
                    if (rd <= bestDistSq) stack.push(node.right);
                }
            }
        }

        return bestHit;
    }

    // ---- Closest point on triangle (Ericson algorithm) ----

    /**
     * Computes the closest point on a triangle to a query point.
     *
     * @return double[7] = {cpx, cpy, cpz, distSq, bary0, bary1, bary2}
     */
    static double[] closestPointOnTriangle(Triangle tri,
                                           double px, double py, double pz) {
        double ax = tri.v1().x(), ay = tri.v1().y(), az = tri.v1().z();
        double bx = tri.v2().x(), by = tri.v2().y(), bz = tri.v2().z();
        double cx = tri.v3().x(), cy = tri.v3().y(), cz = tri.v3().z();

        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double acx = cx - ax, acy = cy - ay, acz = cz - az;
        double apx = px - ax, apy = py - ay, apz = pz - az;

        double d1 = abx * apx + aby * apy + abz * apz;
        double d2 = acx * apx + acy * apy + acz * apz;

        // Vertex A region
        if (d1 <= 0 && d2 <= 0) {
            return result(ax, ay, az, px, py, pz, 1, 0, 0);
        }

        double bpx = px - bx, bpy = py - by, bpz = pz - bz;
        double d3 = abx * bpx + aby * bpy + abz * bpz;
        double d4 = acx * bpx + acy * bpy + acz * bpz;

        // Vertex B region
        if (d3 >= 0 && d4 <= d3) {
            return result(bx, by, bz, px, py, pz, 0, 1, 0);
        }

        // Edge AB region
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
            double v = d1 / (d1 - d3);
            double rx = ax + v * abx, ry = ay + v * aby, rz = az + v * abz;
            return result(rx, ry, rz, px, py, pz, 1 - v, v, 0);
        }

        double cpx = px - cx, cpy = py - cy, cpz = pz - cz;
        double d5 = abx * cpx + aby * cpy + abz * cpz;
        double d6 = acx * cpx + acy * cpy + acz * cpz;

        // Vertex C region
        if (d6 >= 0 && d5 <= d6) {
            return result(cx, cy, cz, px, py, pz, 0, 0, 1);
        }

        // Edge AC region
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            double w = d2 / (d2 - d6);
            double rx = ax + w * acx, ry = ay + w * acy, rz = az + w * acz;
            return result(rx, ry, rz, px, py, pz, 1 - w, 0, w);
        }

        // Edge BC region
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
            double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            double rx = bx + w * (cx - bx), ry = by + w * (cy - by), rz = bz + w * (cz - bz);
            return result(rx, ry, rz, px, py, pz, 0, 1 - w, w);
        }

        // Interior region
        double denom = 1.0 / (va + vb + vc);
        double v = vb * denom;
        double w = vc * denom;
        double rx = ax + abx * v + acx * w;
        double ry = ay + aby * v + acy * w;
        double rz = az + abz * v + acz * w;
        return result(rx, ry, rz, px, py, pz, 1 - v - w, v, w);
    }

    private static double[] result(double rx, double ry, double rz,
                                    double px, double py, double pz,
                                    double b0, double b1, double b2) {
        double dx = px - rx, dy = py - ry, dz = pz - rz;
        return new double[] { rx, ry, rz, dx*dx + dy*dy + dz*dz, b0, b1, b2 };
    }
}
