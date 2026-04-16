import type { ColorInfo } from '../types/api'

interface Props {
  colorInfo: ColorInfo
}

const ALGORITHM_LABELS: Record<string, string> = {
  direct:       'Direct',
  uvlab:        'UV Lab',
  dominant:     'Dominant vote',
  region:       'Region',
  supersampled: 'Supersampled',
}

export default function ColorInfoRow({ colorInfo }: Props) {
  const coveragePct = Math.round((colorInfo.coloredBrickCount / colorInfo.totalBrickCount) * 100)

  return (
    <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl px-5 py-4">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-2 h-2 rounded-full bg-gradient-to-br from-red-400 via-yellow-400 to-blue-400 shrink-0" />
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Color</h3>
      </div>

      <div className="flex flex-wrap gap-x-6 gap-y-1.5 text-xs text-gray-500 dark:text-gray-400">
        <span>
          <span className="font-medium text-gray-700 dark:text-gray-300">
            {colorInfo.coloredBrickCount.toLocaleString()}
          </span>
          {' / '}
          {colorInfo.totalBrickCount.toLocaleString()} bricks colored
          <span className="ml-1 text-gray-400 dark:text-gray-500">({coveragePct}%)</span>
        </span>

        <span>
          <span className="font-medium text-gray-700 dark:text-gray-300">
            {colorInfo.opaquePaletteEntries}
          </span>
          {' palette colors'}
        </span>

        <span>
          <span className="font-medium text-gray-700 dark:text-gray-300">
            {ALGORITHM_LABELS[colorInfo.colorAlgorithm] ?? colorInfo.colorAlgorithm}
          </span>
          {' algorithm'}
        </span>

        {colorInfo.smoothedCount > 0 && (
          <span>
            <span className="font-medium text-gray-700 dark:text-gray-300">
              {colorInfo.smoothedCount.toLocaleString()}
            </span>
            {' smoothed'}
          </span>
        )}
      </div>
    </div>
  )
}
