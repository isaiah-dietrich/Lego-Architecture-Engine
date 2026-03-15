# Modularity Improvement Plan

## Abstract

The LEGO Architecture Engine already has a usable package structure, but its modularity is limited by a few classes that concentrate too many responsibilities and by several places where infrastructure concerns leak into core workflow code.

The main architectural issue is not the absence of packages. The main issue is that orchestration, filesystem access, strategy dispatch, and optional feature handling are still spread across runtime-critical classes in ways that increase coupling. The result is that adding or changing behavior often requires editing central files rather than extending isolated components.

The most important improvement is to introduce clearer boundaries between:

- application orchestration
- core geometry and placement logic
- color-specific processing
- infrastructure concerns such as CSV loading, file export, and CLI I/O

In practical terms, the project should move toward an architecture where:

- `Main` becomes a thin composition root
- pipeline orchestration lives in an application layer
- catalog and palette access are hidden behind repository-style interfaces
- colorization is encapsulated behind a single application-facing service
- large mixed-responsibility classes such as the GLB loader and stepping analyzer are split into focused collaborators

This will make the codebase easier to test, easier to extend, and less fragile when new loaders, exporters, diagnostics, or color strategies are introduced.

## Goals

- reduce coupling between packages
- keep geometry and placement logic as pure as possible
- isolate optional features behind explicit interfaces
- remove direct filesystem access from core workflow classes
- make pipeline stages easier to test independently
- prepare the codebase for future growth without premature multi-module complexity

## Task List

## Phase 1: Thin CLI And Application Layer

- Extract CLI parsing from `Main` into `CliOptionsParser`.
- Introduce `PipelineRequest` to hold validated execution inputs.
- Introduce `PipelineResult` to hold execution outputs and summary metadata.
- Introduce `PipelineRunner` to orchestrate the end-to-end workflow.
- Move output summary formatting out of `Main` into a reporter/helper.
- Move export branching out of `Main` into an export coordinator.
- Move stepping-analysis branching out of `Main` into an analysis coordinator.

## Phase 2: Data Access Boundaries

- Introduce `CatalogPartRepository` interface.
- Introduce `PaletteRepository` interface.
- Implement CSV-backed repository adapters for current catalog and palette files.
- Stop loading catalog parts directly inside `AllowedBrickDimensions`.
- Stop loading catalog parts directly inside `LDrawExporter`.
- Stop loading palette data directly inside CLI color execution branches.
- Centralize base-directory/path resolution in one place instead of threading it through unrelated classes.

## Phase 3: Encapsulate Colorization

- Introduce `ColorizationInput` as the full input model for color assignment.
- Introduce `BrickColorizer` or `ColorizationService` as the top-level colorization interface.
- Move fallback-color filling into the colorization layer.
- Move smoothing decisions into the colorization layer.
- Remove `instanceof`-based color strategy dispatch from `Main`.
- Adapt existing strategies (`direct`, `uvlab`, `dominant`, `supersampled`) to the new interface.
- Keep placement color-awareness, but provide it through a neutral feature artifact rather than direct color-space computation inside `optimize`.

## Phase 4: Split Large Mixed-Responsibility Classes

- Split `GlbLoader` into smaller collaborators for:
- scene traversal
- geometry extraction
- material extraction
- texture decoding
- texture sampling
- triangle color resolution
- Keep `GlbLoader` as a façade over those collaborators.
- Split `VoxelSteppingAnalyzer` into:
- pure analysis computation
- resolution sweep runner
- JSON writer
- CSV writer

## Phase 5: Improve Placement Boundary

- Introduce a neutral placement feature type such as `DetailMap`, `VarianceMap`, or `PlacementFeatureGrid`.
- Compute color/detail sensitivity outside `ScoringPlacementPolicy`.
- Refactor `ScoringPlacementPolicy` to consume feature data instead of performing color-space analysis directly.
- Keep high-detail-region behavior, but move the color interpretation logic out of `optimize`.

## Phase 6: Remove Export Duplication

- Introduce shared OBJ-writing helpers such as `ObjWriter` or `CuboidMeshAppender`.
- Refactor `BrickObjExporter` to use shared OBJ-writing primitives.
- Refactor `VoxelObjExporter` to use shared OBJ-writing primitives.
- Keep export-specific mapping logic in exporter classes, but remove duplicated vertex/face serialization code.

## Phase 7: Clarify Catalog Responsibilities

- Introduce a catalog service layer for part metadata and mapping concerns.
- Split “catalog loading” from “brick spec derivation”.
- Split “catalog loading” from “LDraw part placement resolution”.
- Make `AllowedBrickDimensions` a pure transformer from catalog parts to brick specs.
- Make LDraw part/rotation resolution depend on injected catalog data instead of direct file loading.

## Phase 8: Class-Level Cleanup

- Review static utility classes and convert only the stateful/configurable ones into services.
- Keep purely functional geometry helpers static where appropriate.
- Add small DTOs for stage inputs and outputs where workflows currently pass many loosely related arguments.
- Reduce repeated path and option validation code by centralizing validation models.

## Phase 9: Testing Improvements

- Add focused tests for the future application layer without CLI wiring.
- Add repository tests around catalog/palette adapters.
- Add component tests for GLB loading collaborators after the loader split.
- Add tests for export coordinators separate from file-format writers.
- Add tests for placement using precomputed feature maps.

## Phase 10: Reevaluate Module Split Later

- Do not split into Maven submodules yet.
- First complete the internal boundary refactors above.
- Reevaluate whether separate modules are justified after the application and infrastructure seams are real.
- If needed later, consider modules such as:
- `engine-core`
- `engine-color`
- `engine-io`
- `engine-cli`
- `engine-diagnostics`

## Recommended Execution Order

1. Thin `Main` and introduce `PipelineRunner`.
2. Add repository interfaces for catalog and palette access.
3. Encapsulate colorization behind a single service.
4. Split `GlbLoader`.
5. Split `VoxelSteppingAnalyzer`.
6. Refactor `ScoringPlacementPolicy` to consume precomputed placement features.
7. Remove shared export duplication.
8. Reevaluate module boundaries.

## Success Criteria

- `Main` is small and mostly wiring.
- Core workflow classes no longer load files directly.
- New color strategies can be added without editing CLI orchestration.
- Placement can remain color-aware without importing color-analysis logic into `optimize`.
- Diagnostics and export formatting are reusable without pulling in full pipeline execution.
- The codebase becomes easier to extend by adding components rather than editing central switch points.

## Related Documents

- `docs/PROJECT_PIPELINE.md`
- `docs/MODULARITY_ANALYSIS.md`
- `docs/SHADOW_LIGHTING_PIPELINE.md`
