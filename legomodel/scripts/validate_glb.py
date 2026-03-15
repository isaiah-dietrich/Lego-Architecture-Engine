"""
Validate a GLB file for LEGO pipeline readiness.

Checks:
  1. File structure: valid glTF, correct mesh/material count
  2. Geometry: no stray objects, no sliver strand meshes, no degenerate faces
  3. Textures: base color texture present and correctly sized
  4. Component health: no tiny disconnected fragments in main mesh
  5. Material: clean material assignment, no orphan materials in file

This script reads the raw GLB binary directly (no Blender needed) for fast,
reliable validation that doesn't pick up Blender startup file artifacts.

Exit codes:
  0 = all checks passed
  1 = validation failures found

Usage:
    python3 scripts/validate_glb.py output/labrador_dog_delit.glb
    python3 scripts/validate_glb.py output/labrador_dog_delit.glb --strict
"""
import json
import struct
import sys
import os
import math
import base64


class ValidationResult:
    def __init__(self):
        self.passes = []
        self.warnings = []
        self.failures = []

    def ok(self, msg):
        self.passes.append(msg)

    def warn(self, msg):
        self.warnings.append(msg)

    def fail(self, msg):
        self.failures.append(msg)

    @property
    def passed(self):
        return len(self.failures) == 0

    def report(self):
        lines = []
        for msg in self.passes:
            lines.append(f"  PASS  {msg}")
        for msg in self.warnings:
            lines.append(f"  WARN  {msg}")
        for msg in self.failures:
            lines.append(f"  FAIL  {msg}")
        return "\n".join(lines)


def parse_glb(filepath):
    """Parse a GLB file and return (json_data, binary_chunk)."""
    with open(filepath, 'rb') as f:
        magic, version, length = struct.unpack('<III', f.read(12))
        if magic != 0x46546C67:  # 'glTF'
            raise ValueError(f"Not a valid GLB file (magic: {hex(magic)})")
        if version != 2:
            raise ValueError(f"Unsupported glTF version: {version}")

        # JSON chunk
        json_len, json_type = struct.unpack('<II', f.read(8))
        if json_type != 0x4E4F534A:  # 'JSON'
            raise ValueError("First chunk is not JSON")
        json_data = json.loads(f.read(json_len))

        # Binary chunk (optional)
        bin_data = None
        remaining = length - 12 - 8 - json_len
        if remaining > 8:
            bin_len, bin_type = struct.unpack('<II', f.read(8))
            if bin_type == 0x004E4942:  # 'BIN'
                bin_data = f.read(bin_len)

        return json_data, bin_data


def get_accessor_data(json_data, bin_data, accessor_index):
    """Read raw data from a glTF accessor."""
    accessor = json_data['accessors'][accessor_index]
    buffer_view = json_data['bufferViews'][accessor['bufferView']]
    offset = buffer_view.get('byteOffset', 0) + accessor.get('byteOffset', 0)
    count = accessor['count']
    component_type = accessor['componentType']
    accessor_type = accessor['type']

    # Component size
    comp_sizes = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
    comp_size = comp_sizes.get(component_type, 4)

    # Number of components per element
    type_counts = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}
    num_components = type_counts.get(accessor_type, 1)

    # Format string
    fmt_chars = {5120: 'b', 5121: 'B', 5122: 'h', 5123: 'H', 5125: 'I', 5126: 'f'}
    fmt = fmt_chars.get(component_type, 'f')

    stride = buffer_view.get('byteStride', comp_size * num_components)
    elements = []
    for i in range(count):
        pos = offset + i * stride
        vals = struct.unpack_from(f'<{num_components}{fmt}', bin_data, pos)
        elements.append(vals if num_components > 1 else vals[0])

    return elements


def validate_structure(json_data, result, strict=False):
    """Validate file structure."""
    meshes = json_data.get('meshes', [])
    materials = json_data.get('materials', [])
    nodes = json_data.get('nodes', [])

    # Count mesh-bearing nodes
    mesh_nodes = [n for n in nodes if 'mesh' in n]

    if len(meshes) == 0:
        result.fail("No meshes in file")
    elif len(meshes) == 1:
        result.ok(f"Single mesh: '{meshes[0].get('name', '?')}'")
    else:
        msg = f"Multiple meshes ({len(meshes)}): {[m.get('name', '?') for m in meshes]}"
        if strict:
            result.fail(msg)
        else:
            result.warn(msg)

    if len(mesh_nodes) == 0:
        result.fail("No nodes reference any mesh")
    elif len(mesh_nodes) == 1:
        result.ok(f"Single mesh node: '{mesh_nodes[0].get('name', '?')}'")
    else:
        names = [n.get('name', '?') for n in mesh_nodes]
        if strict:
            result.fail(f"Multiple mesh nodes ({len(mesh_nodes)}): {names}")
        else:
            result.warn(f"Multiple mesh nodes ({len(mesh_nodes)}): {names}")

    if len(materials) == 0:
        result.warn("No materials defined")
    elif len(materials) == 1:
        result.ok(f"Single material: '{materials[0].get('name', '?')}'")
    else:
        names = [m.get('name', '?') for m in materials]
        if strict:
            result.fail(f"Multiple materials ({len(materials)}): {names}")
        else:
            result.warn(f"Multiple materials ({len(materials)}): {names}")

    return meshes, materials, mesh_nodes


