# Project Pipeline

## Purpose

This document describes the end-to-end pipeline for the LEGO Architecture Engine.

It covers the full flow from model ingestion through export, including the optional color path, slope handling, and the HTTP API layer added in Phase 1.

## Entry Points

The pipeline can be driven from two entry points:

- **CLI:** `com.lego.cli.Main` — parses arguments, validates, delegates to `PipelineRunner.run()`
- **API:** `com.lego.api.ApiServer` — HTTP server; receives multipart uploads and delegates to `PipelineRunner.runForApi()`

Both entry points converge on `PipelineRunner`, which contains the canonical pipeline logic.

### PipelineRunner Methods

| Method | Purpose |
|---|---|
| `run()` | CLI path — catches all exceptions, prints summary, returns int exit code |
| `runForApi()` | API path — throws on failure, returns `PipelineResult` directly |
| `runCore()` (private) | Shared pipeline body — load → voxelize → place → colorize |

---

## High-Level Flow

```
input file + config
  -> load model (OBJ or GLB)
  -> normalize mesh into voxel space
  -> voxelize geometry
  -> extract surface shell
  -> [optional] build color feature grid
  -> load allowed brick catalog
  -> place bricks (MaskPlacementPolicy)
  -> [optional] colorize bricks (LDraw + glb-color only)
  -> export
  -> [optional] stepping analysis or benchmark
```

---

## Stage 1: Entry and Option Parsing

**CLI components:**
- `com.lego.cli.Main`
- `com.lego.cli.CliOptionsParser`
- `com.lego.cli.ParsedOptions`
- `com.lego.cli.PipelineRequest`

**API components:**
- `com.lego.api.ApiServer` (multipart form parsing)
- `com.lego.api.JobState`

Responsibilities:
- parse and validate resolution (must be >= 2)
- infer input format from file extension
- validate export mode and voxelizer mode
- validate color mode and color algorithm
- build a `PipelineRequest` record

`PipelineRequest` is an immutable record that carries all configuration into the pipeline. Neither `PipelineRunner` nor any downstream class reads from the CLI or HTTP context directly.

**Export modes accepted:** `brick`, `voxel-surface`, `voxel-solid`, `voxel-slope-surface`, `voxel-surface-combined`, `voxel-slope-placed`, `ldraw`

**Voxelizer modes:** `topological` (default), `legacy`

**Color modes:** `glb-color` (default for GLB), `none`

**Color algorithms:** `direct`, `uvlab`, `dominant`, `region`, `supersampled`

---

## Stage 2: Model Loading

**Components:**
- `com.lego.mesh.ModelLoader` (interface)
- `com.lego.mesh.ObjModelLoader`
- `com.lego.mesh.GlbLoader`
- `com.lego.mesh.LoadedModel`

### OBJ Path

Returns geometry only. No color map. No texture data.

### GLB Path

Returns geometry plus an optional color side channel:
- per-triangle color map
- textured triangle data (for higher-quality supersampled color)

**Architectural note:** `Mesh` and `Triangle` stay geometry-only. Color is carried separately in `LoadedModel` and is never embedded in geometry objects. This keeps the geometry pipeline clean regardless of whether color is requested.

---

## Stage 3: Mesh Normalization

**Component:** `com.lego.mesh.MeshNormalizer`

- Computes the mesh bounding box
- Translates the minimum corner to the origin
- Uniformly scales so the largest axis spans `[0, resolution]`

Result: a mesh in voxel-space coordinates, consistent regardless of original model scale.

---

## Stage 4: Voxelization

**Components:**
- `com.lego.voxel.Voxelizer` (dispatcher)
- `com.lego.voxel.TopologicalVoxelizer` (default)
- `com.lego.voxel.LegacyVoxelizer` (optional build profile)
- `com.lego.voxel.VoxelizationStrategy`

### Topological (default)

Directly produces a surface-oriented voxel grid. The result is already shell-like — no separate surface extraction step is needed. Used as `VoxelGrid surface` directly.

### Legacy

Produces a solid voxel volume. Requires an explicit surface extraction step (Stage 5) to obtain the shell. Available only when built with `-Plegacy` or `-Pfull`.

Result: `VoxelGrid solid`

---

## Stage 5: Surface Extraction

**Component:** `com.lego.voxel.SurfaceExtractor`

Only runs for the legacy voxelizer path. For topological mode, the solid grid is used directly as the surface.

- A voxel is kept if at least one of its 6 axis-aligned neighbors is empty or out of bounds
- Interior voxels with no exposed face are removed

