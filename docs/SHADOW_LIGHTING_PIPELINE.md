# Shadow And Lighting Pipeline

## Purpose

This document describes the color pipeline used when converting a textured GLB into LEGO brick colors, with emphasis on the core problem in this project:

- many source GLB textures contain baked shadows
- many source GLB textures contain ambient occlusion
- many source GLB textures contain specular highlights
- the engine often has to infer material color from those already-lit pixels

That makes this a color-separation problem, not a real-time rendering problem.

## Core Problem

The pipeline wants the intrinsic surface color of the model so it can map that color to a discrete LEGO palette entry.

The source asset often provides the rendered appearance instead:

- `albedo * lighting`
- cavity darkening from AO
- brightened highlight regions
- desaturated shadow regions

When the engine samples those pixels directly, it mistakes lighting information for material information.

Typical failure modes:

- a tan or gold region in shadow maps to dark brown, gray, or dark red
- an AO crease is treated as a true dark stripe
- a highlight maps toward white or light gray instead of the base hue
- low-chroma shadowed pixels drift into the wrong hue family

## End-To-End Pipeline

### 1. GLB Load And Texture Extraction

Primary implementation:

- `legomodel/src/main/java/com/lego/mesh/GlbLoader.java`

Relevant behavior:

- loads geometry, materials, textures, UVs, and optional vertex colors
- samples `baseColorTexture`
- multiplies by `baseColorFactor` when present
- converts texture pixels from sRGB to linear RGB

Important detail:

- the texture sampler is reading pixels that may already include baked lighting

Triangle-level texture sampling currently uses several points across the triangle footprint:

- 3 vertices
- 1 centroid
- 3 edge midpoints

Those samples are averaged into a single triangle color in the standard path. This improves stability, but it also blends shadow gradients and highlight variation into the triangle color.

### 2. Triangle Color Attribution

Primary implementation:

- `legomodel/src/main/java/com/lego/color/ColorSampler.java`

Standard path:

- each filled voxel gathers colors from overlapping triangles
- those triangle colors are area-weighted and averaged into one voxel color
- brick color is then averaged again across the brick's voxels

This creates a double-averaging effect:

1. texture samples are averaged into triangle color
2. triangle colors are averaged into voxel color
3. voxel colors are averaged into brick color

This is one reason soft shadowing and AO can spread into the final brick assignment.

### 3. Palette Matching

Primary implementation:

- `legomodel/src/main/java/com/lego/color/LegoPaletteMapper.java`

Goal:

- map a sampled color to the nearest valid opaque LEGO/LDraw palette entry

Constraint:

- LEGO colors are discrete and limited

This means lighting contamination has an outsized effect. A small shift in lightness or chroma can move the nearest match into a completely different LEGO color bucket.

### 4. Shadow-Aware Correction

Primary implementation:

- `legomodel/src/main/java/com/lego/color/UVLabPaletteProjection.java`

This strategy exists because the project already identified baked lighting as a real source of error.

Pipeline inside this strategy:

1. convert sampled linear RGB into CIE L*a*b*
2. compute global lightness statistics across all colored bricks
3. lift the dark tail to reduce shadow bias
4. compress the bright tail to reduce highlight bias
5. stabilize low-chroma colors so shadowed neutrals do not drift into the wrong hue
6. map the corrected color using CIEDE2000

What this fixes:

- dark shadow regions mapping to obviously wrong dark colors
- washed-out or near-gray shadow pixels choosing unstable palette entries
- some highlight-driven over-bright mapping

What it does not fix:

- it cannot perfectly recover true albedo from arbitrary baked textures
- it is a heuristic correction, not full intrinsic-image decomposition

### 5. Alternative Sampling Paths

The repo contains more than one color path.

#### `direct`

- simplest path
- maps averaged brick RGB directly to palette color
- lowest correction
- most exposed to baked-lighting artifacts

#### `uvlab`

- applies lightness normalization and chroma stabilization
- better than `direct` for shadowed textured models

#### `dominant`

- uses per-voxel voting instead of full brick averaging
- helps preserve sharp boundaries and small dark details

#### `supersampled`

- `legomodel/src/main/java/com/lego/color/SupersampledVoxelColorPipeline.java`
- samples texture color at many points per voxel using BVH hits and barycentric UV interpolation
- performs per-sample LAB conversion and per-sample voting
- preserves detail better than the older averaging path

Important limitation:

- even the supersampled path still samples from the same source textures
- if the source texture is baked with lighting, the input is still contaminated

## Post-Processing

Primary implementation:

- `legomodel/src/main/java/com/lego/color/ColorSmoother.java`

This stage attempts to remove rare wrong-hue islands and isolated outliers after palette assignment.

Why it exists:

- baked lighting can create small color patches that are locally self-consistent but globally wrong
- once those patches have been quantized into a LEGO color, they can appear as obvious speckle or rare hue clusters

This is useful cleanup, but it is not the root fix. It only repairs artifacts after misclassification already happened.

## Source-Data Fix: De-Lighting

Primary implementation:

- `legomodel/scripts/delight.py`

This script confirms that the team treats baked lighting as a source-data issue, not only a mapping issue.

It supports two strategies:

- `emit`: rebake clean albedo when lighting comes from the shader graph
- `retinex`: operate directly on texture pixels by normalizing low-frequency lightness variation

This is the strongest fix when the input GLB genuinely has baked shadows in the base-color texture, because it improves the source before palette matching begins.

## Practical Interpretation

The actual pipeline problem can be summarized like this:

1. the loader samples a color that is often already affected by light
2. the sampler averages that color spatially across triangles, voxels, and bricks
3. the palette matcher is forced to quantize the contaminated result into a limited LEGO palette
4. the output can show false darkening, false brightening, wrong hue shifts, and loss of detail

In short:

- the system often receives rendered appearance
- the system needs intrinsic material color
- shadows and lighting corrupt that conversion

## Recommended Mental Model

When debugging a color issue in this project, assume the problem belongs to one of these buckets:

### Input Contamination

- baked shadows
- ambient occlusion in the base-color texture
- highlight information in the base-color texture

### Sampling Loss

- triangle-footprint averaging
- voxel overlap averaging
- brick-level averaging
- detail loss at color boundaries

### Quantization Loss

- discrete LEGO palette limits
- nearest-color instability for low-chroma samples
- wrong-hue jumps caused by small lightness shifts

### Cleanup Heuristics

- shadow lift
- highlight compression
- chroma stabilization
- rare-color smoothing

## Current Best Understanding

The shadow and lighting issue is not one isolated bug. It is the interaction of:

- source textures that may already be lit
- multiple averaging steps that spread those lighting artifacts
- discrete palette matching that amplifies small color errors

The codebase already contains meaningful mitigations, but the problem remains fundamental until the input colors are closer to clean albedo.
