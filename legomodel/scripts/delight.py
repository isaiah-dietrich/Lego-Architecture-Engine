#!/usr/bin/env python3
"""
Blender headless script: strip baked lighting from GLB textures.

For each material in the GLB, inspects whether the base-color texture
likely contains baked lighting (shadows, AO, specular). If so, rewires
the shader to Emission and re-bakes a clean albedo texture. Materials
with separate AO/normal maps (PBR workflow) or vertex colors are left
unchanged since their base color is already clean.

Usage:
    blender --background --python scripts/delight.py -- input.glb output.glb [--bake-size 2048]
    blender --background --python scripts/delight.py -- input.glb output.glb --mode retinex

Modes:
    emit    — (default) Rewire shader to Emission and re-bake. Only works when
              lighting comes from the shader graph, not baked into texture pixels.
    retinex — Process texture pixels directly: separate lightness from color in
              L*a*b*, remove low-frequency lightness variation (shadows/AO), and
              normalize to a uniform target lightness. Works even when shadows
              are baked directly into the base color texture.

Requires Blender 3.0+ with glTF importer/exporter enabled (default).
"""
import sys
import os
import math
import argparse

import bpy


# ---------------------------------------------------------------------------
#  CLI argument parsing (arguments after "--" in blender command line)
# ---------------------------------------------------------------------------

def parse_args():
    argv = sys.argv
    # Everything after "--" is our script's arguments
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
    else:
        argv = []

    parser = argparse.ArgumentParser(description="De-light a GLB model")
    parser.add_argument("input", help="Path to input .glb file")
    parser.add_argument("output", help="Path to output .glb file")
    parser.add_argument("--bake-size", type=int, default=2048,
                        help="Resolution of baked textures (default: 2048)")
    parser.add_argument("--l-stddev-threshold", type=float, default=20.0,
                        help="L* stddev above which texture is considered baked (default: 20.0)")
    parser.add_argument("--mode", choices=["emit", "retinex"], default="emit",
                        help="De-lighting mode: 'emit' (shader rebake) or 'retinex' (pixel-level) (default: emit)")
    parser.add_argument("--blur-radius", type=int, default=32,
                        help="Retinex mode: blur kernel radius in pixels (default: 32)")
    parser.add_argument("--target-L", type=float, default=65.0,
                        help="Retinex mode: target median L* after normalization (default: 65.0)")
    parser.add_argument("--strip-artifacts", action="store_true", default=False,
                        help="Remove non-textured meshes and stray objects (whiskers, helpers, grease pencil) before export")
    return parser.parse_args(argv)


# ---------------------------------------------------------------------------
#  Texture lightness analysis
# ---------------------------------------------------------------------------

def srgb_to_linear(c):
    """sRGB component [0,1] → linear."""
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_L(r, g, b):
    """Linear RGB → CIE L* (lightness only, D65)."""
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    if y > 0.008856:
        return 116.0 * (y ** (1.0 / 3.0)) - 16.0
    else:
        return 903.3 * y


