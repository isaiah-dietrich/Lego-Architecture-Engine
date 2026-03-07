# GLB + Color Implementation Roadmap

Date: 2026-03-07
Status: Draft
Owner: LEGO Architecture Engine

## Objective
Add `.glb` input support with color output in controlled phases:
1. Geometry + color (no textures) first
2. Texture integration second

This plan prioritizes fast delivery, low refactor risk, and compatibility with current OBJ-based pipeline behavior.

## Goals
- Accept `.glb` uploads in CLI pipeline.
- Preserve and fully maintain existing `.obj` support.
- Preserve current orientation contract (`X` width, `Y` up, `Z` depth).
- Support color from:
  - Vertex colors (`COLOR_0`)
  - Primitive/base color factor (`baseColorFactor`)
- Export per-brick color to LDraw.
- Prepare architecture for texture-based color in a later phase.

## Non-Goals (Initial Scope)
- Full PBR rendering parity with glTF viewers.
- Support for every glTF extension on day 1.
- Physically accurate texture filtering or lighting.
- Replacing or removing `.obj` ingestion.

## Pre-Implementation Decisions

All decisions below were made before Phase 0 began and are locked for the duration of the implementation.

| # | Decision | Answer |
|---|---|---|
| 1 | `ModelLoader` return type | `LoadedModel` wrapper record containing `Mesh` + `Optional<Map<Triangle, ColorRgb>>` |
| 2 | GLB file format scope | `.glb` only; `.gltf` rejected with a clear error message |
| 3 | Voxel color attribution strategy | Majority vote across all triangles overlapping a voxel; conflicts logged per-voxel |
| 4 | LEGO color palette authority | Rebrickable `data/raw/rebrickable/colors.csv` (already in project); opaque entries only (`is_trans=FALSE`); transparent surfaces matched to nearest opaque color and logged |
| 5 | Color in OBJ export modes | Color only in `ldraw` export mode; `brick`, `voxel-surface`, and `voxel-solid` OBJ exports remain color-free and unchanged |
| 6 | `de.javagl:jgltf-model` version | **2.0.4** (latest stable on Maven Central as of 2026-03-07) |

---

## Target Architecture

### Core Design Decisions
- Introduce loader abstraction (`ModelLoader`) so CLI is not OBJ-only.
- Keep `.obj` as a first-class input format (not legacy/deprecated).
- Keep existing `Mesh` pipeline stable for geometry behavior.
- Introduce optional color data model that can be ignored when color mode is off.
- Keep color pipeline deterministic for testability.
- Color data flows alongside geometry via a `LoadedModel` wrapper record containing
  `Mesh` and `Optional<Map<Triangle, ColorRgb>>`. This keeps `Triangle`, `Mesh`, and
  the voxelizer interface unchanged, and allows color to be omitted without any
  branching in the geometry pipeline. See Phase 0 for rationale.

### GLB Parsing Library
GLB is a binary container format and cannot be parsed with a plain text reader.
The selected library is **`de.javagl:jgltf-model:2.0.4`** (Maven Central), which provides
fully typed Java access to glTF 2.0 accessors, primitives, materials, and node
transforms without native dependencies.

Alternatives considered:
- Hand-written binary reader: high risk, maintenance burden.
- LWJGL Assimp bindings: requires native JNI libraries, adds build complexity.

`de.javagl:jgltf-model:2.0.4` must be added to `pom.xml` during Phase 0.

**Supported input:** `.glb` (self-contained binary) only. `.gltf` (JSON + external files) is
**not supported** and must be rejected with a clear error: `"Unsupported format: .gltf files are
not accepted. Convert to .glb first."` This keeps the loader surface small and avoids external
file resolution complexity.

### Proposed New Components
- `com.lego.mesh.ModelLoader` (interface; returns `LoadedModel`)
- `com.lego.mesh.LoadedModel` (wrapper record: `Mesh mesh`, `Optional<Map<Triangle, ColorRgb>> colorMap`)
- `com.lego.mesh.ObjModelLoader` (adapter around current OBJ flow)
- `com.lego.mesh.GlbLoader` (new; wraps `de.javagl:jgltf-model`)
- `com.lego.model.ColorRgb` (immutable RGB record, float r/g/b in [0,1])
- `com.lego.color.ColorSampler` (maps `Map<Triangle, ColorRgb>` + `VoxelGrid` → per-voxel color grid)
- `com.lego.color.LegoPaletteMapper` (RGB → nearest LDraw color code)