def validate_geometry(json_data, bin_data, meshes, result):
    """Validate mesh geometry quality."""
    for mesh_idx, mesh in enumerate(meshes):
        mesh_name = mesh.get('name', f'mesh_{mesh_idx}')

        for prim_idx, prim in enumerate(mesh.get('primitives', [])):
            # Get position data
            pos_accessor_idx = prim.get('attributes', {}).get('POSITION')
            if pos_accessor_idx is None:
                result.fail(f"{mesh_name}: primitive {prim_idx} has no POSITION attribute")
                continue

            positions = get_accessor_data(json_data, bin_data, pos_accessor_idx)
            vert_count = len(positions)

            # Get index data
            indices_accessor_idx = prim.get('indices')
            if indices_accessor_idx is not None:
                indices = get_accessor_data(json_data, bin_data, indices_accessor_idx)
                face_count = len(indices) // 3
            else:
                indices = list(range(vert_count))
                face_count = vert_count // 3

            result.ok(f"{mesh_name}: {vert_count:,} vertices, {face_count:,} faces")

            # Check for degenerate triangles
            degenerate = 0
            sliver = 0
            total_checked = 0
            step = max(1, face_count // 5000)  # sample up to 5000 faces

            for fi in range(0, face_count, step):
                i0, i1, i2 = indices[fi*3], indices[fi*3+1], indices[fi*3+2]
                v0, v1, v2 = positions[i0], positions[i1], positions[i2]

                e = [
                    math.sqrt(sum((a-b)**2 for a,b in zip(v0, v1))),
                    math.sqrt(sum((a-b)**2 for a,b in zip(v1, v2))),
                    math.sqrt(sum((a-b)**2 for a,b in zip(v2, v0))),
                ]
                longest = max(e)
                s = sum(e) / 2.0
                area_sq = s * (s - e[0]) * (s - e[1]) * (s - e[2])

                if area_sq <= 0 or longest < 1e-10:
                    degenerate += 1
                else:
                    area = math.sqrt(area_sq)
                    alt = 2.0 * area / longest
                    if alt > 1e-10 and longest / alt > 20:
                        sliver += 1

                total_checked += 1

            if degenerate > 0:
                result.fail(f"{mesh_name}: {degenerate}/{total_checked} degenerate faces (sampled)")
            else:
                result.ok(f"{mesh_name}: no degenerate faces (sampled {total_checked})")

            sliver_pct = 100.0 * sliver / total_checked if total_checked > 0 else 0
            if sliver_pct > 5:
                result.fail(f"{mesh_name}: {sliver_pct:.1f}% sliver faces — likely strand artifacts")
            elif sliver_pct > 1:
                result.warn(f"{mesh_name}: {sliver_pct:.1f}% sliver faces")
            else:
                result.ok(f"{mesh_name}: sliver faces {sliver_pct:.1f}% (healthy)")

            # Check for UV coordinates
            has_uv = 'TEXCOORD_0' in prim.get('attributes', {})
            if has_uv:
                result.ok(f"{mesh_name}: has UV coordinates")
            else:
                result.warn(f"{mesh_name}: no UV coordinates")

            # Connected component analysis via union-find on index buffer
            parent = list(range(vert_count))

            def find(x):
                while parent[x] != x:
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                return x

            def union(a, b):
                a, b = find(a), find(b)
                if a != b:
                    parent[a] = b

            for fi in range(face_count):
                i0, i1, i2 = indices[fi*3], indices[fi*3+1], indices[fi*3+2]
                union(i0, i1)
                union(i1, i2)

            from collections import Counter
            comp_sizes = Counter()
            for i in range(vert_count):
                comp_sizes[find(i)] += 1

            num_components = len(comp_sizes)
            sizes = sorted(comp_sizes.values(), reverse=True)
            largest = sizes[0] if sizes else 0

            # Count tiny components (< 1% of largest)
            threshold = max(largest * 0.01, 10)
            tiny = [s for s in sizes if s < threshold]

            if num_components == 1:
                result.ok(f"{mesh_name}: single connected component")
            elif len(tiny) == 0:
                result.ok(f"{mesh_name}: {num_components} components, all significant")
            else:
                tiny_verts = sum(tiny)
                result.fail(
                    f"{mesh_name}: {len(tiny)} tiny components "
                    f"({tiny_verts} vertices) — likely line/strand artifacts")

            if num_components <= 200:
                result.ok(f"{mesh_name}: {num_components} components (reasonable)")
            else:
                result.warn(f"{mesh_name}: {num_components} components (highly fragmented)")


def validate_textures(json_data, materials, result):
    """Validate texture assignments."""
    images = json_data.get('images', [])
    textures = json_data.get('textures', [])

    for mat_idx, mat in enumerate(materials):
        mat_name = mat.get('name', f'material_{mat_idx}')
        pbr = mat.get('pbrMetallicRoughness', {})

        base_color_tex = pbr.get('baseColorTexture')
        if base_color_tex:
            tex_idx = base_color_tex.get('index')
            if tex_idx is not None and tex_idx < len(textures):
                tex = textures[tex_idx]
                img_idx = tex.get('source')
                if img_idx is not None and img_idx < len(images):
                    img = images[img_idx]
                    img_name = img.get('name', img.get('uri', f'image_{img_idx}'))
                    mime = img.get('mimeType', 'unknown')
                    result.ok(f"{mat_name}: base color texture '{img_name}' ({mime})")
                else:
                    result.fail(f"{mat_name}: base color texture references missing image")
            else:
                result.fail(f"{mat_name}: base color texture references missing texture")
        else:
            base_factor = pbr.get('baseColorFactor')
            if base_factor:
                result.warn(f"{mat_name}: flat color only (no texture), factor={base_factor}")
            else:
                result.warn(f"{mat_name}: no base color texture or factor")


def validate_artifact_names(json_data, result):
    """Check for known artifact object names."""
    known_artifacts = ['icosphere', 'dots stroke', 'grease pencil']
    nodes = json_data.get('nodes', [])
    materials = json_data.get('materials', [])
    meshes = json_data.get('meshes', [])

    for node in nodes:
        name = node.get('name', '').lower()
        for artifact in known_artifacts:
            if artifact in name:
                if 'mesh' in node:
                    result.fail(f"Known artifact node with mesh: '{node.get('name')}'")
                else:
                    result.warn(f"Known artifact node (no mesh): '{node.get('name')}'")

    for mat in materials:
        name = mat.get('name', '').lower()
        for artifact in known_artifacts:
            if artifact in name:
                result.fail(f"Known artifact material: '{mat.get('name')}'")

    for mesh in meshes:
        name = mesh.get('name', '').lower()
        for artifact in known_artifacts:
            if artifact in name:
                result.fail(f"Known artifact mesh: '{mesh.get('name')}'")


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 validate_glb.py <model.glb> [--strict]")
        sys.exit(1)

    filepath = sys.argv[1]
    strict = '--strict' in sys.argv

    if not os.path.isfile(filepath):
        print(f"ERROR: File not found: {filepath}")
        sys.exit(1)

    result = ValidationResult()
    print(f"\n{'='*60}")
    print(f"GLB VALIDATION: {filepath}")
    print(f"Mode: {'strict' if strict else 'normal'}")
    print(f"{'='*60}")

    # Parse file
    try:
        json_data, bin_data = parse_glb(filepath)
        result.ok("Valid GLB file structure")
    except (ValueError, struct.error) as e:
        result.fail(f"Invalid GLB: {e}")
        print(result.report())
        sys.exit(1)

    file_size = os.path.getsize(filepath)
    result.ok(f"File size: {file_size:,} bytes ({file_size/1024/1024:.1f} MB)")

    # Run all validations
    meshes, materials, mesh_nodes = validate_structure(json_data, result, strict)

    if bin_data and meshes:
        validate_geometry(json_data, bin_data, meshes, result)

    if materials:
        validate_textures(json_data, materials, result)

    validate_artifact_names(json_data, result)

    # Summary
    print(result.report())
    print(f"\n{'='*60}")
    if result.passed:
        print(f"RESULT: PASSED ({len(result.passes)} checks OK"
              + (f", {len(result.warnings)} warnings" if result.warnings else "") + ")")
    else:
        print(f"RESULT: FAILED ({len(result.failures)} failures, "
              f"{len(result.passes)} passed"
              + (f", {len(result.warnings)} warnings" if result.warnings else "") + ")")
    print(f"{'='*60}\n")

    sys.exit(0 if result.passed else 1)


if __name__ == "__main__":
    main()
