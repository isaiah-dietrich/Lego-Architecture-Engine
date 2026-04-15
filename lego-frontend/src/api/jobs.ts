import client from './client'
import type { ConvertResponse, JobStatusResponse, Settings } from '../types/api'

/**
 * Upload a model file and start a conversion job.
 * Returns the job ID immediately (202 Accepted).
 *
 * @param file       the .obj or .glb file to convert
 * @param settings   pipeline configuration
 * @param onProgress called with upload progress 0–100
 */
export async function submitJob(
  file: File,
  settings: Settings,
  onProgress: (pct: number) => void,
): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  form.append('resolution', String(settings.resolution))
  form.append('algorithm', settings.algorithm)
  form.append('outputType', settings.outputType)
  form.append('colorMode', settings.colorMode)
  form.append('colorAlgorithm', settings.colorAlgorithm)

  const res = await client.post<ConvertResponse>('/convert', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 5 * 60 * 1000, // 5 min for large files
    onUploadProgress(event) {
      if (event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })

  return res.data.jobId
}

/**
 * Poll the status of a conversion job.
 */
export async function pollJob(jobId: string): Promise<JobStatusResponse> {
  const res = await client.get<JobStatusResponse>(`/jobs/${jobId}`)
  return res.data
}

/**
 * Returns the URL for downloading the output file of a completed job.
 */
export function downloadUrl(jobId: string): string {
  return `http://localhost:7070/api/jobs/${jobId}/download`
}

/**
 * Check whether the API server is reachable.
 */
export async function checkHealth(): Promise<boolean> {
  try {
    await client.get('/health', { timeout: 3000 })
    return true
  } catch {
    return false
  }
}