Note: `com.lego.color` is a new package introduced in Phase 0 alongside the
loader abstraction. All color-related classes live here, not in `com.lego.model`
or `com.lego.mesh`.

### LDraw Color Palette
`LegoPaletteMapper` will map 8-bit RGB values to official LDraw color codes using
the Rebrickable color table at **`data/raw/rebrickable/colors.csv`** (already in the
project). The CSV columns are: `id` (= LDraw color code, directly usable in `.ldr`
files), `name`, `rgb` (hex), `is_trans`.

**Opaque-only palette:** Only rows with `is_trans=FALSE` are used for matching.
Transparent surfaces (e.g. glass, water) are matched to the nearest opaque color
and a warning is logged per brick: `"Brick at (x,y,z) matched to opaque color <id>;<name>
but source surface is transparent."` This avoids unintended translucent bricks in
BrickLink Studio while preserving visibility of the mismatch.

Distance is computed in **CIE L\*a\*b\* (CIELAB) ΔE** to minimize perceptual mismatch
rather than Euclidean RGB distance. No separate resource file is needed; the existing
Rebrickable CSV is the single source of truth.

## Phases, Milestones, Deliverables

## Phase 0: Foundations and Contracts
Milestone: Loader contract accepted, color data shape decided, parsing library confirmed

Deliverables:
- [ ] Add `de.javagl:jgltf-model:2.0.4` dependency to `pom.xml`; confirm it resolves and compiles.
- [ ] Define `ModelLoader` interface; define `LoadedModel` wrapper record (`Mesh` + `Optional<Map<Triangle, ColorRgb>>`).
- [ ] Implement `ObjModelLoader` adapter wrapping current `ObjLoader`; returns `LoadedModel` with empty color map.
- [ ] Decide and document color data shape: side-channel `Map<Triangle, ColorRgb>` adopted
      (see Core Design Decisions above); no changes to `Triangle` or `Mesh` records.
- [ ] Define `com.lego.color` package structure (stub classes, no logic yet).
- [ ] Confirm `data/raw/rebrickable/colors.csv` as the palette source; no new file needed.
- [ ] Update CLI/docs language from "OBJ path" to "model path" where appropriate.
- [ ] Reject `.gltf` inputs with the error: `"Unsupported format: .gltf files are not accepted. Convert to .glb first."`
- [ ] Backward-compatibility checklist (all existing OBJ tests must remain green).
- [ ] Add explicit CLI compatibility tests for `.obj` inputs against the new loader abstraction.

Exit criteria:
- `ModelLoader` interface, color data shape, and parsing library all reviewed and locked.
- `pom.xml` dependency resolves cleanly.
- No behavior change for current OBJ runs.
- OBJ remains documented and supported in usage/help output.

## Phase 1: GLB Geometry Support
Milestone: `.glb` geometry parses and produces equivalent voxel/brick outputs

Deliverables:
- [ ] `GlbLoader` supports:
  - `POSITION`, indices, primitive assembly
  - basic node transforms (translation, rotation, scale)
  - multi-mesh scenes
- [ ] Axis orientation normalization for GLB inputs:
  - glTF specifies Y-up right-handed, which matches the internal convention.
  - Root node transforms from exporters (Blender, Maya) may embed axis swaps.
  - `GlbLoader` must apply all node transforms before handing triangles to
    `MeshNormalizer`, so that the resulting `Mesh` always arrives in Y-up space.
  - Add an explicit test: GLB exported from Blender with Z-up setting produces
    the same voxel output as the Y-up variant after normalization.
- [ ] CLI auto-selects loader by file extension (`.obj` → `ObjModelLoader`, `.glb` → `GlbLoader`).
- [ ] Clear errors for unsupported features (e.g. Draco compressed meshes, KHR extensions).
- [ ] Geometry parity tests (`OBJ` vs `GLB` variants of the same model).

Exit criteria:
- Geometry-only GLB model runs end-to-end to OBJ/LDR outputs.
- Axis orientation test passes for both Y-up and Z-up GLB exports.
- Existing OBJ tests still pass.

