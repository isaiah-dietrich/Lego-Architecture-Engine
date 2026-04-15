import { useEffect, useRef, useState } from 'react'
import { checkHealth, submitJob, downloadUrl } from './api/jobs'
import { useJobPoller } from './hooks/useJobPoller'
import { useLocalStorage } from './hooks/useLocalStorage'
import { getFileType, formatBytes } from './utils/fileHelpers'
import type { Settings, JobStatus } from './types/api'
import { DEFAULT_SETTINGS } from './types/api'

// ─── Placeholder component stubs (replaced in Phase 3–5) ─────────────────────

function Header() {
  return (
    <header className="border-b border-gray-200 dark:border-gray-800 px-6 py-4 flex items-center gap-3">
      <span className="text-xl font-semibold tracking-tight text-gray-900 dark:text-gray-100">
        Lego Architecture Engine
      </span>
    </header>
  )
}

function OfflineBanner() {
  return (
    <div className="bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-200 text-sm px-4 py-2 rounded-md">
      API server is not reachable. Start it with{' '}
      <code className="font-mono text-xs bg-amber-100 dark:bg-amber-900 px-1 py-0.5 rounded">
        mvn compile exec:java -Dexec.mainClass=com.lego.api.ApiServer
      </code>
    </div>
  )
}

// ─── App ──────────────────────────────────────────────────────────────────────

export default function App() {
  const [settings, setSettings] = useLocalStorage<Settings>('lego-settings', DEFAULT_SETTINGS)
  const [file, setFile] = useState<File | null>(null)
  const [jobStatus, setJobStatus] = useState<JobStatus>('idle')
  const [jobId, setJobId] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [serverOnline, setServerOnline] = useState<boolean | null>(null)

  const { data: jobData } = useJobPoller(
    jobStatus === 'queued' || jobStatus === 'running' ? jobId : null,
  )

  // Health check on mount
  useEffect(() => {
    checkHealth().then(setServerOnline)
  }, [])

  // Sync job status from poller
  useEffect(() => {
    if (!jobData) return
    if (jobData.status === 'done') setJobStatus('done')
    else if (jobData.status === 'error') setJobStatus('error')
    else setJobStatus('running')
  }, [jobData])

  const fileType = file ? getFileType(file) : null

  async function handleConvert() {
    if (!file) return
    setJobStatus('uploading')
    setUploadProgress(0)
    try {
      const id = await submitJob(file, settings, setUploadProgress)
      setJobId(id)
      setJobStatus('queued')
    } catch {
      setJobStatus('error')
    }
  }

  function handleReset() {
    setFile(null)
    setJobId(null)
    setJobStatus('idle')
    setUploadProgress(0)
  }

  const busy = jobStatus === 'uploading' || jobStatus === 'queued' || jobStatus === 'running'
  const done = jobStatus === 'done'
  const errored = jobStatus === 'error'

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950 text-gray-900 dark:text-gray-100 flex flex-col">
      <Header />

      <main className="flex-1 flex flex-col items-center gap-6 px-4 py-10 max-w-2xl mx-auto w-full">
        {serverOnline === false && <OfflineBanner />}

        {/* Upload zone placeholder */}
        <div className="w-full border-2 border-dashed border-gray-300 dark:border-gray-700 rounded-xl p-10 text-center text-gray-500 dark:text-gray-400">
          {file
            ? `${file.name} (${formatBytes(file.size)})`
            : 'Drop your .obj or .glb file here — components coming in Phase 3'}
          <br />
          <input
            type="file"
            accept=".obj,.glb"
            className="mt-4"
            onChange={e => {
              const f = e.target.files?.[0] ?? null
              if (f) { setFile(f); handleReset(); setFile(f) }
            }}
          />
        </div>

        {/* Settings placeholder */}
        {file && (
          <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-6 text-sm text-gray-500 dark:text-gray-400">
            Settings panel — Phase 3
            <br />fileType: {fileType} | colorMode locked to none for .obj: {fileType === 'obj' ? 'yes' : 'no'}
            <br />
            <button
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium disabled:opacity-50"
              onClick={handleConvert}
              disabled={busy}
            >
              {jobStatus === 'uploading' ? `Uploading… ${uploadProgress}%` : 'Convert'}
            </button>
          </div>
        )}

        {/* Progress placeholder */}
        {(jobStatus === 'queued' || jobStatus === 'running') && (
          <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-6 text-sm text-gray-500 dark:text-gray-400">
            Pipeline running — stage: {jobData?.stage ?? 'queued'} — Phase 4 component
          </div>
        )}

        {/* Output placeholder */}
        {done && jobData?.stats && (
          <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-6 text-sm">
            <p className="font-medium text-gray-900 dark:text-gray-100">Done!</p>
            <p className="text-gray-500 dark:text-gray-400 mt-1">
              {jobData.stats.brickCount} bricks · {jobData.stats.reductionPercent}% reduction
            </p>
            <div className="mt-4 flex gap-3">
              <a
                href={downloadUrl(jobId!)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium"
              >
                Download {jobData.outputFilename}
              </a>
              <button
                onClick={handleReset}
                className="px-4 py-2 border border-gray-300 dark:border-gray-700 rounded-lg text-sm"
              >
                Start Over
              </button>
            </div>
          </div>
        )}

        {/* Error placeholder */}
        {errored && (
          <div className="w-full bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-xl p-6 text-sm text-red-800 dark:text-red-200">
            <p className="font-medium">Conversion failed</p>
            <p className="mt-1">{jobData?.error ?? 'Upload failed — check the console'}</p>
            <button
              onClick={() => setJobStatus(file ? 'idle' : 'idle')}
              className="mt-4 px-4 py-2 border border-red-300 dark:border-red-700 rounded-lg text-sm"
            >
              Try Again
            </button>
          </div>
        )}
      </main>
    </div>
  )
}
