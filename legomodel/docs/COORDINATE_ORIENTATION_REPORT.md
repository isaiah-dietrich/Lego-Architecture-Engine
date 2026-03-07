# Coordinate Orientation Report

Date: 2026-03-07

## Scope
Current orientation and display behavior for:
- OBJ model viewing in your VS Code OBJ extension
- LDraw export/viewing in BrickLink Studio

## Current Orientation Contract (Code)

### Canonical internal/world axes
- `X` = width (left/right)
- `Y` = height (up/down)
- `Z` = depth (front/back)

### OBJ ingest/normalize keeps axes unchanged
- Vertices are translated (subtract bounding box min) and scaled uniformly, with no axis swapping:
  - `x = (v.x() - box.minX()) * scale`
  - `y = (v.y() - box.minY()) * scale`
  - `z = (v.z() - box.minZ()) * scale`
- X stays X, Y stays Y, Z stays Z throughout normalization.
- Reference: `src/main/java/com/lego/mesh/MeshNormalizer.java:75-79`

### Voxel grid is axis-labeled as width/height/depth
- `VoxelGrid(width, height, depth)` maps to `(x, y, z)`
- Reference: `src/main/java/com/lego/voxel/VoxelGrid.java:19-22`

### Brick semantics are now Y-up
- `maxX = x + studX`
- `maxY = y + heightUnits` (vertical)
- `maxZ = z + studY` (depth footprint)
- Reference: `src/main/java/com/lego/model/Brick.java:48-66`

### Brick placement is now Y-layered with XZ footprint
- Layer loop is `for (int y = 0; y < surface.height(); y++)`
- Brick footprint checks use `(x, y, z)` with `studX` across X and `studY` across Z
- Reference: `src/main/java/com/lego/optimize/BrickPlacer.java:87-93`
- Reference: `src/main/java/com/lego/optimize/BrickPlacer.java:145-160`

### OBJ exporters emit geometry in the same XYZ frame
- `BrickObjExporter` uses `brick.x/y/z` + `maxX/maxY/maxZ` directly
- `VoxelObjExporter` writes each voxel cube as `[x,x+1] x [y,y+1] x [z,z+1]`
- Reference: `src/main/java/com/lego/export/BrickObjExporter.java:60-66`
- Reference: `src/main/java/com/lego/export/VoxelObjExporter.java:22`
- Note: some face-label comments in these exporters still describe Z as top/bottom; geometry itself follows current XYZ semantics.

### LDraw exporter maps canonical XYZ into LDraw coordinates
- `x_ldu = (x + studX/2) * 20`
- `z_ldu = (z + studY/2) * 20`
- `y_ldu = -((y + heightUnits) * 24)` (negative Y is up in LDraw)
- Reference: `src/main/java/com/lego/export/LDrawExporter.java:67-79`

## VS Code OBJ Extension vs BrickLink Studio

### VS Code OBJ extension (your `.obj` preview)
- Reads OBJ `v`/`f` mesh geometry and renders it directly in OBJ XYZ space.
- No LDraw unit conversion is applied.
- What you see is raw exported geometry from `brick` / `voxel-*` OBJ modes.

### BrickLink Studio (your `.ldr` preview)
- Reads LDraw assembly lines (`1 color x y z a..i part.dat`), not triangle faces.
- Uses LDraw units and LDraw axis convention (`-Y` is up).
- Geometry comes from LDraw part library (`*.dat`), positioned by exporter transforms.

### Why the same model can look different
- Different file semantics: mesh (`.obj`) vs assembly (`.ldr`).
- Different unit systems: voxel-like OBJ units vs LDU in LDraw.
- Different viewer defaults (camera/orbit/grid), even when orientation is correct.

## Output File Audit

### `output/wolf.ldr`
- Content starts with `# LEGO Architecture Engine voxel export`
- That is OBJ-format content with `.ldr` extension (not a valid LDraw assembly file).
- Reference: `output/wolf.ldr:1`

### `output/pyramid.ldr`
- Content starts with `0 LEGO Architecture Engine LDraw export`
- Contains valid type-1 part placement lines for Studio.
- Reference: `output/pyramid.ldr:1`

## Test Validation (Current State)

Executed:

```bash
mvn -q -f legomodel/pom.xml -Dtest=BrickTest,BrickPlacerTest,BrickObjExporterTest,MainTest test
```

Result: pass (exit code 0).

This indicates the current Y-up brick semantics are now internally consistent across:
- `Brick`
- `BrickPlacer`
- OBJ brick export
- CLI + LDraw export path

## Practical Usage Guidance

1. For VS Code extension viewing, open `*.obj` produced by `exportMode=brick` or `voxel-*`.
2. For BrickLink Studio, generate with `exportMode=ldraw` and use a true `.ldr` output path.
3. Do not rely on file extension alone; verify header lines:
   - OBJ: starts with `#` comments plus `v`/`f`
   - LDraw: starts with `0 ...` and part lines starting with `1 `