## Phase 2: Color-Only GLB (No Textures)
Milestone: Colored brick outputs from GLB without texture decoding

Deliverables:
- [ ] Parse and prioritize color sources in `GlbLoader`:
  1. `COLOR_0` vertex colors (per-vertex, normalized to float RGB)
  2. `baseColorFactor` per material/primitive
  3. configurable fallback default color
  The loader outputs a `Map<Triangle, ColorRgb>` alongside the `Mesh`.
- [ ] Implement `ColorSampler`: for each filled voxel in `VoxelGrid`, determine
      its color by finding the triangle(s) that contributed to filling it (tracked
      during voxelization) and applying a majority-vote rule when multiple triangles
      intersect the same voxel. Output: `int[][][] colorGrid` parallel to `VoxelGrid`.
  - Note: this requires `TopologicalVoxelizer` to expose or record the
    triangle-to-voxel mapping. Implement as an opt-in output to avoid performance
    cost when color mode is off.
- [ ] Add brick color consolidation: majority `ColorRgb` across all voxels covered
      by each brick → fed to `LegoPaletteMapper` → LDraw color code per brick.
- [ ] Implement `LegoPaletteMapper` with CIE L\*a\*b\* ΔE nearest-color lookup
      against `data/raw/rebrickable/colors.csv` (opaque rows only). Log a warning
      for each brick where the source color was transparent.
- [ ] Update `LDrawExporter`: replace fixed color `16` with per-brick color code
      when color data is present; use `16` ("current color") when absent.
      **Color is only emitted in `ldraw` export mode.** The `brick`, `voxel-surface`,
      and `voxel-solid` OBJ exporters are unchanged and remain color-free.
- [ ] Add CLI flags:
  - `--color-mode=none|glb-color` (default: `none`)
  - `--color-fallback=<ldrawColorCode>` (default: `16`)
  - Passing `--color-mode=glb-color` with a `.obj` input is an error: print a
    clear message and exit non-zero. OBJ has no color channel; use `--color-mode=none`.

Exit criteria:
- GLB with vertex/base colors produces stable, repeatable colored `.ldr`.
- Behavior with `--color-mode=none` matches current outputs byte-for-byte.
- `LegoPaletteMapper` snapshot tests cover a representative set of input colors.
- Error message test for `--color-mode=glb-color` + `.obj` input passes.

## Phase 3: Texture-Ready Integration Layer
Milestone: Internal model supports texture workflow without full texture rendering yet

Note on schema change: retaining UV coordinates requires extending the face data
model. `Triangle.java` is a record with three `Vector3` vertices only. UVs will
be stored in a parallel side-channel map (`Map<Triangle, TriangleUvs>`) to avoid
modifying the `Triangle` record and to keep the geometry pipeline backward-compatible.
`TriangleUvs` holds three `(float u, float v)` pairs corresponding to the triangle's
vertices. This change does not affect `ObjLoader`, `MeshNormalizer`, or the voxelizer.

Deliverables:
- [ ] Define `com.lego.model.TriangleUvs` record.
- [ ] Extend `ModelLoader` return type to optionally include `Map<Triangle, TriangleUvs>`.
- [ ] `GlbLoader` populates UV map from `TEXCOORD_0` when present.
- [ ] Add texture asset resolver interface (embedded GLB buffer vs. external URI).
- [ ] Add structured diagnostics for missing/invalid texture references.
- [ ] Add placeholder pipeline hooks for texture sampling (no-op implementations).

Exit criteria:
- No regressions in geometry/color-only path.
- `Triangle` record is unchanged.
- UV data is accessible to Phase 4 without further schema changes.

## Phase 4: Texture Color Support
Milestone: GLB textured models map to LEGO colors

Deliverables:
- [ ] Decode `baseColorTexture` image data from embedded GLB buffers.
- [ ] UV sampling for voxel hits: for each voxel, interpolate UV from the
      contributing triangle's `TriangleUvs` and sample the texture image.
- [ ] Combine rule: if `COLOR_0` is present it takes precedence; otherwise
      use sampled texture color; fall back to `baseColorFactor`; then fallback color.
- [ ] `LegoPaletteMapper` tuning:
  - Verify CIE L\*a\*b\* ΔE metric against real LEGO color samples.
  - Optional smoothing mode: majority-color across a brick's immediate neighbors
    to reduce salt-and-pepper noise (`--palette-mode=nearest|smoothed`).
