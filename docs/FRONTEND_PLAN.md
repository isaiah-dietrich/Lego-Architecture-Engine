# Frontend Plan: Lego Architecture Engine Web App

## Status

| Phase | Description | Status |
|---|---|---|
| 1 | Backend API wrapper (Javalin) | **Done** |
| 2 | Frontend scaffold | **Done** |
| 3 | Upload flow + settings + convert button | **Done** |
| 4 | Pipeline progress component | **Done** |
| 5 | Output section (stats + download) | **Remaining** |

---

## Tech Stack

- **React 19 + Vite 8 + TypeScript** — scaffolded via `create-vite`
- **Tailwind CSS v4** — Vite plugin (`@tailwindcss/vite`); no `tailwind.config.ts` needed
- **Axios** — multipart upload with `onUploadProgress` events
- **TanStack Query v5** — polling loop, stops automatically on terminal state

---

## UX Flow

```
[Drop File] → [Settings Panel] → [Convert] → [Upload Progress Bar]
  → [Pipeline Progress] → [Stats + Download]
```

Single-screen. Settings panel appears after a file is dropped. All controls lock while a job is active. Error card has "Try Again" (keeps file + settings). Output has "Start Over" (full reset).

---

## Component Tree

```
App
├── Header                   ✓ done
├── OfflineBanner            ✓ done (inline in App)
├── UploadZone               ✓ done — drag-and-drop, click-to-browse, file badge, clear button
├── SettingsPanel            ✓ done — revealed after file drop
│   ├── ResolutionInput      ✓ integer ≥ 2, validated on blur
│   ├── AlgorithmSelect      ✓ topological | legacy
│   ├── OutputTypeSelect     ✓ all 7 export modes
│   ├── ColorModeToggle      ✓ glb-color | none (auto-disabled + tooltip for .obj)
│   └── ColorAlgorithmSelect ✓ shown only when glb-color + .glb
├── ConvertButton            ✓ done — idle button / animated upload progress bar
├── PipelineProgress         ✓ done — spinner, stage breadcrumb, elapsed timer
└── OutputSection            ☐ Phase 5
    ├── StatsCard            ☐ triangles → voxels → bricks, reduction %
    ├── BrickTypeTable       ☐ partId / name / count, sorted by count desc
    ├── ColorInfoRow         ☐ only for ldraw + glb-color results
    └── DownloadButton       ☐ direct link to /api/jobs/{id}/download
```

---

## API Contract

**Base URL:** `http://localhost:7070/api`

> Port 7070 — macOS AirPlay receiver owns 7000.

| Endpoint | Purpose |
|---|---|
| `POST /api/convert` | Multipart upload + config → `{ jobId }` |
| `GET /api/jobs/{id}` | Poll status + stage + stats |
| `GET /api/jobs/{id}/download` | Stream output file |
| `GET /api/health` | Reachability check on mount |

### POST /api/convert — form fields

| Field | Type | Default | Notes |
|---|---|---|---|
| `file` | binary | — | `.obj` or `.glb` |
| `resolution` | int | — | >= 2 |
| `algorithm` | string | `topological` | `topological` or `legacy` |
| `outputType` | string | `ldraw` | see export modes below |
| `colorMode` | string | `glb-color` / `none` | server defaults `none` for `.obj` |
| `colorAlgorithm` | string | `direct` | ignored unless `colorMode=glb-color` |

### GET /api/jobs/{jobId} — response shape

```json
{
  "jobId": "a3f9c1b2",
  "status": "done",
  "stage": "complete",
  "stats": {
    "triangleCount": 12480,
    "resolution": 40,
    "totalVoxels": 64000,
    "solidVoxels": 8200,
    "surfaceVoxels": 3100,
    "brickCount": 1840,
    "reductionPercent": 40.6,
    "placementPolicy": "mask",
    "brickTypes": [
      { "partId": "3001", "name": "Brick 2 x 4", "count": 420 }
    ],
    "colorInfo": {
      "coloredBrickCount": 1800,
      "totalBrickCount": 1840,
      "opaquePaletteEntries": 14,
      "colorAlgorithm": "direct",
      "smoothedCount": 23
    }
  },
  "outputFilename": "output.ldr",
  "error": null
}
```

