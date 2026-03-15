"""
Deep diagnostic: analyze GLB mesh geometry to find line/strand artifacts.

Reports:
  - All mesh objects with vertex/face counts
  - Thin-triangle analysis (degenerate/sliver triangles)
  - Edge-length distribution (long thin edges = line artifacts)
  - Isolated vertex clusters (disconnected geometry)
  - Material assignment gaps
  - Per-object bounding box vs main body comparison

Usage:
    blender -b -P scripts/diagnose_glb.py -- model.glb
"""
import bpy
import sys
import math
from collections import defaultdict


def parse_args():
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
    else:
        argv = []
    if not argv:
        print("Usage: blender -b -P diagnose_glb.py -- <model.glb>")
        sys.exit(1)
    return argv[0]


def edge_length(v1, v2):
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(v1.co, v2.co)))


def triangle_aspect_ratio(v1, v2, v3):
    """Aspect ratio: longest edge / shortest altitude. High = sliver triangle."""
    edges = [
        math.sqrt(sum((a - b) ** 2 for a, b in zip(v1.co, v2.co))),
        math.sqrt(sum((a - b) ** 2 for a, b in zip(v2.co, v3.co))),
        math.sqrt(sum((a - b) ** 2 for a, b in zip(v3.co, v1.co))),
    ]
    longest = max(edges)
    s = sum(edges) / 2.0
    area_sq = s * (s - edges[0]) * (s - edges[1]) * (s - edges[2])
    if area_sq <= 0:
        return float('inf')  # degenerate
    area = math.sqrt(area_sq)
    shortest_altitude = 2.0 * area / longest
    if shortest_altitude < 1e-10:
        return float('inf')
    return longest / shortest_altitude


def bbox(obj):
    """World-space bounding box dimensions."""
    coords = [obj.matrix_world @ v.co for v in obj.data.vertices]
    if not coords:
        return (0, 0, 0)
    xs = [c.x for c in coords]
    ys = [c.y for c in coords]
    zs = [c.z for c in coords]
    return (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))