Result: `VoxelGrid surface` — the shell on which all downstream stages operate.

---

## Stage 6: Color Feature Grid (Optional)

**Components:**
- `com.lego.color.ColorSampler`
- `com.lego.color.ColorFeatureGridFactory`
- `com.lego.optimize.PlacementFeatureGrid`

Runs when `colorMode == glb-color` and the loaded model carries a color map.

Samples dominant voxel colors from the mesh color data, then constructs a `PlacementFeatureGrid`. This grid is passed into `MaskPlacementPolicy` so that brick placement can factor in color variance when choosing between candidates — preferring placements that preserve sharp color boundaries.

If color is disabled or the model has no color map, `featureGrid` is `null` and placement proceeds geometry-only.

---

## Stage 7: Brick Placement

**Components:**
- `com.lego.optimize.AllowedBrickDimensions` — loads catalog specs
- `com.lego.optimize.BrickPlacer` — placement loop
- `com.lego.optimize.MaskPlacementPolicy` — active placement policy
- `com.lego.optimize.GeometryPartMaskProvider` — geometry masks for slope placement
- `com.lego.optimize.PartMask` — per-part voxel offset masks

### Catalog Loading

`AllowedBrickDimensions.loadFromRepository()` reads the curated CSV catalog and produces an ordered `List<BrickSpec>`, sorted by placement priority:

1. Area descending
2. Height descending
3. Width descending
4. Depth descending

Only active parts are included. The 1×2 brick is normalized to 2×1 (horizontal-only).

### MaskPlacementPolicy

The active production policy. Objective ordering (lexicographic):

1. **Feasibility** — hard constraints: occupancy, blocking, target shell membership
2. **Coverage** — maximize new voxels covered per placement (fewer total pieces)
3. **Color error** — minimize color variance across the brick footprint (when feature grid is available)
4. **Solidity** — prefer placements over more solid voxels; spatial tiebreakers for determinism

Slope parts are placed using geometry masks and surface normal estimation. A voxel is slope-eligible if the local surface normal has sufficient inclination (>20°). `Facing` encodes slope orientation: `NONE`, `FRONT`, `BACK`, `LEFT`, `RIGHT`.

`GeometryPartMaskProvider` provides LDraw-geometry-derived masks for precise slope collision checking. Masks are cached on disk when `geometryMaskCacheDir` is set.

### Placement Loop

`BrickPlacer` scans uncovered surface voxels in deterministic order (y → z → x) and asks the policy which brick to place at each position. The footprint is marked covered and the scan continues until all filled voxels are assigned.

Result: `List<Brick> bricks`

**Other policies (not used in the main pipeline):**
- `GreedyAreaPolicy` — simpler greedy fallback
- `ScoringPlacementPolicy` — scoring-based candidate selection
- `CpsatMaskPlacementPolicy` — constraint-programming variant (tooling profile)

---

## Stage 8: Colorization (Optional)

Only runs when: `exportMode == ldraw` AND `colorMode == glb-color` AND the model carries a color map.

**Components:**
- `com.lego.color.BrickColorizer` — top-level service, dispatches strategy
- `com.lego.color.ColorStrategyRegistry` — maps algorithm names to strategy instances
- `com.lego.color.LegoPaletteMapper` — maps sampled RGB to nearest LDraw opaque color
- `com.lego.color.ColorSmoother` — spatial outlier removal

### Color Strategies

| Algorithm | Key technique |
|---|---|
| `direct` | Per-brick average RGB → nearest LDraw color by ΔE (CIEDE2000) |
| `uvlab` | Shadow lifting + chroma stabilization via `ShadowRemover`; best for baked-lit textures |
| `dominant` | Per-voxel palette voting; majority wins per brick |
| `region` | Flood-fill spatial regions; majority vote per region |
| `supersampled` | Multi-sample color averaging via `SupersampledVoxelColorPipeline` + `TriangleBVH` |

### Palette Mapping

`LegoPaletteMapper` maps sampled or corrected RGB into a valid opaque LDraw color code using CIEDE2000 perceptual distance. Only opaque palette entries are eligible. A fallback color code (default: 16) fills any brick that could not be resolved.

### Spatial Cleanup

`ColorSmoother` runs after initial color assignment and removes isolated color outliers — bricks that are unlikely to be correct given their immediate neighbors. This reduces visual noise in the exported model.

Result: `Map<Brick, Integer> brickColorCodes`

---

## Stage 9: Export

