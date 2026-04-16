import type { JobStats } from '../types/api'

interface Props {
  stats: JobStats
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-gray-400 dark:text-gray-500 uppercase tracking-wide font-medium">
        {label}
      </span>
      <span className="text-lg font-semibold text-gray-900 dark:text-gray-100 tabular-nums">
        {value}
      </span>
      {sub && (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
    </div>
  )
}

function Arrow() {
  return (
    <svg
      className="w-4 h-4 text-gray-300 dark:text-gray-700 shrink-0 self-center mt-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12h14M12 5l7 7-7 7" />
    </svg>
  )
}

export default function StatsCard({ stats }: Props) {
  const reductionColor =
    stats.reductionPercent >= 50
      ? 'text-emerald-600 dark:text-emerald-400'
      : stats.reductionPercent >= 25
        ? 'text-amber-600 dark:text-amber-400'
        : 'text-gray-500 dark:text-gray-400'

  return (
    <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl px-5 py-5">
      <div className="flex items-start justify-between mb-4">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Result</h3>
        <span className={`text-sm font-semibold tabular-nums ${reductionColor}`}>
          {stats.reductionPercent}% reduction
        </span>
      </div>

      {/* Pipeline flow */}
      <div className="flex items-start gap-2 overflow-x-auto pb-1">
        <Stat
          label="Triangles"
          value={stats.triangleCount.toLocaleString()}
          sub="input mesh"
        />
        <Arrow />
        <Stat
          label="Voxels"
          value={stats.surfaceVoxels.toLocaleString()}
          sub={`of ${stats.totalVoxels.toLocaleString()} total`}
        />
        <Arrow />
        <Stat
          label="Bricks"
          value={stats.brickCount.toLocaleString()}
          sub={stats.placementPolicy}
        />
      </div>

      {/* Secondary row */}
      <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-800 flex gap-6 text-xs text-gray-400 dark:text-gray-500">
        <span>Resolution <span className="font-mono text-gray-600 dark:text-gray-300">{stats.resolution}³</span></span>
        <span>Solid voxels <span className="font-mono text-gray-600 dark:text-gray-300">{stats.solidVoxels.toLocaleString()}</span></span>
      </div>
    </div>
  )
}
