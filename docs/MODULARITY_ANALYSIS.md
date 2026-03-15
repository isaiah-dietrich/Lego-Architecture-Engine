# Modularity Analysis

## Scope

This document analyzes the current architecture of the LEGO Architecture Engine and identifies where modularity can be improved without losing the existing deterministic pipeline.

The goal is not to split everything into many Maven modules immediately. The goal is to make the codebase easier to change by:

- reducing orchestration coupling
- separating domain logic from infrastructure
- isolating optional features
- making the pipeline easier to test in slices

## Current Architecture

The project already has a reasonable package-level shape:

- `cli`
- `mesh`
- `voxel`
- `optimize`
- `color`
- `export`
- `data`
- `diag`
- `model`

That is a solid starting point. The main issue is not package naming. The main issue is that runtime responsibilities still cross those boundaries in a few large classes.

## Strengths

### Clear Geometry Backbone

The geometry pipeline is conceptually clean:

```text
load -> normalize -> voxelize -> extract surface -> place bricks -> export
```

That flow is easy to understand and already documented in [PROJECT_PIPELINE.md](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/docs/PROJECT_PIPELINE.md).

### Side-Channel Color Model

`LoadedModel` keeps color separate from core mesh data:

- [LoadedModel.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/mesh/LoadedModel.java)

This is the right direction. Geometry and optional color data are not fused into `Mesh` or `Triangle`.

### Deterministic Core

Placement, voxelization, and export are largely deterministic. That makes future modularization safer because behavior is easier to lock down with tests.

## Main Modularity Problems

## 1. `Main` Is An Overloaded Application God Object

`Main` is currently the largest architectural hotspot.

Evidence:

- [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java) is 617 lines
- it imports nearly every major package
- it parses arguments, validates options, composes dependencies, runs the full pipeline, selects strategy-specific execution branches, writes output summaries, and coordinates diagnostics

Concrete examples:

- loader selection: [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L421)
- core geometry orchestration: [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L188)
- color branch orchestration: [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L255)
- stepping analysis orchestration: [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L321)
- CLI parsing and validation mixed into the same file: [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L445)

Why this hurts modularity:

- all feature branches terminate in one file
- adding a new loader, export target, diagnostic, or color path requires editing `Main`
- test seams are coarse because composition and execution are intertwined

### Recommended Refactor

Introduce an application layer.

Suggested classes:

- `PipelineRequest`
- `PipelineResult`
- `PipelineRunner`
- `ColorizationService`
- `ExportCoordinator`
- `AnalysisCoordinator`
- `CliOptionsParser`

Target shape:

```text
Main
  -> CliOptionsParser
  -> PipelineRunner
  -> ExitCodeMapper / ConsoleReporter
```

This keeps `Main` as a composition root only.

## 2. Domain Logic Still Pulls Infrastructure Directly

Several classes inside core workflow areas reach directly into filesystem-backed data loading.

Examples:

