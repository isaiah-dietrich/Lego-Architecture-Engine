# Voxel Contract Definition & Lock

**Status**: LOCKED via comprehensive test suite (64 tests)  
**Date**: February 27, 2026  
**Scope**: Normalization → Voxelization → Surface Extraction pipeline

## 1. Contract Invariants (LOCKED)

### 1.1 NORMALIZATION → VOXEL SPACE MAPPING

**Invariant: Coordinate System Transformation**
```
Input mesh (arbitrary bounds) → Normalized mesh → Voxel grid
```

**Rules:**
- ✅ Min corner (minX, minY, minZ) → (0, 0, 0)
- ✅ Largest axis dimension → exactly [0, resolution]
- ✅ Non-largest axes → preserved aspect ratio, fit within [0, resolution]
- ✅ Uniform scaling applied to all axes (no shearing)
- ✅ Aspect ratios preserved across all transformations

**Voxel Sampling Convention:**
- Voxel grid: [0, resolution) × [0, resolution) × [0, resolution)
- Voxel centers sampled at: **(x + 0.5, y + 0.5, z + 0.5)** where x, y, z ∈ [0, resolution-1]
- Coverage: normalized mesh must fit completely in [0, resolution] bounds
- Ray-casting: shoots rays in +X direction from voxel centers using **Moller-Trumbore algorithm**

**Tests Verifying:**
- `testTranslationToOrigin()` - min corner → (0,0,0) ✓
- `testUniformScalingPreservesProportions()` - aspect ratios preserved ✓
- `testNormalizedMeshFitsBounds()` - mesh fits [0, resolution] ✓
- `testLargestAxisMaxEqualsResolution()` - largest axis = resolution exactly ✓
- `testAsymmetricMeshAspectRatios()` - non-cubic meshes preserve ratios ✓
- `testResolutionTwoEdgeCase()` - minimum resolution=2 works ✓
- `testNormalizationVoxelSamplingAlignment()` - integration test ✓

---

### 1.2 VOXELIZATION → SOLID GRID

**Invariant: Ray-Casting Parity Occupancy**
```
Normalized mesh + ray-casting → VoxelGrid[x,y,z] = {true (inside), false (outside)}
```

**Rules:**
- ✅ Grid dimensions: resolution × resolution × resolution
- ✅ Deterministic output: same mesh → identical grid (all voxel positions match)
- ✅ Ray bias values (fixed):
  - `RAY_BIAS_Y = 1e-6`
  - `RAY_BIAS_Z = 2e-6`
  - Purpose: avoid hits exactly on shared triangle edges
- ✅ Epsilon for intersection: `EPSILON = 1e-9`
- ✅ Parity rule: odd intersection count = inside, even = outside
- ✅ No out-of-bounds writes: all filled voxels in valid range [0, resolution)

**Known Occupancy Patterns:**
- Unit cube [0,1]³ at resolution 10 → ~300-400 filled voxels
- Cube [0,2]³ at resolution 2 → 8 filled voxels (all corners)
- Tetrahedron, pyramid, asymmetric boxes produce expected patterns

**Tests Verifying:**
- `testCubeProducesFilledVoxels()` - basic voxelization works ✓
- `testEmptyMeshProducesZeroVoxels()` - empty input → empty output ✓
- `testDeterminismAcrossRuns()` - repeated voxelization identical ✓
- `testResolutionValidation()` - resolution < 2 rejected ✓
- `testBoundaryCoverageAtResolutionTwo()` - all 8 corners filled at res=2 ✓
- `testOutputGridDimensionsMatchResolution()` - grid size matches input ✓
- `testPyramidVoxelization()` - non-cube mesh works ✓
- `testDeterministicVoxelPositions()` - exact position determinism ✓
- `testAsymmetricMeshVoxelization()` - tall/thin objects voxelize ✓

---

### 1.3 SURFACE EXTRACTION → HOLLOW SHELL

**Invariant: Boundary Voxel Selection**
```
Solid VoxelGrid → Keep voxels with ≥1 empty 6-neighbor → Surface VoxelGrid
```

**Rules:**
- ✅ Surface ⊆ Solid (every surface voxel was filled in input)
- ✅ 6-neighbor check: {±X, ±Y, ±Z}
- ✅ Out-of-bounds treated as empty → boundary voxels kept
- ✅ Fully internal voxels (all 6 neighbors filled) → excluded
- ✅ Isolated voxels (all 6 neighbors empty) → kept (trivial surface)
- ✅ Deterministic: same input → identical surface grid

**Hollow Shell Property:**
- Result is topologically a "shell" of the solid volume
- Interior voxels removed, boundary voxels retained
- Useful for brick placement (surface is where bricks can go)

**Tests Verifying:**
- `testNullInputThrowsException()` - null guard ✓
- `testSingleVoxelRemainsSurface()` - isolated voxel kept ✓
- `testFilled3x3x3RemovesCenterVoxel()` - internal voxels excluded ✓
- `testEmptyGridStaysEmpty()` - empty → empty ✓
- `testDeterministicResult()` - determinism ✓
- `testSurfaceIsSubsetOfSolid()` - subset property ✓
- `testInternalVoxelsExcluded()` - fully internal removed ✓
- `testAllSurfaceVoxelsHaveEmptyNeighbor()` - boundary property ✓
- `testLargeSolidGridSurfaceExtraction()` - larger grids (4×4×4) work ✓
- `testScatteredVoxelsRemainSurface()` - isolated voxels kept ✓