def analyze_mesh(obj):
    """Full geometric analysis of a mesh object."""
    mesh = obj.data
    verts = mesh.vertices
    polys = mesh.polygons
    edges_data = mesh.edges

    result = {
        "name": obj.name,
        "vertices": len(verts),
        "faces": len(polys),
        "edges": len(edges_data),
        "materials": [m.name for m in mesh.materials] if mesh.materials else [],
        "has_uv": len(mesh.uv_layers) > 0,
    }

    if len(verts) == 0:
        return result

    # Bounding box
    bb = bbox(obj)
    result["bbox"] = bb
    result["bbox_volume"] = bb[0] * bb[1] * bb[2]

    # Edge length analysis
    edge_lengths = []
    for e in edges_data:
        v1, v2 = verts[e.vertices[0]], verts[e.vertices[1]]
        edge_lengths.append(edge_length(v1, v2))

    if edge_lengths:
        edge_lengths.sort()
        result["edge_min"] = edge_lengths[0]
        result["edge_max"] = edge_lengths[-1]
        result["edge_median"] = edge_lengths[len(edge_lengths) // 2]
        result["edge_mean"] = sum(edge_lengths) / len(edge_lengths)
        # Ratio of longest to median edge — high values indicate line geometry
        if result["edge_median"] > 0:
            result["edge_max_to_median"] = result["edge_max"] / result["edge_median"]
        else:
            result["edge_max_to_median"] = float('inf')

    # Triangle aspect ratio analysis (sliver detection)
    aspect_ratios = []
    degenerate_count = 0
    for poly in polys:
        if len(poly.vertices) >= 3:
            v1, v2, v3 = verts[poly.vertices[0]], verts[poly.vertices[1]], verts[poly.vertices[2]]
            ar = triangle_aspect_ratio(v1, v2, v3)
            if ar == float('inf'):
                degenerate_count += 1
            else:
                aspect_ratios.append(ar)

    result["degenerate_faces"] = degenerate_count
    if aspect_ratios:
        aspect_ratios.sort()
        result["aspect_ratio_median"] = aspect_ratios[len(aspect_ratios) // 2]
        result["aspect_ratio_p95"] = aspect_ratios[int(len(aspect_ratios) * 0.95)]
        result["aspect_ratio_max"] = aspect_ratios[-1]
        # Slivers: aspect ratio > 20
        result["sliver_faces"] = sum(1 for a in aspect_ratios if a > 20)
        result["sliver_pct"] = 100.0 * result["sliver_faces"] / len(aspect_ratios)

    # Loose vertices (not connected to any face)
    verts_in_faces = set()
    for poly in polys:
        for vi in poly.vertices:
            verts_in_faces.add(vi)
    result["loose_vertices"] = len(verts) - len(verts_in_faces)

    # Connected components via union-find
    parent = list(range(len(verts)))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        a, b = find(a), find(b)
        if a != b:
            parent[a] = b

    for e in edges_data:
        union(e.vertices[0], e.vertices[1])

    components = defaultdict(int)
    for i in range(len(verts)):
        components[find(i)] += 1

    result["connected_components"] = len(components)
    comp_sizes = sorted(components.values(), reverse=True)
    result["component_sizes"] = comp_sizes[:10]  # top 10

    # Vertex-to-face ratio (strand geometry has high ratio)
    if len(polys) > 0:
        result["vert_face_ratio"] = len(verts) / len(polys)
    else:
        result["vert_face_ratio"] = float('inf')

    return result


def classify_artifact(analysis, main_face_count):
    """Classify whether a mesh is likely an artifact."""
    reasons = []

    # No materials
    if not analysis["materials"]:
        reasons.append("NO_MATERIAL")

    # No UV
    if not analysis.get("has_uv", True):
        reasons.append("NO_UV")

    # Very small relative to main mesh
    if main_face_count > 0 and analysis["faces"] < main_face_count * 0.10:
        reasons.append(f"SMALL_SUBMESH ({analysis['faces']}/{main_face_count} = {100*analysis['faces']/main_face_count:.1f}%)")

    # High sliver percentage
    if analysis.get("sliver_pct", 0) > 30:
        reasons.append(f"HIGH_SLIVER ({analysis['sliver_pct']:.0f}%)")

    # High edge max-to-median ratio (line-like geometry)
    if analysis.get("edge_max_to_median", 0) > 50:
        reasons.append(f"LINE_GEOMETRY (edge_max/median={analysis['edge_max_to_median']:.1f})")

    # Many connected components (scattered small pieces)
    if analysis.get("connected_components", 1) > 10:
        reasons.append(f"FRAGMENTED ({analysis['connected_components']} components)")

    # Degenerate faces
    if analysis.get("degenerate_faces", 0) > 0:
        reasons.append(f"DEGENERATE_FACES ({analysis['degenerate_faces']})")

    # Many loose vertices
    if analysis.get("loose_vertices", 0) > analysis["vertices"] * 0.1:
        reasons.append(f"LOOSE_VERTS ({analysis['loose_vertices']})")

    return reasons


def main():
    filepath = parse_args()

    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()
    bpy.ops.import_scene.gltf(filepath=filepath)

    meshes = [obj for obj in bpy.data.objects if obj.type == 'MESH']
    non_meshes = [obj for obj in bpy.data.objects if obj.type != 'MESH']

    print(f"\n{'='*70}")
    print(f"GLB DIAGNOSTIC REPORT: {filepath}")
    print(f"{'='*70}")
    print(f"Total objects: {len(bpy.data.objects)} ({len(meshes)} meshes, {len(non_meshes)} non-mesh)")
    print(f"Materials: {len(bpy.data.materials)}")

    print(f"\n--- Non-mesh objects ---")
    for obj in non_meshes:
        print(f"  {obj.type:12s}  {obj.name}")

    # Analyze all meshes
    analyses = []
    for obj in meshes:
        analyses.append(analyze_mesh(obj))

    # Find the main body mesh (largest face count)
    main_face_count = max((a["faces"] for a in analyses), default=0)

    print(f"\n--- Mesh analysis ---")
    for a in sorted(analyses, key=lambda x: -x["faces"]):
        print(f"\n  MESH: {a['name']}")
        print(f"    Vertices: {a['vertices']:,}  Faces: {a['faces']:,}  Edges: {a['edges']:,}")
        print(f"    Materials: {a['materials'] or '(none)'}")
        print(f"    Has UV: {a.get('has_uv', 'N/A')}")
        if "bbox" in a:
            bb = a["bbox"]
            print(f"    BBox: {bb[0]:.4f} x {bb[1]:.4f} x {bb[2]:.4f}")
        if "vert_face_ratio" in a:
            print(f"    Vert/face ratio: {a['vert_face_ratio']:.3f}")
        if "edge_min" in a:
            print(f"    Edge lengths: min={a['edge_min']:.6f} median={a['edge_median']:.6f} "
                  f"max={a['edge_max']:.6f} mean={a['edge_mean']:.6f}")
            print(f"    Edge max/median: {a.get('edge_max_to_median', 0):.1f}")
        if "aspect_ratio_median" in a:
            print(f"    Aspect ratio: median={a['aspect_ratio_median']:.1f} "
                  f"p95={a['aspect_ratio_p95']:.1f} max={a['aspect_ratio_max']:.1f}")
            print(f"    Sliver faces (AR>20): {a.get('sliver_faces', 0)} ({a.get('sliver_pct', 0):.1f}%)")
        if "degenerate_faces" in a:
            print(f"    Degenerate faces: {a['degenerate_faces']}")
        if "loose_vertices" in a:
            print(f"    Loose vertices: {a['loose_vertices']}")
        if "connected_components" in a:
            print(f"    Connected components: {a['connected_components']}")
            print(f"    Largest components: {a['component_sizes']}")

        # Classification
        reasons = classify_artifact(a, main_face_count)
        if reasons:
            print(f"    >>> ARTIFACT FLAGS: {', '.join(reasons)}")
        else:
            print(f"    >>> CLEAN")

    # Material summary
    print(f"\n--- Material summary ---")
    for mat in bpy.data.materials:
        users = mat.users
        has_nodes = mat.use_nodes if hasattr(mat, 'use_nodes') else False
        tex_count = 0
        if has_nodes and mat.node_tree:
            tex_count = sum(1 for n in mat.node_tree.nodes if n.type == 'TEX_IMAGE')
        print(f"  {mat.name}: users={users} has_nodes={has_nodes} textures={tex_count}")

    print(f"\n{'='*70}")
    print("DIAGNOSTIC COMPLETE")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
