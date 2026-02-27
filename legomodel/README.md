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
java -jar target/legomodel.jar path/to/model.obj 40
```

Arguments:
- `path/to/model.obj` - Path to OBJ file
- `40` - Voxel grid resolution (40×40×40)

## Requirements

- Java 17+
- Maven 3.6+

## Code Quality

- Clean, modular, production-quality Java code
- Immutability-first design
- Methods kept under 40 lines
- No circular dependencies
- No unnecessary external dependencies
