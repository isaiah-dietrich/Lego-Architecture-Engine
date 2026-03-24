# LEGO Architecture Engine

A Java-based 3D LEGO replica generator that converts 3D models (OBJ format) into decorative LEGO structures using voxelization and brick merging algorithms.

Lego database from - rebrickable.com

## Phase 1 Deliverables

- [x] 3D mesh loading (OBJ parser)
- [x] Mesh normalization and scaling
- [x] 3D voxelization (ray-casting algorithm)
- [x] Surface extraction (hollowing)
- [x] CLI execution

## Project Structure

```
legomodel/
└── src/main/java/com/lego/
    ├── model/      # Immutable data objects
    ├── mesh/       # OBJ parsing and normalization
    ├── voxel/      # Voxelization and surface extraction
    ├── optimize/   # Brick merging algorithms
    ├── export/     # Export formats
    └── cli/        # Command-line interface
```

## Building

```bash
mvn clean package
```

## Running

```bash
java -jar target/legomodel.jar path/to/model.obj 40 [output.obj] [exportMode] [voxelizerMode]
```

Arguments:
- `path/to/model.obj` - Path to OBJ file
- `40` - Voxel grid resolution (40×40×40)
- `output.obj` (optional) - Output OBJ path
- `exportMode` (optional) - `brick` (default), `voxel-surface`, `voxel-solid`
- `voxelizerMode` (optional) - `legacy` (default), `topological` (placeholder scaffold)

## Exact Geometry Collision Check (LDraw)

For `.ldr` outputs, a mesh-based collision diagnostic script is available:

```bash
python3 scripts/exact_geometry_collision_check.py output/lab40v2_color.ldr --mode slope
```

Options:
- `--parts-dir` path to local LDraw library (default: `/Applications/Studio 2.0/ldraw`)
- `--step` LDU sampling step (smaller is stricter/slower; default `2.0`)
- `--mode` `slope` (default, slope-involved pairs only) or `all`

## Requirements

- Java 17+
- Maven 3.6+

## Code Quality

- Clean, modular, production-quality Java code
- Immutability-first design
- Methods kept under 40 lines
- No circular dependencies
- No unnecessary external dependencies
