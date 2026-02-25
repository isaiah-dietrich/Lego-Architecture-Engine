# 3D LEGO Building Generator (Java Backend)

**Status:** Portfolio-level, phase 1 development

**Goal:** Upload a 3D model of an office or house (OBJ/STL) and generate a decorative LEGO replica using voxelization and brick merging.

**Quality Standards:**
- Code must be clean, modular, deterministic, and production-quality
- NOT a toy project

## Phase 1: Project Scope

### What We Are Building
- [x] 3D mesh loading (OBJ only)
- [x] Mesh normalization
- [x] 3D voxelization
- [x] Surface extraction (hollowing)
- [x] Basic 3D brick merging (rectangular bricks only)
- [x] CLI execution (no Spring yet)

### What We Are NOT Building (Phase 1)
- ❌ Structural stability validation
- ❌ Physics simulation
- ❌ BrickLink integration
- ❌ Build instructions
- ❌ Database
- ❌ Authentication
- ❌ UI
- ❌ 3D rendering
- *Frontend will come later as separate phase*

## Project Architecture (STRICT)

### Package Structure
```
legomodel/
└── src/main/java/com/lego/
    ├── model/          # Immutable data objects
    ├── mesh/           # Parsing and geometry normalization
    ├── voxel/          # Voxel grid and voxelization logic
    ├── optimize/       # Brick merging
    ├── export/         # Export formats
    └── cli/            # CLI entry point only
```

### Rules
- ✋ No circular dependencies
- ✋ No static mutable global state
- ✋ No unnecessary external dependencies
- 📦 `model` contains only immutable data objects
- 📦 `mesh` handles parsing and geometry normalization
- 📦 `voxel` handles voxel grid and voxelization logic
- 📦 `optimize` handles brick merging
- 📦 `cli` contains only entry point logic
- ☕ Java 17+

## Core Data Models

### Vector3
- **Location:** `com.lego.model.Vector3`
- **Type:** Immutable record
- **Purpose:** 3D coordinate representation
```java
public record Vector3(double x, double y, double z) {}
```

### Triangle
- **Location:** `com.lego.model.Triangle`
- **Type:** Immutable record
- **Purpose:** Triangle representation for mesh geometry
```java
public record Triangle(Vector3 v1, Vector3 v2, Vector3 v3) {}
```

### Mesh
- **Location:** `com.lego.model.Mesh`
- **Type:** Immutable
- **Purpose:** Hold triangle list
- **Contents:** `List<Triangle> triangles`

### VoxelGrid
- **Location:** `com.lego.voxel.VoxelGrid`
- **Type:** Mutable voxel container
- **Purpose:** 3D boolean grid representation

**Required Fields:**
- `boolean[][][] grid` - 3D voxel array
- `int width` - X dimension
- `int height` - Y dimension
- `int depth` - Z dimension

**Required Methods:**
- Bounds-safe voxel access
- Filled voxel count calculation
- 6-directional neighbor lookup (±X, ±Y, ±Z)

### Brick (Future Phase)
- **Status:** NOT implemented in Phase 1
- **Will Represent:** 
  - `x, y, z` - Position
  - `width, depth, height` - Dimensions

## Mesh Loading (OBJ Parser)

### Location
`com.lego.mesh.ObjLoader`

### Supported OBJ Features
- ✅ `v` lines (vertices)
- ✅ `f` lines (triangles only)

### Ignored Features
- ❌ Normal vectors (`vn`)
- ❌ Texture coordinates (`vt`)
- ❌ Materials
- ❌ Quads (must be triangulated)

### Requirements
- All faces must be triangulated (exactly 3 vertices per face)
- Throw exception if face has more than 3 vertices
- Parse vertices into `Vector3` objects
- Parse faces into `Triangle` objects
- Return immutable `Mesh` object

