# Frontend Plan: Lego Architecture Engine Web App

## Tech Stack

- **React + Vite + TypeScript** — minimal setup, fast dev server
- **Tailwind CSS** — utility-first, no component library overhead
- **Axios** — handles multipart upload with progress events
- **TanStack Query** — clean polling loop for job status
- No external state manager needed — `useState`/`useReducer` is sufficient

---

## UX Flow

```
[Drop File] → [Settings Panel] → [Convert] → [Upload Progress] → [Pipeline Stages] → [Stats + Download]
```

The page is single-screen. The settings panel appears after a file is dropped. Controls are locked during upload/run. On error, the user can retry with the same file and settings.

---

## Component Tree

```
App
├── Header
├── UploadZone               ← drag-and-drop, click-to-browse, .obj/.glb validation
├── SettingsPanel            ← revealed after file drop
│   ├── ResolutionInput      ← integer ≥ 2, default 40
│   ├── AlgorithmSelect      ← topological | legacy
│   ├── OutputTypeSelect     ← brick | voxel-surface | ldraw | ...
│   ├── ColorModeToggle      ← glb-color | none (disabled if .obj)
│   └── ColorAlgorithmSelect ← direct | uvlab | dominant | region | supersampled
│                               (shown only when glb-color + .glb)
├── ConvertButton / UploadProgressBar
├── PipelineProgress         ← stage label + spinner + elapsed timer
└── OutputSection
    ├── StatsCard            ← triangles → voxels → bricks, reduction %
    ├── BrickTypeTable       ← partId / name / count
    ├── ColorInfoRow         ← only for ldraw + glb-color
    └── DownloadButton
```

---

## API Contract

The backend needs a Javalin (or Spring Boot) HTTP wrapper around `PipelineRunner`.

**Base URL:** `http://localhost:7070/api`

| Endpoint | Purpose |
|---|---|
| `POST /api/convert` | Multipart upload + config → returns `{ jobId }` |
| `GET /api/jobs/{id}` | Poll status: `queued/running/done/error` + stage name + stats |
| `GET /api/jobs/{id}/download` | Stream output file as attachment |
| `GET /api/health` | Frontend health check on mount |

### POST /api/convert

**Request:** `multipart/form-data`

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | binary | yes | `.obj` or `.glb` file |
| `resolution` | int | yes | >= 2 |
| `algorithm` | string | yes | `topological` or `legacy` |
| `outputType` | string | yes | `brick`, `voxel-surface`, `voxel-solid`, `voxel-slope-surface`, `voxel-surface-combined`, `voxel-slope-placed`, `ldraw` |
| `colorMode` | string | yes | `glb-color` or `none` |
| `colorAlgorithm` | string | no | `direct`, `uvlab`, `dominant`, `region`, `supersampled` |

**Response:** `202 Accepted`
```json
{ "jobId": "a3f9c1b2" }
```

### GET /api/jobs/{jobId}

Poll every 1–2 seconds until `status` is `done` or `error`.

Stage values: `queued`, `loading`, `voxelizing`, `placing_bricks`, `colorizing`, `exporting`, `complete`

**Response (running):**
```json
{
  "jobId": "a3f9c1b2",
  "status": "running",
  "stage": "placing_bricks",
  "stats": null
}
```