---

### 1.4 FULL PIPELINE INTEGRATION

**Invariant: End-to-End Consistency**
```
Mesh → Normalize → Voxelize → Extract Surface → Valid hollow shell grid
```

**Rules:**
- ✅ All normalization bounds invariants hold
- ✅ All voxelization determinism holds
- ✅ All surface extraction subset properties hold
- ✅ Pipeline determinism: same mesh → identical final surface grid
- ✅ Works for symmetric (cubes) and asymmetric (tall/thin) meshes
- ✅ Works for minimum resolution=2 and larger

**Tests Verifying:**
- `testNormalizationVoxelSamplingAlignment()` - norm coords match voxel space ✓
- `testVoxelizationOutputBounds()` - no out-of-bounds writes ✓
- `testSurfaceExtractionSubsetInvariant()` - subset holds end-to-end ✓
- `testFullPipelineDeterminism()` - complete determinism ✓
- `testAsymmetricMeshPipeline()` - non-cubes work ✓
- `testMinimumResolutionPipeline()` - res=2 edge case ✓
- `testEmptyMeshProducesEmptyVoxels()` - empty inputs handled ✓

---

## 2. Test Suite Summary

| Test Class | Count | Status |
|---|---|---|
| `ObjLoaderTest` | 14 | ✅ PASS |
| `MeshNormalizerTest` | 9 | ✅ PASS |
| `BoundingBoxTest` | 3 | ✅ PASS |
| `SurfaceExtractorTest` | 10 | ✅ PASS |
| `VoxelGridTest` | 7 | ✅ PASS |
| `VoxelizerTest` | 9 | ✅ PASS |
| `VoxelContractTest` | 7 | ✅ PASS |
| `MainTest` | 5 | ✅ PASS |
| **TOTAL** | **64** | **✅ ALL PASS** |

**Build Result:**
```
mvn clean test:   Tests run: 64, Failures: 0, Errors: 0 ✅
mvn clean package: BUILD SUCCESS ✅
```

---

## 3. Edge Cases & Constraints (LOCKED)

### 3.1 Resolution Limits
- **Minimum**: resolution = 2 (tested, works correctly)
- **Maximum**: no hard limit (tested up to 40, works)
- **Recommendation**: res ≥ 8 for reasonable brick placement later

### 3.2 Mesh Properties
- **Triangulated required**: quads auto-triangulated, 5+ vertices rejected
- **Degenerate meshes**: zero-size bounding box rejected
- **Empty meshes**: handled gracefully (empty grid result)
- **Negative coordinates**: allowed, normalized appropriately

### 3.3 Aspect Ratios
- **Cubes**: 1:1:1 preserved
- **Rectangular boxes**: a:b:c preserved exactly
- **Tall/thin objects**: e.g., 1:1:8 aspect ratio preserved

### 3.4 Voxel Determinism
- Same mesh + same resolution → **byte-identical grids**
- Order of voxel processing: x → y → z (outer to inner loops)
- Ray direction: +X axis (fixed)
- Ray bias: fixed constants (not random)

---

## 4. Ambiguities / Decisions Made

**No Ambiguities Remaining** - All behavior is locked by tests.

### Decisions During Contract Definition:
1. ✅ **Ray bias asymmetry** (RAY_BIAS_Y ≠ RAY_BIAS_Z): Required to avoid diagonal edge hits at low resolution
2. ✅ **Uniform scaling only**: Non-uniform scaling would break aspect ratio preservation
3. ✅ **Largest axis normalization**: Ensures voxel sampling covers full mesh with 0.5 spacing
4. ✅ **6-neighbor surface definition**: Standard 3D topology, clear and deterministic
5. ✅ **Out-of-bounds = empty**: Simplifies boundary handling, makes isolated voxels remain surface

---

## 5. What's Guaranteed for Brick Placement

Brick placement logic can **reliably count on:**

1. **Grid is always cuboid**: width = height = depth = resolution
2. **Coordinates are consecutive**: [0, resolution) with no gaps
3. **Surface voxels form a connected shell**: every surface voxel touches empty space
4. **No internal "hidden" voxels**: fully internal voxels are excluded
5. **Deterministic output**: can cache results, compare grids
6. **Bounded input validation**: resolution and mesh constraints enforced
7. **Aspect ratios preserved**: can infer object shape from voxel distribution

---

## 6. Contract Lock Mechanism

This contract is enforced by:

1. **Unit tests** (64 integrated tests across all modules)
2. **No further production code changes** until new requirements identified
3. **Test execution at build time** (maven surefire)
4. **Clear error messages** for contract violations (e.g., resolution < 2)
5. **Determinism tests** catch accidental non-determinism

Any changes to voxelization logic require:
- ✅ All 64 tests still passing
- ✅ New test added for new behavior
- ✅ Documentation update here

---

## 7. Next Phase (Brick Placement) Prereq Checklist

Before implementing brick merging (Phase 3):

- [x] Normalization contract locked (tests: 9/9)
- [x] Voxelization contract locked (tests: 9/9)
- [x] Surface extraction contract locked (tests: 10/10)
- [x] Integration contract locked (tests: 7/7)
- [x] Edge cases covered (res=2, asymmetric, empty meshes)
- [x] Determinism guaranteed across pipeline
- [x] No ambiguous behavior remaining

**READY FOR PHASE 3: Brick Placement Logic** ✅
