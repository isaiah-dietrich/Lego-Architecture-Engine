"""Inspect mesh objects in a GLB file."""
import bpy
import sys

argv = sys.argv
if "--" in argv:
    argv = argv[argv.index("--") + 1:]
else:
    argv = []

filepath = argv[0] if argv else "models/labrador_dog.glb"

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
bpy.ops.import_scene.gltf(filepath=filepath)

for obj in bpy.data.objects:
    if obj.type == 'MESH':
        verts = len(obj.data.vertices) if obj.data else 0
        faces = len(obj.data.polygons) if obj.data else 0
        mats = [m.name for m in obj.data.materials] if obj.data and obj.data.materials else []
        print(f"MESH: {obj.name} | verts={verts} faces={faces} mats={mats}")
    else:
        print(f"OTHER: {obj.name} | type={obj.type}")

print("\n--- Materials ---")
for mat in bpy.data.materials:
    print(f"Material: {mat.name}")
    if mat.use_nodes and mat.node_tree:
        for node in mat.node_tree.nodes:
            print(f"  Node: {node.name} type={node.type}")
