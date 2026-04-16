import { useEffect, useState } from 'react'

interface Props {
  stage: string
  startTime: number  // epoch ms — when the job was submitted
}

const STAGE_LABELS: Record<string, string> = {
  queued:         'Waiting to start',
  loading:        'Loading model',
  voxelizing:     'Voxelizing geometry',
  placing_bricks: 'Placing bricks',
  colorizing:     'Colorizing',
  exporting:      'Exporting',
  complete:       'Complete',
}

function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatElapsed(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}m ${s.toString().padStart(2, '0')}s`
}

// Ordered stages for the progress track — maps position to a fraction 0–1
const STAGE_ORDER = ['queued', 'loading', 'voxelizing', 'placing_bricks', 'colorizing', 'exporting']

function stageProgress(stage: string): number {
  const idx = STAGE_ORDER.indexOf(stage)
  if (idx === -1) return 0.15  // unknown stage: show a little progress
  return (idx + 1) / STAGE_ORDER.length
}

export default function PipelineProgress({ stage, startTime }: Props) {
  const [elapsed, setElapsed] = useState(() => Math.floor((Date.now() - startTime) / 1000))

  useEffect(() => {
    const id = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startTime) / 1000))
    }, 1000)
    return () => clearInterval(id)
  }, [startTime])

  const progress = stageProgress(stage)

  return (
    <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl px-5 py-5 space-y-4">

      {/* Stage row */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          {/* Spinner */}
          <svg
            className="w-4 h-4 text-blue-500 animate-spin shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <circle
              className="opacity-25"
              cx="12" cy="12" r="10"
              stroke="currentColor"
              strokeWidth="3"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          <span className="text-sm font-medium text-gray-800 dark:text-gray-200">
            {stageLabel(stage)}
          </span>
        </div>

        {/* Elapsed */}
        <span className="text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0 tabular-nums">
          {formatElapsed(elapsed)}
        </span>
      </div>

      {/* Progress track */}
      <div className="w-full h-1 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500 rounded-full transition-[width] duration-700 ease-out"
          style={{ width: `${Math.round(progress * 100)}%` }}
        />
      </div>

      {/* Stage breadcrumb */}
      <div className="flex items-center gap-1 overflow-x-auto">
        {STAGE_ORDER.map((s, i) => {
          const currentIdx = STAGE_ORDER.indexOf(stage)
          const isPast    = i < currentIdx
          const isCurrent = i === currentIdx
          return (
            <span key={s} className="flex items-center gap-1 shrink-0">
              <span
                className={`text-xs transition-colors ${
                  isCurrent
                    ? 'text-blue-600 dark:text-blue-400 font-medium'
                    : isPast
                      ? 'text-gray-400 dark:text-gray-600'
                      : 'text-gray-300 dark:text-gray-700'
                }`}
              >
                {stageLabel(s)}
              </span>
              {i < STAGE_ORDER.length - 1 && (
                <span className={`text-xs ${isPast ? 'text-gray-300 dark:text-gray-700' : 'text-gray-200 dark:text-gray-800'}`}>
                  ›
                </span>
              )}
            </span>
          )
        })}
      </div>
    </div>
  )
}
