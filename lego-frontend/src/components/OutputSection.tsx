import type { JobStats } from '../types/api'
import StatsCard from './StatsCard'
import BrickTypeTable from './BrickTypeTable'
import ColorInfoRow from './ColorInfoRow'

interface Props {
  stats: JobStats
  outputFilename: string
  downloadHref: string
  onStartOver: () => void
  onConvertAgain: () => void
}

export default function OutputSection({
  stats,
  outputFilename,
  downloadHref,
  onStartOver,
  onConvertAgain,
}: Props) {
  return (
    <div className="w-full flex flex-col gap-4">
      <StatsCard stats={stats} />

      {stats.colorInfo && <ColorInfoRow colorInfo={stats.colorInfo} />}

      <BrickTypeTable brickTypes={stats.brickTypes} totalBricks={stats.brickCount} />

      {/* Actions */}
      <div className="flex gap-3">
        <a
          href={downloadHref}
          download={outputFilename}
          className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-sm font-medium transition-colors"
        >
          <svg
            className="w-4 h-4 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="7 10 12 15 17 10" />
            <line x1="12" y1="15" x2="12" y2="3" />
          </svg>
          Download {outputFilename}
        </a>

        <button
          onClick={onConvertAgain}
          className="py-2.5 px-4 rounded-lg border border-gray-300 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
          title="Convert again with the same file and settings"
        >
          Convert Again
        </button>

        <button
          onClick={onStartOver}
          className="py-2.5 px-4 rounded-lg border border-gray-300 dark:border-gray-700 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
          title="Clear file and start from scratch"
        >
          Start Over
        </button>
      </div>
    </div>
  )
}
