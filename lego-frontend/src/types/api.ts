// ─── Settings ────────────────────────────────────────────────────────────────

export type Algorithm = 'topological' | 'legacy'

export type OutputType =
  | 'brick'
  | 'voxel-surface'
  | 'voxel-solid'
  | 'voxel-slope-surface'
  | 'voxel-surface-combined'
  | 'voxel-slope-placed'
  | 'ldraw'

export type ColorMode = 'glb-color' | 'none'

export type ColorAlgorithm = 'direct' | 'uvlab' | 'dominant' | 'region' | 'supersampled'

export interface Settings {
  resolution: number
  algorithm: Algorithm
  outputType: OutputType
  colorMode: ColorMode
  colorAlgorithm: ColorAlgorithm
}

export const DEFAULT_SETTINGS: Settings = {
  resolution: 40,
  algorithm: 'topological',
  outputType: 'ldraw',
  colorMode: 'glb-color',
  colorAlgorithm: 'direct',
}

// ─── Job state ────────────────────────────────────────────────────────────────

export type JobStatus = 'idle' | 'uploading' | 'queued' | 'running' | 'done' | 'error'

// ─── API response shapes ──────────────────────────────────────────────────────

export interface ConvertResponse {
  jobId: string
}

export interface BrickType {
  partId: string
  name: string
  count: number
}

export interface ColorInfo {
  coloredBrickCount: number
  totalBrickCount: number
  opaquePaletteEntries: number
  colorAlgorithm: string
  smoothedCount: number
}

export interface JobStats {
  triangleCount: number
  resolution: number
  totalVoxels: number
  solidVoxels: number
  surfaceVoxels: number
  brickCount: number
  reductionPercent: number
  placementPolicy: string
  brickTypes: BrickType[]
  colorInfo: ColorInfo | null
}

export interface JobStatusResponse {
  jobId: string
  status: 'queued' | 'running' | 'done' | 'error'
  stage: string
  stats: JobStats | null
  outputFilename: string | null
  error?: string
}
