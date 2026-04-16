import type { BrickType } from '../types/api'

interface Props {
  brickTypes: BrickType[]
  totalBricks: number
}

export default function BrickTypeTable({ brickTypes, totalBricks }: Props) {
  if (brickTypes.length === 0) return null

  const max = brickTypes[0].count  // already sorted desc by server

  return (
    <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Brick types</h3>
        <span className="text-xs text-gray-400 dark:text-gray-500">
          {brickTypes.length} unique part{brickTypes.length !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="divide-y divide-gray-50 dark:divide-gray-800/60">
        {brickTypes.map(brick => {
          const pct = Math.round((brick.count / totalBricks) * 100)
          const barWidth = Math.round((brick.count / max) * 100)

          return (
            <div key={brick.partId} className="px-5 py-2.5 flex items-center gap-3">
              {/* Part ID badge */}
              <span className="text-xs font-mono text-gray-400 dark:text-gray-500 w-12 shrink-0 text-right">
                {brick.partId}
              </span>

              {/* Name + bar */}
              <div className="flex-1 min-w-0">
                <p className="text-sm text-gray-700 dark:text-gray-300 truncate leading-tight">
                  {brick.name}
                </p>
                <div className="mt-1 h-1 bg-gray-100 dark:bg-gray-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-blue-400 dark:bg-blue-500 rounded-full"
                    style={{ width: `${barWidth}%` }}
                  />
                </div>
              </div>

              {/* Count + pct */}
              <div className="text-right shrink-0">
                <p className="text-sm font-medium text-gray-800 dark:text-gray-200 tabular-nums">
                  {brick.count.toLocaleString()}
                </p>
                <p className="text-xs text-gray-400 dark:text-gray-500 tabular-nums">{pct}%</p>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
