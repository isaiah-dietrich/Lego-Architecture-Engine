# Project Pipeline

## Purpose

This document describes the end-to-end pipeline for the LEGO Architecture Engine.

It covers the full project flow:

1. model ingestion
2. geometry normalization
3. voxelization
4. surface extraction
5. brick placement
6. optional color processing
7. export
8. optional analysis and diagnostics

The main runtime orchestration lives in:

- `legomodel/src/main/java/com/lego/cli/Main.java`

## High-Level Flow

```text
CLI args
  -> parse options
  -> choose loader by file extension
  -> load model into Mesh / LoadedModel
  -> normalize mesh into voxel space
  -> voxelize geometry
  -> derive surface grid
  -> choose placement policy
  -> load allowed brick dimensions
  -> place bricks
  -> optionally compute colors
  -> export result
  -> optionally run stepping analysis
```

## Stage 1: CLI Entry And Option Parsing

Primary component:

- `legomodel/src/main/java/com/lego/cli/Main.java`

Responsibilities:

- parse positional arguments and flags
- validate resolution
- validate export mode
- validate voxelizer mode
- validate color mode and color algorithm
- resolve placement policy

Important decisions made here:

- input format is inferred from file extension
- voxelizer mode is `topological` or `legacy`
- export mode is `brick`, `voxel-surface`, `voxel-solid`, or `ldraw`
- color processing only matters for GLB-based color-aware LDraw export

## Stage 2: Model Loading

Primary components:

- `legomodel/src/main/java/com/lego/mesh/ModelLoader.java`
- `legomodel/src/main/java/com/lego/mesh/ObjModelLoader.java`
- `legomodel/src/main/java/com/lego/mesh/GlbLoader.java`
- `legomodel/src/main/java/com/lego/mesh/LoadedModel.java`

### OBJ Path

`ObjModelLoader` returns:

- geometry only
- no color map
- no textured triangle data

### GLB Path

`GlbLoader` returns:

- geometry
- optional per-triangle color map
- optional textured triangle data for higher-quality color sampling

This is an important architectural choice:

- color is a side channel
- `Mesh` and `Triangle` stay geometry-only
- color is carried in `LoadedModel`

## Stage 3: Mesh Normalization

Primary component:

- `legomodel/src/main/java/com/lego/mesh/MeshNormalizer.java`

Responsibilities:

- compute the bounding box
- translate the mesh so its minimum corner becomes the origin
- uniformly scale the mesh so the largest dimension fits the requested resolution

Result:

- the mesh is transformed into voxel-space-friendly coordinates
- the largest axis spans roughly `[0, resolution]`

This stage ensures downstream voxelization works in a consistent coordinate system regardless of the original model scale.

## Stage 4: Voxelization

Primary components:

- `legomodel/src/main/java/com/lego/voxel/Voxelizer.java`
- `legomodel/src/main/java/com/lego/voxel/LegacyVoxelizer.java`
- `legomodel/src/main/java/com/lego/voxel/TopologicalVoxelizer.java`
- `legomodel/src/main/java/com/lego/voxel/VoxelizationStrategy.java`

The CLI chooses one of two paths:

### Legacy Voxelizer

Characteristics:

- older compatibility path
- produces a solid voxel grid
- typically followed by explicit surface extraction

### Topological Voxelizer

Characteristics:

- newer surface-focused path
- directly produces a surface-oriented voxel grid
- used as the default CLI mode

Result:

- `VoxelGrid solid`

In topological mode, this grid is already surface-like.
In legacy mode, it still contains solid interior volume.

## Stage 5: Surface Extraction

Primary component:

- `legomodel/src/main/java/com/lego/voxel/SurfaceExtractor.java`

Purpose:

- convert a solid voxel grid into a shell by removing interior voxels

Behavior:

- a voxel remains if at least one of its 6 axis-aligned neighbors is empty or out of bounds

Result:

- `VoxelGrid surface`

The rest of the pipeline operates on this surface grid for brick placement.

## Stage 6: Placement Policy Resolution

Primary components:

- `legomodel/src/main/java/com/lego/optimize/PlacementPolicy.java`
- `legomodel/src/main/java/com/lego/optimize/GreedyAreaPolicy.java`
- `legomodel/src/main/java/com/lego/optimize/ScoringPlacementPolicy.java`

The system supports multiple placement policies.

### Greedy Policy

Characteristics:

- favors larger area quickly
- simpler behavior
- less quality-oriented at boundaries

### Scoring Policy

Characteristics:

- default quality-first policy
- considers candidate fit
- explores rotated footprints
- considers neighbor coverage
- can optionally use voxel color variance to preserve detail in color-sensitive regions

When GLB color mode is enabled and the scoring policy is active, the CLI can precompute a voxel color grid and rebuild the policy in color-aware mode.

## Stage 7: Allowed Brick Catalog Loading

Primary component:

- `legomodel/src/main/java/com/lego/optimize/AllowedBrickDimensions.java`

Purpose:

- load the curated catalog of allowed brick dimensions and part IDs

This catalog constrains what the placer is allowed to use. The placer does not invent arbitrary brick sizes.

Result:

- ordered `BrickSpec` list used by the placement policy

## Stage 8: Brick Placement

Primary component:

- `legomodel/src/main/java/com/lego/optimize/BrickPlacer.java`

Responsibilities:

- scan the surface grid deterministically
- ask the active placement policy which brick to place at each uncovered filled voxel
- mark the brick footprint as covered
- repeat until all covered voxels are assigned