- [AllowedBrickDimensions.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/optimize/AllowedBrickDimensions.java#L128) loads the curated catalog itself
- [LDrawExporter.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/export/LDrawExporter.java#L148) loads catalog parts itself
- [LegoPaletteMapper.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/color/LegoPaletteMapper.java#L61) resolves the palette from a hardcoded project-relative path
- [CuratedCatalogLoader.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/data/CuratedCatalogLoader.java#L30) uses current working directory resolution as a default path rule

Why this hurts modularity:

- business logic depends on file layout
- the same data source is loaded in multiple places
- it is harder to replace the catalog, cache it, or use an in-memory implementation in tests
- configuration concerns leak across packages

### Recommended Refactor

Introduce repository-style interfaces at the application boundary.

Suggested interfaces:

- `CatalogPartRepository`
- `BrickSpecRepository`
- `PaletteRepository`

Suggested implementations:

- `CsvCatalogPartRepository`
- `CsvPaletteRepository`

Then convert:

- `AllowedBrickDimensions` into a pure transformer from `List<CatalogPart>` to `List<BrickSpec>`
- `LDrawExporter` into a pure exporter that receives part metadata instead of loading it
- `LegoPaletteMapper` into a mapper created from already-loaded palette entries

This change will remove filesystem decisions from core flow classes.

## 3. `GlbLoader` Contains Too Many Responsibilities

`GlbLoader` is 528 lines and currently handles:

- file format validation
- glTF reader lifecycle
- scene graph traversal
- transform application
- primitive assembly
- vertex color reading
- UV reading
- material extraction
- texture image decoding
- texture sampling
- padding heuristics
- triangle color resolution

Reference:

- [GlbLoader.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/mesh/GlbLoader.java)

Why this hurts modularity:

- geometry parsing and color extraction are tightly coupled
- changes to color logic require touching the loader
- loader tests must cover too many concerns at once
- the class is difficult to reason about because it mixes graph traversal, image decoding, and color policy

### Recommended Refactor

Split `GlbLoader` into collaborating components.

Suggested internal decomposition:

- `GlbSceneReader`
- `GlbGeometryExtractor`
- `GlbMaterialExtractor`
- `GlbTextureDecoder`
- `GlbTextureSampler`
- `GlbTriangleColorResolver`

Keep `GlbLoader` as a façade:

```text
GlbLoader
  -> GlbSceneReader
  -> GlbGeometryExtractor
  -> GlbTriangleColorResolver
  -> LoadedModelAssembler
```

This keeps the public API stable while making the implementation modular.

## 4. Color Strategy Selection Is Not Properly Encapsulated

The project has a `ColorStrategy` abstraction, which is good, but `Main` still knows strategy-specific execution details.

Evidence:

- [Main.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/cli/Main.java#L266)

`Main` branches on concrete types:

- `SupersampledVoxelColorPipeline`
- `DominantVoteStrategy`
- generic `ColorStrategy`

Why this hurts modularity:

- strategy dispatch is partly in the registry and partly in `Main`
- adding a new strategy may require editing `Main`
- strategy-specific input requirements are not represented in the interface

### Recommended Refactor

Introduce a higher-level colorization interface around pipeline context.

Suggested shape:

```java
interface BrickColorizer {
    Map<Brick, Integer> colorize(ColorizationInput input);
}
```

Where `ColorizationInput` includes:

- original mesh
- normalized mesh
- surface grid
- placed bricks
- loaded model color payloads
- palette
- requested algorithm

Then each strategy becomes a `BrickColorizer`, not just a transformation of `Map<Brick, ColorRgb>`.

That removes the concrete `instanceof` logic from `Main`.

## 5. `optimize` Depends On `color`

`ScoringPlacementPolicy` currently imports:

- [ScoringPlacementPolicy.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/optimize/ScoringPlacementPolicy.java#L5)

Specifically:

- `LegoPaletteMapper`
- `ColorRgb`

The optimization package therefore knows about color-space math and palette-oriented concerns.

Why this hurts modularity:

- brick placement stops being purely a placement concern
- color-aware behavior cannot be switched independently of optimize internals
- package boundaries become misleading

### Recommended Refactor

Keep `optimize` focused on placement mechanics.

Extract the color-sensitive scoring input into a neutral abstraction:

- `VoxelFeatureGrid`
- `PlacementHeuristics`
- `RegionVarianceMap`

Then have the color pipeline produce a variance or uniformity map outside `optimize`, and pass that in as data.

In other words:

- `optimize` should consume precomputed placement features
- `color` should compute those features if needed

That preserves feature richness without entangling the packages.

## 6. `VoxelSteppingAnalyzer` Mixes Analysis Computation With Artifact Writing

`VoxelSteppingAnalyzer` is 642 lines and handles:

- metric computation
- resolution sweep orchestration
- JSON generation
- CSV generation
- file writing

Reference:

- [VoxelSteppingAnalyzer.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/voxel/VoxelSteppingAnalyzer.java)

Why this hurts modularity:

- analysis logic is tied to persistence format
- diagnostics are harder to reuse programmatically
- file formats and metric computation evolve together unnecessarily

### Recommended Refactor

Split into:

- `VoxelSteppingAnalyzer` for pure computation
- `VoxelSteppingSweepRunner` for multi-resolution orchestration
- `VoxelSteppingJsonWriter`
- `VoxelSteppingCsvWriter`

That creates a clean boundary between analysis and reporting.

## 7. Exporters Duplicate Mesh-Serialization Logic

`BrickObjExporter` and `VoxelObjExporter` both manually write:

- cuboid vertices
- 12 cuboid triangles
- OBJ text assembly

References:

- [BrickObjExporter.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/export/BrickObjExporter.java)
- [VoxelObjExporter.java](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/src/main/java/com/lego/export/VoxelObjExporter.java)

Why this hurts modularity:

- shared geometry-writing behavior is duplicated
- bug fixes must be duplicated
- exporter package has no reusable writer primitives

### Recommended Refactor

Introduce:

- `ObjWriter`
- `CuboidMeshAppender`
- `ObjSceneBuilder`

Then:

- `BrickObjExporter` maps `Brick -> cuboid`
- `VoxelObjExporter` maps filled voxel -> cuboid
- the shared OBJ writing logic lives once

## 8. Catalog Concepts Are Spread Across Too Many Places

Catalog-related concerns currently appear in:

- `data` for CSV parsing
- `optimize` for brick spec extraction
- `export` for part lookup and rotation resolution
- `cli` for test-only path threading

Why this hurts modularity:

- there is no single catalog boundary
- part metadata, placement dimensions, and export mapping are split across unrelated packages
- test-time path injection leaks into unrelated APIs

### Recommended Refactor

Create a dedicated catalog service layer.

Suggested abstractions:

- `CatalogService`
- `PartLookup`
- `BrickSpecFactory`
- `PartPlacementResolver`

This would centralize:

- active part loading
- brick spec generation
- export lookup and part mapping

Then `optimize` and `export` can depend on interfaces or DTOs instead of raw CSV loading logic.

## 9. Static Utility Style Limits Composition

Many classes are static utility classes:

- `MeshNormalizer`
- `Voxelizer`
- `SurfaceExtractor`
- `ColorSampler`
- `BrickPlacer`
- `LDrawExporter`
- `VoxelSteppingAnalyzer`
- catalog loaders

Static methods are fine for pure transforms, but they become limiting when the class wants configuration, caching, instrumentation, alternate implementations, or injected collaborators.

Why this hurts modularity:

- composition happens by global static calls instead of explicit objects
- testing alternative implementations is awkward
- swapping behavior usually means editing call sites rather than substituting dependencies

### Recommended Refactor

Be selective.

Keep static utilities only where the class is truly pure and configuration-free.

Convert to instance services when the class needs:

- repository access
- strategy composition
- filesystem access
- format writing
- optional behavior
- caching

Good candidates for staying static:

- `MeshNormalizer`
- simple geometric helpers

Good candidates for becoming services:

- colorization coordinator
- export coordinator
- catalog access
- stepping analysis writers

## 10. The Build Is Still A Single Deployment Unit

Right now the project is one Maven module:

- [pom.xml](/Users/isaiahdietrich/Desktop/Projects/Lego-Architecture-Engine/legomodel/pom.xml)

That is not inherently bad. The problem would be splitting too early.

### Recommended Approach

Do not start with a multi-module Maven split.

Instead:

1. create clean internal interfaces first
2. remove direct filesystem coupling from core logic
3. split orchestration from domain logic
4. isolate diagnostics and adapters
5. only then consider separate modules

Likely future module candidates:

- `engine-core`
- `engine-color`
- `engine-io`
- `engine-cli`
- `engine-diagnostics`

But doing that before internal boundaries are real would create ceremony without actual modularity.

## Recommended Target Architecture

## Layering

### Domain / Core

Owns:

- geometry model
- voxel model
- brick model
- placement contracts
- pure transforms

Packages that should trend toward this layer:

- `model`
- parts of `mesh`
- parts of `voxel`
- parts of `optimize`

### Application

Owns:

- pipeline orchestration
- request/result models
- use-case coordination
- feature branching

Suggested package:

- `app`

Suggested classes:

- `PipelineRunner`
- `GeometryPipeline`
- `ColorPipeline`
- `ExportPipeline`
- `AnalysisPipeline`

### Infrastructure / Adapters

Owns:

- CSV loading
- GLB reading
- file writing
- CLI I/O
- LDraw output
- OBJ output

Packages that should trend toward this layer:

- `cli`
- `data`
- `export`
- GLB-specific reading parts of `mesh`
- diagnostics writers

## Suggested Refactor Roadmap

## Phase 1: Untangle Orchestration

Highest leverage, lowest conceptual risk.

1. Extract `CliOptionsParser` from `Main`
2. Introduce `PipelineRequest`
3. Introduce `PipelineRunner`
4. Move export branching out of `Main`
5. Move stepping-analysis branching out of `Main`

Expected outcome:

- `Main` becomes small
- application flow becomes testable without console wiring

## Phase 2: Remove Filesystem Access From Core Workflow Classes

1. Introduce `CatalogPartRepository` and `PaletteRepository`
2. Stop loading catalog data inside `AllowedBrickDimensions`
3. Stop loading catalog data inside `LDrawExporter`
4. Stop loading palette data inside color execution branches

Expected outcome:

- easier testing
- easier caching
- cleaner boundaries between domain and infrastructure

## Phase 3: Encapsulate Colorization

1. Introduce `ColorizationInput`
2. Introduce `BrickColorizer`
3. Remove `instanceof` branching from `Main`
4. Move fallback and smoothing into a single colorization coordinator

Expected outcome:

- color path becomes open for extension
- new strategies stop requiring changes in the CLI entry point

## Phase 4: Split Large Hotspot Classes

1. split `GlbLoader`
2. split `VoxelSteppingAnalyzer`
3. extract shared OBJ writing components

Expected outcome:

- smaller implementation units
- more focused tests
- easier long-term maintenance

## Phase 5: Reassess Package And Module Boundaries

Only after the previous phases.

At that point you can evaluate whether Maven multi-module packaging adds real value.

## Highest-Value Improvements

If the goal is to make the project "a lot more modular" with the least wasted effort, the best order is:

1. shrink `Main` into a real composition root
2. introduce repository interfaces for catalog and palette data
3. encapsulate the entire color pipeline behind a single application-facing interface
4. split `GlbLoader` into geometry, texture, and color collaborators
5. separate analysis computation from analysis artifact writing

## Bottom Line

The project already has decent package organization, but not yet strong modular boundaries.

The biggest issue is not that packages are missing. The biggest issue is that orchestration, filesystem access, and feature-specific branching still leak across the system.

The most effective modularity move is:

- create a proper application layer
- treat catalog/palette/filesystem concerns as infrastructure
- keep geometry, voxelization, and placement logic pure where possible
- encapsulate optional color and diagnostic flows behind interfaces

That will make the codebase easier to extend than a premature Maven multi-module split would.
