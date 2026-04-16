import type { Settings, Algorithm, OutputType, ColorMode, ColorAlgorithm } from '../types/api'
import type { FileType } from '../utils/fileHelpers'

interface Props {
  settings: Settings
  onChange: (settings: Settings) => void
  fileType: FileType | null
  disabled: boolean
}

function Label({ htmlFor, children }: { htmlFor: string; children: React.ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="text-sm font-medium text-gray-700 dark:text-gray-300 block mb-1.5">
      {children}
    </label>
  )
}

function Select<T extends string>({
  id, value, onChange, options, disabled,
}: {
  id: string
  value: T
  onChange: (v: T) => void
  options: { value: T; label: string; description?: string }[]
  disabled: boolean
}) {
  return (
    <select
      id={id}
      value={value}
      disabled={disabled}
      onChange={e => onChange(e.target.value as T)}
      className="w-full text-sm px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500"
    >
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

const OUTPUT_TYPE_OPTIONS: { value: OutputType; label: string }[] = [
  { value: 'ldraw',                label: 'LDraw (.ldr) — BrickLink Studio' },
  { value: 'brick',                label: 'Brick OBJ — visual mesh' },
  { value: 'voxel-surface',        label: 'Voxel OBJ — surface shell' },
  { value: 'voxel-solid',          label: 'Voxel OBJ — solid volume' },
  { value: 'voxel-slope-surface',  label: 'Voxel OBJ — slope surface' },
  { value: 'voxel-surface-combined', label: 'Voxel OBJ — surface + slopes' },
  { value: 'voxel-slope-placed',   label: 'Voxel OBJ — placed slopes' },
]

const COLOR_ALGORITHM_OPTIONS: { value: ColorAlgorithm; label: string; description: string }[] = [
  { value: 'direct',       label: 'Direct',       description: 'Nearest palette match by ΔE' },
  { value: 'uvlab',        label: 'UV Lab',        description: 'Shadow lifting + chroma stabilization' },
  { value: 'dominant',     label: 'Dominant vote', description: 'Per-voxel palette voting' },
  { value: 'region',       label: 'Region',        description: 'Flood-fill regions, majority vote' },
  { value: 'supersampled', label: 'Supersampled',  description: 'Multi-sample color averaging' },
]

export default function SettingsPanel({ settings, onChange, fileType, disabled }: Props) {
  const isObj = fileType === 'obj'
  const showColorAlgorithm = settings.colorMode === 'glb-color' && fileType === 'glb'

  function set<K extends keyof Settings>(key: K, value: Settings[K]) {
    onChange({ ...settings, [key]: value })
  }

  return (
    <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-5 space-y-5">
      <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Settings</h2>

      <div className="grid grid-cols-2 gap-4">
        {/* Resolution */}
        <div>
          <Label htmlFor="resolution">Resolution</Label>
          <input
            id="resolution"
            type="number"
            min={2}
            max={200}
            value={settings.resolution}
            disabled={disabled}
            onChange={e => {
              const v = parseInt(e.target.value, 10)
              if (!isNaN(v)) set('resolution', v)
            }}
            onBlur={e => {
              const v = parseInt(e.target.value, 10)
              if (isNaN(v) || v < 2) set('resolution', 2)
            }}
            className="w-full text-sm px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">Voxel grid size · min 2</p>
        </div>

        {/* Algorithm */}
        <div>
          <Label htmlFor="algorithm">Voxelizer</Label>
          <Select<Algorithm>
            id="algorithm"
            value={settings.algorithm}
            onChange={v => set('algorithm', v)}
            disabled={disabled}
            options={[
              { value: 'topological', label: 'Topological (default)' },
              { value: 'legacy',      label: 'Legacy' },
            ]}
          />
        </div>
      </div>

      {/* Output type */}
      <div>
        <Label htmlFor="outputType">Output format</Label>
        <Select<OutputType>
          id="outputType"
          value={settings.outputType}
          onChange={v => set('outputType', v)}
          disabled={disabled}
          options={OUTPUT_TYPE_OPTIONS}
        />
      </div>

      {/* Color mode */}
      <div>
        <Label htmlFor="colorMode">Color mode</Label>
        <div className="flex gap-2">
          {(['glb-color', 'none'] as ColorMode[]).map(mode => {
            const active = settings.colorMode === mode
            const lockedOff = isObj && mode === 'glb-color'
            return (
              <button
                key={mode}
                type="button"
                disabled={disabled || lockedOff}
                onClick={() => set('colorMode', mode)}
                title={lockedOff ? '.obj files have no color channel' : undefined}
                className={`flex-1 px-3 py-2 rounded-lg border text-sm font-medium transition-colors
                  ${active
                    ? 'bg-blue-600 border-blue-600 text-white'
                    : 'bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750'
                  }
                  disabled:opacity-40 disabled:cursor-not-allowed`}
              >
                {mode === 'glb-color' ? 'From model' : 'None'}
              </button>
            )
          })}
        </div>
        {isObj && (
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
            OBJ files have no color channel
          </p>
        )}
      </div>

      {/* Color algorithm — only when glb-color + glb */}
      {showColorAlgorithm && (
        <div>
          <Label htmlFor="colorAlgorithm">Color algorithm</Label>
          <Select<ColorAlgorithm>
            id="colorAlgorithm"
            value={settings.colorAlgorithm}
            onChange={v => set('colorAlgorithm', v)}
            disabled={disabled}
            options={COLOR_ALGORITHM_OPTIONS.map(o => ({
              value: o.value,
              label: `${o.label} — ${o.description}`,
            }))}
          />
        </div>
      )}
    </div>
  )
}
