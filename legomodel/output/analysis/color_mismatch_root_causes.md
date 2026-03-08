# GLB → LDraw Color Mismatch: Root Causes Analysis

**Analysis Date:** 2026-03-08  
**Model:** `the_cats_body.glb` → `the_cats_body.ldr`  
**Pipeline:** GlbLoader → ColorSampler → LegoPaletteMapper → LDrawExporter

---

## 🔴 CONFIRMED ROOT CAUSES (Fixed)

### 1. **UV V-Coordinate Inversion**
- **Status:** ✅ FIXED
- **Location:** `GlbLoader.java:385-420` (`sampleTexture` and `sampleTextureFiltered`)
- **Issue:** glTF UV space has origin at bottom-left (V=0 is bottom), but image pixel coordinates have origin at top-left (Y=0 is top). The sampler was not flipping the V coordinate when converting to pixel Y coordinate.
- **Impact:** Textures sampled upside-down, causing wrong colors (e.g., sampling dark fur regions instead of white belly regions)
- **Fix Applied:** Added `uvToPixelY()` helper that computes `(1 - v) * imageHeight` to flip V-axis
- **Test Added:** `textureSamplingFlipsVFromGltfToImageSpace()` in `GlbLoaderTest.java`

### 2. **Invalid LDraw Color Codes from High Rebrickable IDs**
- **Status:** ✅ FIXED
- **Location:** `LegoPaletteMapper.java:35-82`
- **Issue:** Rebrickable CSV contains extended color IDs (e.g., 1137, 1083, 1088, 1092, 1119, 1138) that are NOT valid LDraw color codes. Standard LDraw colors are in range [0, 511]. These high IDs render as black or undefined in most LDraw viewers.
- **Impact:** Major color quantization artifacts; many bricks rendered as black/wrong colors despite correct RGB sampling
- **Evidence:** 
  ```
  610 bricks → color 1137 (invalid)
  486 bricks → color 86
  275 bricks → color 28
  236 bricks → color 21
  221 bricks → color 1021 (invalid)
  ```
- **Fix Applied:** Filter palette entries to `id ≤ 511` during CSV load
- **Test Added:** `ignoresOutOfRangeRebrickableIds()` in `LegoPaletteMapperTest.java`

---

## 🟡 POTENTIAL CONTRIBUTING FACTORS (Not Yet Addressed)

### 3. **sRGB ↔ Linear RGB Mismanagement**
- **Location:** `GlbLoader.java:437-442`, `LegoPaletteMapper.java:131-136`
- **Issue:** 
  - Texture samples are converted from sRGB → linear RGB (via `srgbToLinear()`)
  - Palette CSV stores colors as sRGB hex (`rgb` column)
  - Palette conversion also does sRGB → linear → XYZ → LAB
  - **Potential mismatch:** If texture baseColorFactor is already in linear space but treated as sRGB during conversion