**Response (done):**
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
      { "partId": "3001", "name": "Brick 2 x 4", "count": 420 },
      { "partId": "3003", "name": "Brick 2 x 2", "count": 310 }
    ],
    "colorInfo": {
      "coloredBrickCount": 1800,
      "totalBrickCount": 1840,
      "opaquePaletteEntries": 14,
      "colorAlgorithm": "direct",
      "smoothedCount": 23
    }
  },
  "outputFilename": "output.ldr"
}
```

`colorInfo` is `null` when color was not applied.

**Response (error):**
```json
{
  "jobId": "a3f9c1b2",
  "status": "error",
  "stage": "voxelizing",
  "error": "Failed to parse OBJ file: unexpected token at line 42"
}
```

### GET /api/jobs/{jobId}/download

Returns the output file as a binary stream with `Content-Disposition: attachment`.

---

## State Shape

```typescript
type AppState = {
  file: File | null;
  fileType: 'obj' | 'glb' | null;
  settings: {
    algorithm: 'topological' | 'legacy';
    outputType: 'brick' | 'voxel-surface' | 'voxel-solid' | 'voxel-slope-surface' | 'voxel-surface-combined' | 'voxel-slope-placed' | 'ldraw';
    colorMode: 'glb-color' | 'none';
    colorAlgorithm: 'direct' | 'uvlab' | 'dominant' | 'region' | 'supersampled';
    resolution: number;
  };
  jobId: string | null;
  jobStatus: 'idle' | 'uploading' | 'running' | 'done' | 'error';
  uploadProgress: number;     // 0–100 from Axios onUploadProgress
  currentStage: string | null;
  stats: StatsPayload | null;
  errorMessage: string | null;
  outputFilename: string | null;
};
```

Settings are persisted to `localStorage` so they survive page refresh.

---

## Folder Structure

```
lego-frontend/
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── package.json
├── tsconfig.json
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── api/
    │   ├── client.ts           # Axios instance with base URL
    │   └── jobs.ts             # submitJob, pollJob, downloadUrl
    ├── hooks/
    │   ├── useJobPoller.ts     # TanStack Query polling wrapper
    │   └── useLocalStorage.ts  # Persist settings between sessions
    ├── components/
    │   ├── Header.tsx
    │   ├── UploadZone.tsx
    │   ├── SettingsPanel.tsx
    │   ├── ConvertButton.tsx
    │   ├── PipelineProgress.tsx
    │   ├── OutputSection.tsx
    │   ├── StatsCard.tsx
    │   ├── BrickTypeTable.tsx
    │   └── ColorInfoRow.tsx
    ├── types/
    │   └── api.ts              # TypeScript types mirroring the API contract
    └── utils/
        └── fileHelpers.ts      # Extension check, size formatting
```

---

## Build Order

### Phase 1 — Backend API wrapper (do this first)

1. Add Javalin to `pom.xml`
2. Create `ApiServer.java` alongside `Main.java` — wraps `PipelineRunner` over HTTP
3. Implement job storage with `ConcurrentHashMap<String, JobState>`
4. Implement thread pool with `Executors.newFixedThreadPool(2)` for async pipeline runs
5. Implement the four endpoints
6. Add CORS headers for `localhost:5173`
7. Test with curl before touching the frontend

Output files go to a system temp directory keyed by job ID. Clean up with a `ScheduledExecutorService` on a 30-minute TTL.

### Phase 2 — Frontend scaffold

1. `npm create vite@latest lego-frontend -- --template react-ts`
2. Install Tailwind, Axios, TanStack Query
3. Create `api/client.ts`, `api/jobs.ts`, `types/api.ts`

### Phase 3 — Upload flow

1. Build `UploadZone` with drag-and-drop and click-to-browse
2. Wire file drop to App state, display accepted file name + size
3. Build `SettingsPanel` with all controls and sensible defaults
4. Wire `useLocalStorage` to persist settings
5. Build `ConvertButton` with Axios upload progress bar

### Phase 4 — Polling and pipeline progress

1. Build `useJobPoller` with TanStack Query `refetchInterval: 1500`
2. Build `PipelineProgress` — stage label + spinner + elapsed timer
3. Wire App state transitions: `idle → uploading → running → done/error`

### Phase 5 — Output section

1. Build `StatsCard` from stats payload
2. Build `BrickTypeTable`
3. Build `ColorInfoRow` (conditional)
4. Wire `DownloadButton` using `URL.createObjectURL` on a temporary `<a>` element
5. Implement "Start Over" and "Try Again" reset flows

### Phase 6 — Polish

1. Lock settings panel during upload/run
2. Disable color controls for `.obj` files with tooltip
3. Validate resolution input (integer ≥ 2)
4. Health check on mount — show banner if backend unreachable
5. End-to-end test with a real `.glb` and a real `.obj`

---

## Open Question

Whether to add Javalin to the existing `legomodel` Maven module or create a sibling `lego-api` module. A sibling module is cleaner for separation of concerns but adds Maven complexity. For v1, adding to the existing module is fine.
