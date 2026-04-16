import { useEffect, useState } from 'react'
import { checkHealth, submitJob, downloadUrl } from './api/jobs'
import { useJobPoller } from './hooks/useJobPoller'
import { useLocalStorage } from './hooks/useLocalStorage'
import { getFileType } from './utils/fileHelpers'
import type { Settings, JobStatus } from './types/api'
import { DEFAULT_SETTINGS } from './types/api'
import Header from './components/Header'
import UploadZone from './components/UploadZone'
import SettingsPanel from './components/SettingsPanel'
import ConvertButton from './components/ConvertButton'
import PipelineProgress from './components/PipelineProgress'
import OutputSection from './components/OutputSection'

function OfflineBanner() {
  return (
    <div className="w-full bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800 text-amber-800 dark:text-amber-200 text-sm px-4 py-3 rounded-xl">
      API server is not reachable. Start it with:{' '}
      <code className="font-mono text-xs bg-amber-100 dark:bg-amber-900 px-1.5 py-0.5 rounded">
        mvn compile exec:java -Dexec.mainClass=com.lego.api.ApiServer
      </code>
    </div>
  )
}

export default function App() {
  const [settings, setSettings] = useLocalStorage<Settings>('lego-settings', DEFAULT_SETTINGS)
  const [file, setFile] = useState<File | null>(null)
  const [jobStatus, setJobStatus] = useState<JobStatus>('idle')
  const [jobId, setJobId] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [pipelineStartTime, setPipelineStartTime] = useState<number>(0)
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

  // Force color off when switching to an OBJ file
  function handleFile(f: File) {
    setFile(f)
    if (getFileType(f) === 'obj' && settings.colorMode === 'glb-color') {
      setSettings({ ...settings, colorMode: 'none' })
    }
  }

  function handleClear() {
    setFile(null)
    resetJob()
  }

  function resetJob() {
    setJobId(null)
    setJobStatus('idle')
    setUploadProgress(0)
  }

  async function handleConvert() {
    if (!file) return
    setJobStatus('uploading')
    setUploadProgress(0)
    try {
      const id = await submitJob(file, settings, setUploadProgress)
      setJobId(id)
      setPipelineStartTime(Date.now())
      setJobStatus('queued')
    } catch {
      setJobStatus('error')
    }
  }

  const busy = jobStatus === 'uploading' || jobStatus === 'queued' || jobStatus === 'running'
  const done = jobStatus === 'done'
  const errored = jobStatus === 'error'

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950 text-gray-900 dark:text-gray-100 flex flex-col">
      <Header />

      <main className="flex-1 flex flex-col items-center gap-4 px-4 py-8 max-w-2xl mx-auto w-full">
        {serverOnline === false && <OfflineBanner />}

        <UploadZone
          file={file}
          onFile={handleFile}
          onClear={handleClear}
          disabled={busy}
        />

        {file && (
          <>
            <SettingsPanel
              settings={settings}
              onChange={setSettings}
              fileType={fileType}
              disabled={busy}
            />

            <ConvertButton
              jobStatus={jobStatus}
              uploadProgress={uploadProgress}
              onClick={handleConvert}
              disabled={busy || done}
            />
          </>
        )}

        {(jobStatus === 'queued' || jobStatus === 'running') && (
          <PipelineProgress
            stage={jobData?.stage ?? 'queued'}
            startTime={pipelineStartTime}
          />
        )}

        {done && jobData?.stats && jobData.outputFilename && (
          <OutputSection
            stats={jobData.stats}
            outputFilename={jobData.outputFilename}
            downloadHref={downloadUrl(jobId!)}
            onStartOver={() => { resetJob(); setFile(null) }}
            onConvertAgain={resetJob}
          />
        )}

        {/* Error */}
        {errored && (
          <div className="w-full bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-xl px-5 py-4 text-sm text-red-800 dark:text-red-200">
            <p className="font-medium">Conversion failed</p>
            <p className="mt-1 text-red-600 dark:text-red-300">
              {jobData?.error ?? 'Upload failed — check that the API server is running'}
            </p>
            <button
              onClick={resetJob}
              className="mt-3 px-4 py-2 border border-red-300 dark:border-red-700 rounded-lg text-sm hover:bg-red-100 dark:hover:bg-red-900 transition-colors"
            >
              Try Again
            </button>
          </div>
        )}
      </main>
    </div>
  )
}