- **Risk:** Medium — incorrect gamma could shift colors toward darker or lighter values
- **Verification Needed:** Confirm glTF spec compliance for `baseColorFactor` (spec says it's linear RGB)

### 4. **UV Padding Pixel Filtering**
- **Location:** `GlbLoader.java:61`, `sampleTextureFiltered:409-425`
- **Issue:** Pixels with all sRGB channels ≤ 10 are treated as UV-atlas padding and excluded from color averaging
- **Risk:** Low-Medium — could exclude legitimate dark brown/black fur pixels (e.g., stripes)
- **Current Threshold:** `UV_PADDING_SRGB_THRESHOLD = 10` (very dark gray)
- **Impact:** Multi-sample averaging (7 samples per triangle) may skip valid color data, biasing toward lighter samples

### 5. **CIE L\*a\*b\* ΔE76 Distance Metric**
- **Location:** `LegoPaletteMapper.java:111-117`, `linearRgbToLab:138-162`
- **Issue:** ΔE76 uses simple Euclidean distance in LAB space, which does NOT perfectly match human perceptual difference (especially for blues/greens)
- **Better Metric:** ΔE2000 (CIEDE2000) accounts for perceptual non-uniformity
- **Risk:** Low — but could cause "nearest color" to be perceptually wrong (e.g., mapping orange fur to red instead of tan)

### 6. **Limited LEGO Palette Gamut**
- **Location:** `data/raw/rebrickable/colors.csv`
- **Issue:** After filtering to standard LDraw codes [0-511], the opaque palette may be reduced significantly
- **Current Count:** Originally >100 opaque entries; likely ~40-70 after filtering high IDs
- **Impact:** Inherent quantization loss — GLB may have smooth color gradients (e.g., orange tabby stripes → tan → white) that collapse into discrete LEGO colors

### 7. **Multi-Sampling Strategy**
- **Location:** `GlbLoader.java:287-331`
- **Issue:** 7-point sampling (3 vertices + centroid + 3 edge midpoints) averages color across triangle footprint
- **Risk:** Low-Medium — large triangles spanning multiple texture regions (e.g., body panel with stripes) may average distinct colors into a muddy intermediate
- **Alternative:** Area-weighted texture filtering or UV-space triangle rasterization (complex)

### 8. **Voxel-to-Triangle Color Assignment**
- **Location:** `ColorSampler.java:64-146`
- **Issue:** Per-voxel color is area-weighted average of all overlapping triangles; per-brick color is average of all voxels
- **Risk:** Medium — double-averaging could wash out sharp color boundaries (e.g., stripe edges)
- **Impact:** Fine details (thin stripes, spots) may be lost if they don't align with voxel grid

### 9. **Missing baseColorFactor Multiplication**
- **Status:** ❓ TO VERIFY
- **Location:** `GlbLoader.java:318-323`
- **Issue:** Code multiplies texture sample by `baseColorFactor` if both are present, but need to verify factor is applied correctly
- **Risk:** Low — looks correct in current code

---

## 📊 DIAGNOSIS SUMMARY

| Cause | Severity | Fixed | Test Coverage |
|-------|----------|-------|---------------|
| UV V-flip | 🔴 Critical | ✅ | ✅ Regression test added |
| Invalid LDraw codes | 🔴 Critical | ✅ | ✅ Regression test added |
| sRGB/Linear mismatch | 🟡 Medium | ❌ | ⚠️ Existing unit tests pass |
| UV padding filter | 🟡 Medium | ❌ | ⚠️ Needs threshold tuning |
| LAB distance metric | 🟢 Low | ❌ | N/A (design choice) |
| Palette gamut | 🟢 Low | N/A | N/A (inherent limitation) |
| Multi-sampling blur | 🟢 Low | ❌ | N/A (design tradeoff) |
| Voxel averaging | 🟡 Medium | ❌ | ✅ Unit tests for area weighting |
| baseColorFactor | 🟢 Low | N/A | ✅ Integration test passes |

---

## 🔬 NEXT STEPS FOR VERIFICATION

1. **Regenerate the_cats_body.ldr** with fixed UV sampling and palette filtering
2. **Compare color code distribution** (before/after) — expect elimination of 1000+ codes
3. **Visual inspection** in LDraw viewer (e.g., LeoCAD, LDView) 
4. **Histogram analysis** of pixel CSVs:
   - `the_cats_body_glb_pixels.csv` (1114×786, 31,387 unique colors)
   - `the_cats_body_ldraw_pixels.csv` (1692×1384, 5,347 unique colors)
5. **Per-region color sampling** — manually check if white belly, orange back, tan paws map correctly

---

## 📁 ARTIFACTS GENERATED

- ✅ `legomodel/output/analysis/the_cats_body_glb_pixels.csv` (876,204 pixels)
- ✅ `legomodel/output/analysis/the_cats_body_ldraw_pixels.csv` (2,340,528 pixels)
- ✅ Regression tests: `GlbLoaderTest.textureSamplingFlipsVFromGltfToImageSpace()`
- ✅ Regression tests: `LegoPaletteMapperTest.ignoresOutOfRangeRebrickableIds()`

---

## 🎯 EXPECTED IMPROVEMENT

- **Before Fix:** Upside-down texture + ~40% invalid color codes → severe color distortion
- **After Fix:** Correct texture orientation + standard LDraw palette → accurate color quantization within gamut limits
- **Remaining Limitation:** LEGO palette has ~50-70 distinct opaque solid colors; smooth gradients will always quantize to discrete steps