def texture_lightness_stddev(image):
    """Compute standard deviation of L* across all opaque pixels in a Blender image."""
    pixels = list(image.pixels)  # flat RGBA list
    width, height = image.size[0], image.size[1]
    total = width * height

    # Sample up to 10000 pixels for performance
    step = max(1, total // 10000)
    L_values = []
    for i in range(0, total, step):
        idx = i * 4
        a = pixels[idx + 3]
        if a < 0.5:
            continue  # skip transparent pixels
        # Blender stores texture pixels in sRGB for 8-bit images
        sr, sg, sb = pixels[idx], pixels[idx + 1], pixels[idx + 2]
        lr = srgb_to_linear(max(0.0, min(1.0, sr)))
        lg = srgb_to_linear(max(0.0, min(1.0, sg)))
        lb = srgb_to_linear(max(0.0, min(1.0, sb)))
        L_values.append(linear_to_L(lr, lg, lb))

    if len(L_values) < 100:
        return 0.0  # not enough pixels to judge

    mean_L = sum(L_values) / len(L_values)
    variance = sum((v - mean_L) ** 2 for v in L_values) / len(L_values)
    return math.sqrt(variance)


# ---------------------------------------------------------------------------
#  Retinex de-lighting (pixel-level shadow removal)
# ---------------------------------------------------------------------------

def linear_to_srgb(c):
    """Linear component [0,1] → sRGB."""
    if c <= 0.0031308:
        return 12.92 * c
    else:
        return 1.055 * (c ** (1.0 / 2.4)) - 0.055


def linear_rgb_to_lab(r, g, b):
    """Linear RGB → CIE L*a*b* (D65)."""
    x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
    x /= 0.95047
    z /= 1.08883

    def f(t):
        return t ** (1.0 / 3.0) if t > 0.008856 else (903.3 * t + 16.0) / 116.0

    return (116.0 * f(y) - 16.0, 500.0 * (f(x) - f(y)), 200.0 * (f(y) - f(z)))


def lab_to_linear_rgb(L, a, b):
    """CIE L*a*b* (D65) → linear RGB, clamped to [0,1]."""
    fy = (L + 16.0) / 116.0
    fx = a / 500.0 + fy
    fz = fy - b / 200.0

    def finv(t):
        return t ** 3.0 if t > 0.206893 else (t - 16.0 / 116.0) / 7.787

    x = finv(fx) * 0.95047
    y = finv(fy)
    z = finv(fz) * 1.08883

    r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    b_ = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

    return (max(0.0, min(1.0, r)), max(0.0, min(1.0, g)), max(0.0, min(1.0, b_)))


def box_blur_2d(grid, width, height, radius):
    """
    Fast box blur on a 1D array representing a 2D grid.
    Two-pass (horizontal then vertical) for O(n) per pixel.
    """
    # Horizontal pass
    temp = [0.0] * len(grid)
    for y in range(height):
        row_start = y * width
        running = 0.0
        count = 0
        # Initialize window for first pixel
        for x in range(min(radius + 1, width)):
            running += grid[row_start + x]
            count += 1
        temp[row_start] = running / count

        for x in range(1, width):
            # Add right edge
            right = x + radius
            if right < width:
                running += grid[row_start + right]
                count += 1
            # Remove left edge
            left = x - radius - 1
            if left >= 0:
                running -= grid[row_start + left]
                count -= 1
            temp[row_start + x] = running / count

    # Vertical pass
    result = [0.0] * len(grid)
    for x in range(width):
        running = 0.0
        count = 0
        for y in range(min(radius + 1, height)):
            running += temp[y * width + x]
            count += 1
        result[x] = running / count

        for y in range(1, height):
            right = y + radius
            if right < height:
                running += temp[right * width + x]
                count += 1
            left = y - radius - 1
            if left >= 0:
                running -= temp[left * width + x]
                count -= 1
            result[y * width + x] = running / count

    return result


def retinex_delight_image(image, blur_radius, target_L):
    """
    Retinex-style de-lighting: remove low-frequency lightness variation.

    1. Convert every pixel to L*a*b*
    2. Box-blur the L* channel to get local average lightness
    3. For each pixel: L_new = L_original / L_blurred * target_L
       This divides out shadows (low L_blurred) and highlights (high L_blurred),
       normalizing everything to a uniform lightness while preserving hue.
    4. Convert back to sRGB and write into the image.
    """
    pixels = list(image.pixels)  # flat RGBA
    width, height = image.size[0], image.size[1]
    total = width * height

    # Step 1: Convert to L*a*b*
    L_grid = [0.0] * total
    a_grid = [0.0] * total
    b_grid = [0.0] * total
    alpha_grid = [1.0] * total

    for i in range(total):
        idx = i * 4
        sr, sg, sb = pixels[idx], pixels[idx + 1], pixels[idx + 2]
        alpha_grid[i] = pixels[idx + 3]

        lr = srgb_to_linear(max(0.0, min(1.0, sr)))
        lg = srgb_to_linear(max(0.0, min(1.0, sg)))
        lb = srgb_to_linear(max(0.0, min(1.0, sb)))

        L, a, b = linear_rgb_to_lab(lr, lg, lb)
        L_grid[i] = L
        a_grid[i] = a
        b_grid[i] = b

    # Step 2: Box-blur the L* channel
    print(f"    Blurring L* channel (radius={blur_radius})...")
    L_blurred = box_blur_2d(L_grid, width, height, blur_radius)

    # Step 3: Retinex normalization  L_new = (L / L_blur) * target
    for i in range(total):
        if alpha_grid[i] < 0.5:
            continue  # skip transparent
        L_local = L_blurred[i]
        if L_local < 1.0:
            L_local = 1.0  # avoid division by near-zero
        L_new = (L_grid[i] / L_local) * target_L
        L_grid[i] = max(0.0, min(100.0, L_new))

    # Step 4: Convert back to sRGB
    print("    Converting back to sRGB...")
    new_pixels = [0.0] * len(pixels)
    for i in range(total):
        idx = i * 4
        if alpha_grid[i] < 0.5:
            # Keep original transparent pixels
            new_pixels[idx] = pixels[idx]
            new_pixels[idx + 1] = pixels[idx + 1]
            new_pixels[idx + 2] = pixels[idx + 2]
            new_pixels[idx + 3] = pixels[idx + 3]
            continue

        lr, lg, lb = lab_to_linear_rgb(L_grid[i], a_grid[i], b_grid[i])
        new_pixels[idx] = linear_to_srgb(lr)
        new_pixels[idx + 1] = linear_to_srgb(lg)
        new_pixels[idx + 2] = linear_to_srgb(lb)
        new_pixels[idx + 3] = alpha_grid[i]

    image.pixels[:] = new_pixels
    image.update()
    image.pack()
    print(f"    Done — {total} pixels processed")


def retinex_process_materials(args):
    """
    Retinex mode: process all base-color textures in-place, regardless
    of shader graph structure. Works on any texture with baked lighting.
    """
    processed = set()  # track images already processed (shared across materials)

    for mat in bpy.data.materials:
        if not mat.use_nodes or not mat.node_tree:
            print(f"  {mat.name}: SKIP — no node tree")
            continue

        tree = mat.node_tree
        bsdf = find_node(tree, 'BSDF_PRINCIPLED')
        if not bsdf:
            print(f"  {mat.name}: SKIP — no Principled BSDF")
            continue

        base_node = find_linked_image(tree, "Base Color", bsdf)
        if not base_node or not base_node.image:
            print(f"  {mat.name}: SKIP — no base color texture")
            continue

        image = base_node.image
        if image.name in processed:
            print(f"  {mat.name}: SKIP — texture '{image.name}' already processed")
            continue

        l_std = texture_lightness_stddev(image)
        print(f"  {mat.name}: RETINEX de-lighting (L* stddev={l_std:.1f}, "
              f"blur={args.blur_radius}, target_L={args.target_L})")

        retinex_delight_image(image, args.blur_radius, args.target_L)
        processed.add(image.name)

    return len(processed)


# ---------------------------------------------------------------------------
#  Material classification
# ---------------------------------------------------------------------------

def find_node(node_tree, node_type):
    """Find the first node of a given type in a node tree."""
    for node in node_tree.nodes:
        if node.type == node_type:
            return node
    return None


def find_linked_image(node_tree, input_name, bsdf_node):
    """Find the image texture linked to a specific BSDF input."""
    for link in node_tree.links:
        if link.to_node == bsdf_node and link.to_socket.name == input_name:
            if link.from_node.type == 'TEX_IMAGE' and link.from_node.image:
                return link.from_node
    return None


def classify_material(mat, l_threshold):
    """
    Classify a material's base-color texture.

    Returns:
        ("delight", image_node, bsdf_node) — needs de-lighting
        ("clean", reason)                  — already clean, skip
    """
    if not mat.use_nodes or not mat.node_tree:
        return ("clean", "no node tree")

    tree = mat.node_tree
    bsdf = find_node(tree, 'BSDF_PRINCIPLED')
    if not bsdf:
        return ("clean", "no Principled BSDF")

    base_color_node = find_linked_image(tree, "Base Color", bsdf)
    if not base_color_node:
        return ("clean", "no base color texture (flat color only)")

    # Check for separate AO map — indicates PBR workflow with clean albedo
    has_ao = any(
        link.to_socket.name in ("Occlusion", "Fac")
        and link.from_node.type == 'TEX_IMAGE'
        for link in tree.links
    )
    has_normal = find_linked_image(tree, "Normal", bsdf) is not None

    if has_ao and has_normal:
        return ("clean", "PBR workflow (separate AO + normal) — albedo is clean")

    # Analyze texture lightness variance
    image = base_color_node.image
    l_std = texture_lightness_stddev(image)

    if l_std < l_threshold:
        return ("clean", f"low L* stddev ({l_std:.1f} < {l_threshold}) — likely clean")

    return ("delight", base_color_node, bsdf)


# ---------------------------------------------------------------------------
#  Shader rewiring: Principled BSDF → Emission
# ---------------------------------------------------------------------------

def rewire_to_emission(mat, base_color_node, bsdf_node):
    """Replace Principled BSDF with Emission shader wired to the base color texture."""
    tree = mat.node_tree

    # Find the Material Output node
    output_node = find_node(tree, 'OUTPUT_MATERIAL')
    if not output_node:
        return False

    # Create Emission shader
    emission = tree.nodes.new('ShaderNodeEmission')
    emission.location = bsdf_node.location

    # Wire: base color texture → Emission color
    tree.links.new(base_color_node.outputs["Color"], emission.inputs["Color"])
    emission.inputs["Strength"].default_value = 1.0

    # Wire: Emission → Material Output surface
    # Remove existing links to output surface
    for link in list(tree.links):
        if link.to_node == output_node and link.to_socket.name == "Surface":
            tree.links.remove(link)
    tree.links.new(emission.outputs["Emission"], output_node.inputs["Surface"])

    # Remove the old BSDF to keep things clean
    tree.nodes.remove(bsdf_node)

    return True


# ---------------------------------------------------------------------------
#  Baking
# ---------------------------------------------------------------------------

def create_bake_target(mat, image_node, bake_size):
    """Create a new image for baking and an Image Texture node to hold it."""
    tree = mat.node_tree
    src_image = image_node.image

    # Create new image with same name + "_delit" suffix
    name = src_image.name.rsplit(".", 1)[0] + "_delit"
    bake_image = bpy.data.images.new(
        name=name,
        width=bake_size,
        height=bake_size,
        alpha=True,
    )
    bake_image.colorspace_settings.name = 'sRGB'

    # Create a new Image Texture node for the bake target
    bake_node = tree.nodes.new('ShaderNodeTexImage')
    bake_node.image = bake_image
    bake_node.location = (image_node.location[0], image_node.location[1] - 300)

    # Baking writes to the SELECTED (active) image texture node
    for node in tree.nodes:
        node.select = False
    bake_node.select = True
    tree.nodes.active = bake_node

    return bake_image, bake_node


def finalize_bake(mat, bake_image, bake_node, emission_node, output_node):
    """After baking, wire the baked image to Emission and pack it."""
    tree = mat.node_tree

    # Wire baked image → Emission color (replacing original texture)
    for link in list(tree.links):
        if link.to_node == emission_node and link.to_socket.name == "Color":
            tree.links.remove(link)
    tree.links.new(bake_node.outputs["Color"], emission_node.inputs["Color"])

    # Pack the baked image into the blend file (so it exports with the GLB)
    bake_image.pack()


# ---------------------------------------------------------------------------
#  Main pipeline
# ---------------------------------------------------------------------------

def main():
    args = parse_args()

    if not os.path.isfile(args.input):
        print(f"ERROR: Input file not found: {args.input}")
        sys.exit(1)

    # Clear the default scene
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()

    # Import GLB
    print(f"\n=== Importing {args.input} ===")
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(args.input))

    # Remove all lights from scene
    for obj in list(bpy.data.objects):
        if obj.type == 'LIGHT':
            bpy.data.objects.remove(obj, do_unlink=True)

    # Set world background to white (flat ambient)
    world = bpy.data.worlds.get("World")
    if world is None:
        world = bpy.data.worlds.new("World")
    bpy.context.scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (1, 1, 1, 1)
        bg.inputs["Strength"].default_value = 1.0

    # Configure render engine for baking
    bpy.context.scene.render.engine = 'CYCLES'
    bpy.context.scene.cycles.device = 'CPU'
    bpy.context.scene.cycles.samples = 1  # Emission bake needs only 1 sample
    bpy.context.scene.cycles.bake_type = 'EMIT'

    # ---- Mode dispatch ----
    if args.mode == "retinex":
        print(f"\n=== Retinex de-lighting ({len(bpy.data.materials)} materials) ===")
        count = retinex_process_materials(args)
        if count == 0:
            print("\n=== No textures to process ===")
    else:
        # Emit mode: classify and selectively re-bake
        materials_to_bake = []
        print(f"\n=== Analyzing {len(bpy.data.materials)} materials ===")

        for mat in bpy.data.materials:
            result = classify_material(mat, args.l_stddev_threshold)

            if result[0] == "clean":
                print(f"  {mat.name}: PASS-THROUGH — {result[1]}")
            else:
                _, base_color_node, bsdf_node = result
                l_std = texture_lightness_stddev(base_color_node.image)
                print(f"  {mat.name}: DE-LIGHTING (L* stddev={l_std:.1f})")

                if rewire_to_emission(mat, base_color_node, bsdf_node):
                    # base_color_node is still valid (we only removed the BSDF)
                    bake_image, bake_node = create_bake_target(
                        mat, base_color_node, args.bake_size)
                    materials_to_bake.append((mat, bake_image, bake_node))

    # Bake all materials that need de-lighting (emit mode only)
    if args.mode == "emit" and materials_to_bake:
        print(f"\n=== Baking {len(materials_to_bake)} materials ===")

        # Select all mesh objects for baking
        bpy.ops.object.select_all(action='DESELECT')
        for obj in bpy.data.objects:
            if obj.type == 'MESH':
                obj.select_set(True)
                bpy.context.view_layer.objects.active = obj

                # Ensure object has UV map
                if not obj.data.uv_layers:
                    print(f"  WARNING: {obj.name} has no UV map — skipping bake")
                    continue

        # Bake emission pass
        try:
            bpy.ops.object.bake(type='EMIT')
            print("  Bake complete.")
        except RuntimeError as e:
            print(f"  ERROR during bake: {e}")
            sys.exit(1)

        # Finalize: wire baked images, pack them
        for mat, bake_image, bake_node in materials_to_bake:
            tree = mat.node_tree
            emission = find_node(tree, 'EMISSION')
            output_node = find_node(tree, 'OUTPUT_MATERIAL')
            if emission and output_node:
                finalize_bake(mat, bake_image, bake_node, emission, output_node)
                print(f"  {mat.name}: baked texture assigned")
    elif args.mode == "emit":
        print("\n=== All materials are clean — no baking needed ===")

    # ---- Strip artifact objects and geometry ----
    if args.strip_artifacts:
        print(f"\n=== Stripping artifacts ===")
        removed = []

        # --- Phase 1: Remove entire artifact objects ---
        # Find the main mesh (largest face count) for comparison
        all_meshes = [o for o in bpy.data.objects if o.type == 'MESH']
        if all_meshes:
            main_face_count = max(len(o.data.polygons) for o in all_meshes)
        else:
            main_face_count = 0

        for obj in list(bpy.data.objects):
            if obj.type != 'MESH':
                continue

            mesh_data = obj.data
            verts = len(mesh_data.vertices)
            faces = len(mesh_data.polygons)
            should_remove = False
            reason = ""

            # No materials — stray helper (Icosphere, etc.)
            if not mesh_data.materials or len(mesh_data.materials) == 0:
                should_remove = True
                reason = "no material"

            # High sliver percentage — strand/whisker/fur geometry
            elif faces > 0:
                sliver_count = 0
                sample_step = max(1, faces // 2000)
                sampled = 0
                for fi in range(0, faces, sample_step):
                    poly = mesh_data.polygons[fi]
                    if len(poly.vertices) >= 3:
                        v1 = mesh_data.vertices[poly.vertices[0]]
                        v2 = mesh_data.vertices[poly.vertices[1]]
                        v3 = mesh_data.vertices[poly.vertices[2]]
                        e = [
                            math.sqrt(sum((a-b)**2 for a,b in zip(v1.co, v2.co))),
                            math.sqrt(sum((a-b)**2 for a,b in zip(v2.co, v3.co))),
                            math.sqrt(sum((a-b)**2 for a,b in zip(v3.co, v1.co))),
                        ]
                        longest = max(e)
                        s = sum(e) / 2.0
                        area_sq = s * (s - e[0]) * (s - e[1]) * (s - e[2])
                        if area_sq > 0:
                            area = math.sqrt(area_sq)
                            alt = 2.0 * area / longest
                            if alt > 1e-10 and longest / alt > 20:
                                sliver_count += 1
                        else:
                            sliver_count += 1
                        sampled += 1
                if sampled > 0:
                    sliver_pct = 100.0 * sliver_count / sampled
                    if sliver_pct > 40:
                        should_remove = True
                        reason = f"strand geometry ({sliver_pct:.0f}% sliver faces, {verts}v/{faces}f)"

            # Small sub-mesh with no significant geometry
            if not should_remove and main_face_count > 0 and faces < main_face_count * 0.10:
                should_remove = True
                reason = f"small sub-mesh ({faces}/{main_face_count} faces = {100*faces/main_face_count:.1f}%)"

            if should_remove:
                removed.append(f"object '{obj.name}' ({reason})")
                mesh_name = mesh_data.name
                # Delete object from all collections
                for col in list(obj.users_collection):
                    col.objects.unlink(obj)
                bpy.data.objects.remove(obj, do_unlink=True)
                # Delete orphan mesh data block
                if mesh_name in bpy.data.meshes:
                    orphan = bpy.data.meshes[mesh_name]
                    if orphan.users == 0:
                        bpy.data.meshes.remove(orphan)

        # --- Phase 2: Clean tiny components within remaining meshes ---
        import bmesh
        for obj in list(bpy.data.objects):
            if obj.type != 'MESH':
                continue
            mesh_data = obj.data
            total_verts = len(mesh_data.vertices)
            if total_verts < 100:
                continue

            # Union-find to identify components
            parent = list(range(total_verts))

            def find(x):
                while parent[x] != x:
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                return x

            def union(a, b):
                a, b = find(a), find(b)
                if a != b:
                    parent[a] = b

            for edge in mesh_data.edges:
                union(edge.vertices[0], edge.vertices[1])

            # Group vertices by component
            from collections import defaultdict
            comp_verts = defaultdict(set)
            for i in range(total_verts):
                comp_verts[find(i)].add(i)

            # Find the largest component size
            max_comp = max(len(v) for v in comp_verts.values())
            # Threshold: components < 1% of largest are artifacts
            threshold = max(max_comp * 0.01, 10)

            tiny_verts = set()
            for root, vert_set in comp_verts.items():
                if len(vert_set) < threshold:
                    tiny_verts.update(vert_set)

            if tiny_verts:
                # Use bmesh to delete tiny component vertices
                bm = bmesh.new()
                bm.from_mesh(mesh_data)
                bm.verts.ensure_lookup_table()

                verts_to_remove = [bm.verts[vi] for vi in tiny_verts if vi < len(bm.verts)]
                bmesh.ops.delete(bm, geom=verts_to_remove, context='VERTS')

                bm.to_mesh(mesh_data)
                bm.free()
                mesh_data.update()

                new_verts = len(mesh_data.vertices)
                new_faces = len(mesh_data.polygons)
                removed.append(
                    f"{obj.name}: removed {len(tiny_verts)} vertices from "
                    f"{len(comp_verts) - sum(1 for v in comp_verts.values() if len(v) >= threshold)} "
                    f"tiny components ({total_verts}v→{new_verts}v, "
                    f"{len(mesh_data.polygons)}f remaining)")

        # --- Phase 3: Clean orphaned materials ---
        for mat in list(bpy.data.materials):
            if mat.users == 0:
                removed.append(f"material '{mat.name}' (orphaned)")
                bpy.data.materials.remove(mat)

        # --- Phase 4: Purge ALL orphan data blocks ---
        # bpy.data.objects.remove() doesn't always clean up mesh/material
        # data blocks. Orphan purge is needed so the glTF exporter
        # doesn't re-include deleted geometry.
        for _ in range(3):  # iterate — purging can create new orphans
            bpy.ops.outliner.orphans_purge(
                do_local_ids=True, do_linked_ids=True, do_recursive=True)

        if removed:
            for r in removed:
                print(f"  {r}")

            # Verify
            remaining_meshes = [o for o in bpy.data.objects if o.type == 'MESH']
            print(f"  --- remaining: {len(remaining_meshes)} mesh object(s), "
                  f"{len(bpy.data.materials)} material(s), "
                  f"{len(bpy.data.meshes)} mesh data block(s)")
            for o in remaining_meshes:
                print(f"      {o.name}: {len(o.data.vertices)}v {len(o.data.polygons)}f")
        else:
            print("  Nothing to strip")

    # Export cleaned GLB
    output_path = os.path.abspath(args.output)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    print(f"\n=== Exporting {output_path} ===")
    bpy.ops.export_scene.gltf(
        filepath=output_path,
        export_format='GLB',
        export_image_format='AUTO',
    )
    print("Done.\n")


if __name__ == "__main__":
    main()