**Dispatcher:** `com.lego.cli.ExportCoordinator`

**Exporters:**
- `com.lego.export.BrickObjExporter`
- `com.lego.export.VoxelObjExporter`
- `com.lego.export.LDrawExporter`

### Export Modes

| Mode | Output | Use case |
|---|---|---|
| `brick` | OBJ of placed brick cuboids | Inspect merged brick geometry as a mesh |
| `voxel-surface` | OBJ of the surface voxel shell | Inspect hollowing quality |
| `voxel-solid` | OBJ of the full solid volume | Inspect raw voxel fill |
| `voxel-slope-surface` | OBJ of slope-eligible voxels only | Inspect slope detection |
| `voxel-surface-combined` | OBJ with surface + slope layers | Combined diagnostic view |
| `voxel-slope-placed` | OBJ of voxels under placed slope bricks | Inspect slope placement results |
| `ldraw` | `.ldr` assembly with catalog part IDs + optional colors | Import into BrickLink Studio |

`voxel-slope-surface`, `voxel-surface-combined`, and `voxel-slope-placed` are built using `SlopeSurfaceMask`, which extracts slope-eligible cells from the surface grid using the same normal-matching logic as placement.

`ldraw` is the only mode that uses the full color pipeline.

---

## Stage 10: Optional Diagnostics

Only available when built with `-Ptooling` or `-Pfull`.

### Stepping Analysis

**Component:** `com.lego.cli.AnalysisCoordinator`

Evaluates voxel stepping artifacts across layers. Produces JSON metrics, CSV summaries, and optional resolution sweeps.

### Policy Benchmark

**Component:** `com.lego.cli.PolicyBenchmarkRunner`

Runs multiple placement policies against the same surface grid and writes comparative runtime and quality metrics.

Both diagnostic branches are orthogonal to normal export and do not affect the output file.

---

## Data Flow Summary

### Geometry Path

```
Path (input file)
  -> LoadedModel
  -> Mesh (raw)
  -> Mesh (normalized)
  -> VoxelGrid solid
  -> VoxelGrid surface
  -> List<Brick>
  -> exported file
```

### Color Side Channel

```
LoadedModel.colorMap / texturedTriangles
  -> [optional] PlacementFeatureGrid   (feeds into placement)
  -> [colorize] BrickColorizer         (feeds into LDraw export)
      -> sampled RGB per brick
      -> LegoPaletteMapper -> LDraw color code
      -> ColorSmoother -> cleaned codes
  -> Map<Brick, Integer> brickColorCodes
  -> LDraw export
```

---

## Pipeline Branches By Input

### OBJ — geometry only

```
OBJ -> ObjModelLoader -> MeshNormalizer -> Voxelizer
  -> [SurfaceExtractor if legacy]
  -> BrickPlacer (geometry-only policy)
  -> OBJ or LDraw export (default color)
```

### GLB — geometry, no color export

```
GLB -> GlbLoader -> MeshNormalizer -> Voxelizer
  -> [SurfaceExtractor if legacy]
  -> BrickPlacer (geometry-only policy)
  -> OBJ or LDraw export
```

### GLB — full color export

```
GLB -> GlbLoader -> MeshNormalizer -> Voxelizer
  -> [SurfaceExtractor if legacy]
  -> ColorFeatureGridFactory  (color-aware placement)
  -> BrickPlacer (MaskPlacementPolicy with feature grid)
  -> BrickColorizer -> LegoPaletteMapper -> ColorSmoother
  -> LDraw export with per-brick color codes
```

---

## Architectural Properties

The pipeline is organized around a stable geometry backbone with color attached as an optional side channel.

1. **Geometry and color are decoupled.** `Mesh`, `Triangle`, and `VoxelGrid` carry no color. Color enters only through `LoadedModel` and exits only through `brickColorCodes`.

2. **OBJ compatibility is preserved.** The geometry path works identically for OBJ and GLB inputs. Color simply does not activate.

3. **Placement is policy-driven.** `BrickPlacer` knows nothing about policy internals. The `MaskPlacementPolicy` can operate in geometry-only or color-aware mode without changing the placement loop.

4. **The pipeline is entry-point agnostic.** `PipelineRunner.runCore()` does not know whether it was called from the CLI or the HTTP API. Both entry points build a `PipelineRequest` and delegate.

---

## Related Documents

- `docs/CONTEXT.md`
- `docs/FRONTEND_PLAN.md`
- `docs/SHADOW_LIGHTING_PIPELINE.md`