### Load Pipeline
1. Parse vertices (v lines) → `List<Vector3>`
2. Parse faces (f lines) → validate 3 vertices per face
3. Build `Triangle` list from parsed vertices and face indices
4. Return `Mesh(List<Triangle> triangles)`

## Mesh Normalization

### Purpose
Prepare mesh for voxelization by centering and scaling to fit within voxel grid

### Pipeline
1. Compute bounding box (min/max of all vertices)
2. Translate mesh so minimum corner is at origin (0, 0, 0)
3. Scale mesh uniformly to fit within voxel grid bounds
4. Verify mesh fits inside voxel grid resolution

### Components Needed
- `com.lego.mesh.BoundingBox` - Calculate mesh bounds
- `com.lego.mesh.MeshNormalizer` - Apply transformations

## Voxelization

### Location
`com.lego.voxel.Voxelizer`

### Algorithm: Ray Casting (Brute Force)
For each voxel center:
1. Cast ray in +X direction
2. Count triangle intersections
3. If count is odd → voxel is inside mesh, mark as filled
4. If count is even → voxel is outside mesh

### Grid Configuration
- **Default Resolution:** 40×40×40
- **Bounds:** Normalized mesh coordinates

### Performance Target
- Process ~10k triangle meshes in < 2 seconds

### Output
- Filled `VoxelGrid` with both interior and boundary voxels marked

## Surface Extraction (Hollowing)

### Location
`com.lego.voxel.SurfaceExtractor`

### Purpose
Convert solid voxel grid into hollow shell by removing interior voxels

### Algorithm
After voxelization:
1. Iterate through all filled voxels
2. Check all 6 neighbors (±X, ±Y, ±Z)
3. Keep voxel only if at least one neighbor is empty
4. Remove all interior voxels with 6 filled neighbors

### Output
- Voxel grid containing only surface voxels
- Creates hollow shell model suitable for LEGO

## CLI Implementation

### Location
`com.lego.cli.Main`

### Entry Point
```java
public class Main {
    public static void main(String[] args) {
        // Implementation
    }
}
```

### Usage
```bash
java -jar legomodel.jar path/to/model.obj 40
```
- Argument 1: Path to OBJ file
- Argument 2: Voxel grid resolution (e.g., 40 = 40×40×40)

### Output (STDOUT)
- Triangle count (from loaded mesh)
- Grid resolution (e.g., "40×40×40")
- Total voxel count (all filled voxels after voxelization)
- Surface voxel count (after hollowing)

### Constraints
- ❌ No UI
- ❌ No interactive prompts
- ✅ Deterministic output

## Code Quality Standards

### Requirements
- ✅ Use records where appropriate
- ✅ Favor immutability (immutable where possible)
- ✅ Keep methods under 40 lines
- ✅ Extract private helper methods
- ✅ Avoid nested if-statements when possible
- ✅ Use descriptive method names
- ✅ No premature optimization
- ✅ **Ensure code compiles**

### Version
- ☕ Java 17+

## AI Collaboration Guidelines

### Rules
1. ✅ Generate only what is requested
2. ✅ Do NOT invent new architecture
3. ✅ Do NOT introduce external dependencies unless asked
4. ✅ Provide full file contents
5. ✅ **Ensure code compiles**
6. ✅ Ask for clarification if uncertain
7. ✅ Do NOT generate entire project at once

### Build Order (Strict Sequence)
- [ ] `pom.xml` - Maven configuration
- [ ] `Vector3` - Core data model
- [ ] `Triangle` - Core data model
- [ ] `Mesh` - Core data model
- [ ] `ObjLoader` - Mesh loading
- [ ] `BoundingBox` - Mesh utilities
- [ ] `MeshNormalizer` - Mesh utilities
- [ ] `VoxelGrid` - Voxel data structure
- [ ] `Voxelizer` - Voxelization algorithm
- [ ] `SurfaceExtractor` - Surface extraction
- [ ] `Main` - CLI entry point

**⚠️ Only generate what is explicitly requested. Stop at checkpoint.**
