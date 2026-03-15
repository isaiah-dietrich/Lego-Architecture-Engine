"""
Analyze connected components within a single mesh to find strand/line artifacts.
Lists every component with vertex count, face count, aspect ratios, and bbox.

Usage:
    blender -b -P scripts/analyze_components.py -- model.glb [mesh_name]
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
        print("Usage: blender -b -P analyze_components.py -- <model.glb> [mesh_name]")
        sys.exit(1)
    filepath = argv[0]
    mesh_name = argv[1] if len(argv) > 1 else None
    return filepath, mesh_name


def main():
    filepath, target_mesh = parse_args()

    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()
    bpy.ops.import_scene.gltf(filepath=filepath)

    # Find the target mesh (or largest)
    meshes = [obj for obj in bpy.data.objects if obj.type == 'MESH']
    if target_mesh:
        obj = next((m for m in meshes if m.name == target_mesh), None)
        if not obj:
            print(f"Mesh '{target_mesh}' not found. Available: {[m.name for m in meshes]}")
            sys.exit(1)
    else:
        obj = max(meshes, key=lambda m: len(m.data.polygons))

    mesh = obj.data
    verts = mesh.vertices
    polys = mesh.polygons
    edges = mesh.edges

    print(f"\n{'='*70}")
    print(f"COMPONENT ANALYSIS: {obj.name} ({len(verts)} verts, {len(polys)} faces)")
    print(f"{'='*70}")

    # Union-find
    parent = list(range(len(verts)))
    rank = [0] * len(verts)

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        a, b = find(a), find(b)
        if a != b:
            if rank[a] < rank[b]:
                a, b = b, a
            parent[b] = a
            if rank[a] == rank[b]:
                rank[a] += 1

    for e in edges:
        union(e.vertices[0], e.vertices[1])

    # Group vertices and faces by component
    comp_verts = defaultdict(list)
    for i in range(len(verts)):
        comp_verts[find(i)].append(i)

    comp_faces = defaultdict(list)
    for fi, poly in enumerate(polys):
        root = find(poly.vertices[0])
        comp_faces[root].append(fi)

    # Analyze each component
    components = []
    for root, vert_indices in comp_verts.items():
        face_indices = comp_faces.get(root, [])

        # Bounding box
        coords = [obj.matrix_world @ verts[vi].co for vi in vert_indices]
        xs = [c.x for c in coords]
        ys = [c.y for c in coords]
        zs = [c.z for c in coords]
        bbox_dims = (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
        bbox_center = ((max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2, (max(zs) + min(zs)) / 2)

        # Aspect ratios of faces in this component
        ars = []
        for fi in face_indices:
            poly = polys[fi]
            if len(poly.vertices) >= 3:
                v1, v2, v3 = verts[poly.vertices[0]], verts[poly.vertices[1]], verts[poly.vertices[2]]
                e_lengths = [
                    math.sqrt(sum((a - b) ** 2 for a, b in zip(v1.co, v2.co))),
                    math.sqrt(sum((a - b) ** 2 for a, b in zip(v2.co, v3.co))),
                    math.sqrt(sum((a - b) ** 2 for a, b in zip(v3.co, v1.co))),
                ]
                longest = max(e_lengths)
                s = sum(e_lengths) / 2.0
                area_sq = s * (s - e_lengths[0]) * (s - e_lengths[1]) * (s - e_lengths[2])
                if area_sq > 0:
                    area = math.sqrt(area_sq)
                    alt = 2.0 * area / longest
                    if alt > 1e-10:
                        ars.append(longest / alt)

        # Slimness: ratio of longest bbox dimension to shortest
        sorted_dims = sorted(bbox_dims)
        slimness = sorted_dims[2] / max(sorted_dims[0], 1e-10)

        median_ar = sorted(ars)[len(ars) // 2] if ars else 0
        sliver_pct = 100.0 * sum(1 for a in ars if a > 20) / len(ars) if ars else 0

        components.append({
            "root": root,
            "verts": len(vert_indices),
            "faces": len(face_indices),
            "bbox": bbox_dims,
            "bbox_center": bbox_center,
            "slimness": slimness,
            "median_ar": median_ar,
            "sliver_pct": sliver_pct,
        })

    # Sort by vertex count descending
    components.sort(key=lambda c: -c["verts"])

    total_verts = len(verts)
    total_faces = len(polys)

    # Identify suspicious components
    body_verts = components[0]["verts"] if components else 0

    print(f"\nTotal components: {len(components)}")
    print(f"Largest component: {components[0]['verts']} verts ({100*components[0]['verts']/total_verts:.1f}%)")
    print()

    artifact_components = []
    body_components = []

    for i, c in enumerate(components):
        is_artifact = False
        flags = []

        # Very small component
        if c["verts"] < body_verts * 0.01:
            flags.append("TINY")
            is_artifact = True

        # High sliver percentage
        if c["sliver_pct"] > 50:
            flags.append(f"SLIVER({c['sliver_pct']:.0f}%)")
            is_artifact = True

        # Very slim bounding box (line-like shape)
        if c["slimness"] > 50 and c["verts"] < 200:
            flags.append(f"SLIM(ratio={c['slimness']:.0f})")
            is_artifact = True

        label = "ARTIFACT" if is_artifact else "BODY"
        if is_artifact:
            artifact_components.append(c)
        else:
            body_components.append(c)

        bb = c["bbox"]
        ctr = c["bbox_center"]
        print(f"  [{i:3d}] {label:8s} verts={c['verts']:5d} faces={c['faces']:5d} "
              f"bbox=({bb[0]:.4f},{bb[1]:.4f},{bb[2]:.4f}) "
              f"center=({ctr[0]:.2f},{ctr[1]:.2f},{ctr[2]:.2f}) "
              f"slim={c['slimness']:.1f} medAR={c['median_ar']:.1f} "
              f"sliver={c['sliver_pct']:.0f}%"
              + (f"  >>> {', '.join(flags)}" if flags else ""))

    art_verts = sum(c["verts"] for c in artifact_components)
    art_faces = sum(c["faces"] for c in artifact_components)
    print(f"\n--- Summary ---")
    print(f"Body components: {len(body_components)} ({total_verts - art_verts} verts, {total_faces - art_faces} faces)")
    print(f"Artifact components: {len(artifact_components)} ({art_verts} verts, {art_faces} faces)")
    print(f"Artifact vertex %: {100*art_verts/total_verts:.1f}%")


if __name__ == "__main__":
    main()
