# Module-Split Evaluation (Phase 10)

**Date:** 2026-03-15
**Status:** Evaluated — single module retained

## Context

Phases 1–9 of the Modularity Improvement Plan are complete. This evaluation assesses whether splitting the single `legomodel` Maven artifact into multiple submodules is justified.

## Current State

| Metric | Value |
|---|---|
| Source files | 72 |
| Test files | 36 |
| Tests passing | 400 |
| Packages | 9 (`cli`, `color`, `data`, `diag`, `export`, `mesh`, `model`, `optimize`, `voxel`) |

### Package responsibilities after Phases 1–9

- **model** — Immutable data records and shared utilities (`Brick`, `Triangle`, `Vector3`, `ColorRgb`, `ColorMath`, `VoxelKey`, `TriangleAabbTest`)
- **cli** — Composition root, CLI parsing, pipeline orchestration, coordinators, reporters
- **data** — Repository interfaces and CSV-backed adapters for catalog/palette access
- **mesh** — GLB/OBJ loading split into focused collaborators behind `GlbLoader` façade
- **voxel** — Voxelization strategies, surface extraction, stepping analysis (split into writer, runner, analyzer)
- **color** — Color strategies, palette mapping, feature grid factory, BrickColorizer service
- **optimize** — Brick placement policies consuming precomputed feature grids, no color imports
- **export** — LDraw/OBJ exporters using shared `ObjCuboidWriter`
- **diag** — Texture analysis diagnostics

### Boundary quality

- `optimize` has **zero** imports from `color` (feature grid factory lives in `color`; the result type `PlacementFeatureGrid` lives in `optimize`)
- `data` exposes repository interfaces; no core package imports CSV libraries directly
- `cli` is the only package that wires dependencies together
- `model` is a leaf package with no upstream imports
- No circular package dependencies

## Evaluation

The plan suggested these potential modules:

| Module | Maps to package(s) | File count |
|---|---|---|
| `engine-core` | `model`, `mesh`, `voxel`, `optimize` | 34 |
| `engine-color` | `color` | 13 |
| `engine-io` | `data`, `export` | 11 |
| `engine-cli` | `cli` | 10 |
| `engine-diagnostics` | `diag`, `voxel` (analysis subset) | 4 |

### Arguments for splitting

- Packages already have clean boundaries — migration would be straightforward
- Enforces compile-time isolation (e.g., `optimize` cannot accidentally import `color`)
- Enables independent versioning if components are reused

### Arguments against splitting now

- 72 source files across 9 packages is comfortably managed in a single module
- No external consumers require independent artifacts
- Multi-module Maven setup adds build complexity (parent POM, inter-module dependency declarations, IDE configuration)
- Package-level discipline is currently sufficient — no boundary violations exist
- The project has a single deployment artifact (fat JAR via shade plugin)

## Conclusion

**Do not split into Maven submodules at this time.**

The internal boundaries established in Phases 1–9 are clean and well-enforced by package structure. The codebase is not large enough to benefit from compile-time module isolation, and there are no external consumers that need separate artifacts. The package layout already supports the module boundaries listed above, so splitting remains straightforward if the codebase grows significantly or external reuse becomes a requirement.

### Trigger conditions for revisiting

- Source file count exceeds ~150
- A second deployment artifact is needed (e.g., separate CLI vs. library JAR)
- External projects need to depend on `engine-core` without pulling in CLI/color
- Build times exceed acceptable thresholds due to unnecessary recompilation