- [ ] CLI options:
  - `--texture-mode=off|base-color` (default: `off`)
  - `--palette-mode=nearest|smoothed` (default: `nearest`)

Exit criteria:
- Textured GLB runs produce stable, repeatable colored `.ldr`.
- Missing textures fail gracefully with fallback color and a warning message.
- Combine rule is unit-tested for all precedence branches.

## Phase 5: Hardening and Release
Milestone: Production-ready GLB + color pipeline

Performance baseline (current OBJ pipeline as of 2026-03-07):
- Wolf model, resolution 75: ~2 s end-to-end, 2756 triangles → 2550 bricks.
- GLB + color pipeline must not exceed 2× this runtime for equivalent models.

Deliverables:
- [ ] Performance benchmarks: small (< 5k triangles), medium (5–50k), large (> 50k).
      Targets measured against the OBJ baseline above.
- [ ] Input guards: file size ceiling (configurable, default 100 MB), triangle count
      ceiling (configurable, default 500k), memory headroom check before voxelization.
- [ ] End-to-end regression suite for OBJ and GLB covering all export modes.
- [ ] Updated docs:
  - usage examples for `.glb` inputs
  - supported GLB features and known unsupported extensions
  - color mode and palette usage guide

Exit criteria:
- Test suite green (all OBJ and GLB tests).
- Performance targets met or documented exceptions noted.
- Documentation published in repo.

## Test Strategy

### Unit Tests
- GLB accessor parsing correctness (positions, indices, colors, UVs).
- Index/primitive triangulation correctness.
- Node transform application correctness (including axis-swap cases).
- Color precedence (`COLOR_0` > `baseColorFactor` > fallback).
- `LegoPaletteMapper` determinism and CIE L\*a\*b\* nearest-color correctness.
- `ColorSampler` majority-vote per voxel correctness.
- `--color-mode=glb-color` + OBJ input produces non-zero exit with clear message.

### Integration Tests
- CLI run on:
  - geometry-only GLB
  - vertex-colored GLB
  - `baseColorFactor`-only GLB
  - texture GLB (Phase 4)
  - `.obj` unchanged from current behavior
- Compare known output metrics:
  - triangle count
  - filled voxels
  - brick count
  - color distribution summary (Phase 2+)

### Regression Guardrails
- Existing OBJ tests must remain green every phase.
- Coordinate/orientation contract must remain unchanged.
- Any GLB PR that breaks OBJ support is blocked from merge.
- `--color-mode=none` output must be byte-for-byte identical to current output.

## Risks and Mitigations
- Risk: GLB parsing complexity and edge cases (compressed meshes, extensions).
  - Mitigation: `de.javagl:jgltf-model` handles parsing; strict unsupported-feature errors surfaced early.
- Risk: Axis orientation bugs in GLB inputs from different exporters.
  - Mitigation: explicit axis normalization step in `GlbLoader`; parity test with Blender Z-up export.
- Risk: Color instability/noise in voxelized output.
  - Mitigation: deterministic majority-vote sampling; optional smoothing mode.
- Risk: Performance regression from color sampling pass.
  - Mitigation: color attribution is opt-in (off by default); benchmark gates in Phase 5.
- Risk: Palette mismatch with expected LEGO colors.
  - Mitigation: CIE L\*a\*b\* distance metric; snapshot tests; configurable palette file.
- Risk: `Triangle` schema changes breaking downstream consumers.
  - Mitigation: UVs and colors are side-channel maps; `Triangle` record is unchanged throughout.

## Milestone Summary
1. Loader abstraction, parsing library, color data shape, and package structure complete
2. GLB geometry end-to-end complete (including axis normalization)
3. GLB color (no textures) complete
4. Texture pipeline integrated
5. Hardened and released

## Definition of Done
- `.glb` input supported in CLI with documented behavior.
- `.obj` input support fully retained and documented.
- Colored `.ldr` output supported for color-enabled GLB models.
- Texture support integrated in final phase with fallback safety.
- Full regression suite passing for both OBJ and GLB flows.
- No changes to `Triangle`, `Mesh`, or `VoxelGrid` public APIs.