Deterministic scan order:

- `y` ascending
- then `z`
- then `x`

Result:

- `List<Brick> bricks`

At this point, the geometry pipeline is complete. If color is disabled, the project can export directly from here.

## Stage 9: Optional Color Pipeline

This stage only matters when:

- input carries color data
- color mode is enabled
- export mode is `ldraw`

Primary components:

- `legomodel/src/main/java/com/lego/color/ColorSampler.java`
- `legomodel/src/main/java/com/lego/color/ColorStrategyRegistry.java`
- `legomodel/src/main/java/com/lego/color/DirectMatchStrategy.java`
- `legomodel/src/main/java/com/lego/color/UVLabPaletteProjection.java`
- `legomodel/src/main/java/com/lego/color/DominantVoteStrategy.java`
- `legomodel/src/main/java/com/lego/color/SupersampledVoxelColorPipeline.java`
- `legomodel/src/main/java/com/lego/color/LegoPaletteMapper.java`
- `legomodel/src/main/java/com/lego/color/ColorSmoother.java`

### 9A. Source Color Acquisition

Depending on the chosen strategy, the system may use:

- per-triangle colors from the loader
- per-voxel dominant colors
- direct texture sampling at many points through the supersampled pipeline

### 9B. Brick Color Determination

Possible approaches:

- direct averaged brick RGB mapping
- LAB-based shadow-aware correction
- per-voxel voting
- per-sample supersampled voting

### 9C. Palette Mapping

The sampled or corrected color is mapped to a valid opaque LEGO/LDraw color code using `LegoPaletteMapper`.

### 9D. Spatial Cleanup

`ColorSmoother` can remove isolated outliers and rare wrong-hue clusters after initial color assignment.

### 9E. Fallback Fill

If some bricks have no resolved color and a fallback is configured, the CLI fills them with the fallback LDraw color code.

## Stage 10: Export

Primary components:

- `legomodel/src/main/java/com/lego/export/BrickObjExporter.java`
- `legomodel/src/main/java/com/lego/export/VoxelObjExporter.java`
- `legomodel/src/main/java/com/lego/export/LDrawExporter.java`

The export path depends on `exportMode`.

### `brick`

Exports:

- visual OBJ made from placed bricks

Use case:

- inspect merged brick geometry as a mesh

### `voxel-surface`

Exports:

- OBJ representation of the surface voxel shell

Use case:

- inspect voxelization quality after hollowing

### `voxel-solid`

Exports:

- OBJ representation of the full solid voxel volume

Use case:

- inspect raw voxel fill before shell extraction

### `ldraw`

Exports:

- assembly-style `.ldr` model using catalog part IDs and brick transforms
- optional per-brick LDraw colors

Use case:

- import into LDraw-compatible tools such as BrickLink Studio

This is the only export path that uses the LEGO part/color pipeline in full.

## Stage 11: Optional Stepping Analysis

Primary component:

- `legomodel/src/main/java/com/lego/voxel/VoxelSteppingAnalyzer.java`

Purpose:

- evaluate voxel stepping artifacts
- write metrics and layer-by-layer analysis
- optionally sweep multiple resolutions

Outputs include:

- JSON metrics
- CSV layer summaries
- sweep summaries when multiple resolutions are requested

This analysis path is orthogonal to normal export. It is a diagnostic branch, not a required production stage.

## Data Objects That Flow Through The Pipeline

### Geometry Path

```text
Path
  -> LoadedModel
  -> Mesh
  -> normalized Mesh
  -> VoxelGrid solid
  -> VoxelGrid surface
  -> List<Brick>
  -> exported file
```

### Color Path

```text
LoadedModel
  -> colorMap / texturedTriangles
  -> voxel or sample colors
  -> brick color codes
  -> optional smoothing / fallback
  -> LDraw export
```

## Pipeline Branches By Input Type

### OBJ Input

End-to-end path:

```text
OBJ
  -> ObjModelLoader
  -> MeshNormalizer
  -> Voxelizer
  -> SurfaceExtractor if needed
  -> BrickPlacer
  -> OBJ or LDraw export
```

Characteristics:

- geometry only
- no color side channel
- LDraw export uses default or fallback coloring behavior

### GLB Input Without Color Export

End-to-end path:

```text
GLB
  -> GlbLoader
  -> MeshNormalizer
  -> Voxelizer
  -> SurfaceExtractor if needed
  -> BrickPlacer
  -> geometry export
```

Characteristics:

- color data may be loaded
- color branch is ignored unless requested

### GLB Input With Color Export

End-to-end path:

```text
GLB
  -> GlbLoader
  -> MeshNormalizer
  -> Voxelizer
  -> SurfaceExtractor if needed
  -> BrickPlacer
  -> color strategy
  -> palette mapping
  -> smoothing / fallback
  -> LDraw export
```

Characteristics:

- full project pipeline
- most complete path in the system

## Architectural Summary

The project is organized around a stable geometry backbone with optional color attached as a side channel.

That design yields three important properties:

1. geometry processing is independent of color handling
2. OBJ compatibility is preserved cleanly
3. color-specific complexity is isolated to the GLB and LDraw path

In practical terms, the project pipeline is:

```text
load
  -> normalize
  -> voxelize
  -> extract surface
  -> place bricks
  -> optionally colorize
  -> export
  -> optionally analyze
```

## Related Documents

- `docs/CONTEXT.md`
- `docs/SHADOW_LIGHTING_PIPELINE.md`