`stats` and `outputFilename` are `null` until `status === "done"`.
`colorInfo` is `null` when color was not applied.

**Implemented stage values:** `queued`, `loading`, `exporting`, `complete`, `error`

> The plan originally specified intermediate stages (`voxelizing`, `placing_bricks`, `colorizing`). These are not currently emitted by the backend — `PipelineProgress` handles unknown stage strings gracefully by auto-capitalizing them.

---

## State Shape (as implemented in App.tsx)

```typescript
// Persisted to localStorage
settings: Settings  // { resolution, algorithm, outputType, colorMode, colorAlgorithm }

// Ephemeral
file: File | null
jobStatus: 'idle' | 'uploading' | 'queued' | 'running' | 'done' | 'error'
jobId: string | null
uploadProgress: number       // 0–100, Axios onUploadProgress
pipelineStartTime: number    // epoch ms, set when job is accepted (202)
serverOnline: boolean | null // set by health check on mount

// Via TanStack Query (useJobPoller)
jobData: JobStatusResponse | undefined  // polled every 1.5s while active
```

---

## Folder Structure (as built)

```
lego-frontend/
├── index.html
├── vite.config.ts           # Tailwind v4 plugin + React plugin
├── package.json
├── tsconfig.json
└── src/
    ├── main.tsx             # QueryClientProvider root
    ├── App.tsx              # All state + layout
    ├── index.css            # @import "tailwindcss" + minimal resets
    ├── api/
    │   ├── client.ts        # Axios instance → localhost:7070
    │   └── jobs.ts          # submitJob, pollJob, downloadUrl, checkHealth
    ├── hooks/
    │   ├── useJobPoller.ts  # TanStack Query, refetchInterval 1500ms
    │   └── useLocalStorage.ts
    ├── components/
    │   ├── Header.tsx
    │   ├── UploadZone.tsx
    │   ├── SettingsPanel.tsx
    │   ├── ConvertButton.tsx
    │   ├── PipelineProgress.tsx
    │   ├── OutputSection.tsx     ☐ Phase 5
    │   ├── StatsCard.tsx         ☐ Phase 5
    │   ├── BrickTypeTable.tsx    ☐ Phase 5
    │   └── ColorInfoRow.tsx      ☐ Phase 5
    ├── types/
    │   └── api.ts           # All TS types + DEFAULT_SETTINGS
    └── utils/
        └── fileHelpers.ts   # getFileType, formatBytes
```

---

## Phase 5 — Output Section

The output section is currently a placeholder in `App.tsx`. Replace it with:

### StatsCard

Key numbers in a grid — triangles in, bricks out, reduction percent, resolution.

```
12,480 triangles  →  40³ voxels  →  1,840 bricks  (40.6% reduction)
```

### BrickTypeTable

Sorted by count descending. Columns: part ID, part name, count, bar showing relative proportion.

### ColorInfoRow

Shown only when `stats.colorInfo !== null`. One line:
`1,800 / 1,840 bricks colored · 14 palette colors · direct algorithm · 23 smoothed`

### DownloadButton

Direct `<a>` link to `GET /api/jobs/{id}/download` — no `createObjectURL` needed since the API streams the file with the correct `Content-Disposition` header.

Also needs:
- "Start Over" button → `resetJob(); setFile(null)`
- "Convert Again" button → `resetJob()` only (keeps file + settings for re-runs)

---

## Backend Files

| File | Role |
|---|---|
| `legomodel/src/main/java/com/lego/api/ApiServer.java` | Javalin server, 4 endpoints, thread pool, cleanup scheduler |
| `legomodel/src/main/java/com/lego/api/JobState.java` | Mutable job state with volatile fields |
| `legomodel/src/main/java/com/lego/cli/PipelineRunner.java` | Added `runForApi()` and `runCore()` alongside existing `run()` |

Start the server:
```bash
cd legomodel
mvn compile exec:java -Dexec.mainClass=com.lego.api.ApiServer
```

Start the frontend:
```bash
cd lego-frontend
npm run dev   # http://localhost:5173
```
