import type { JobStatus } from '../types/api'

interface Props {
  jobStatus: JobStatus
  uploadProgress: number
  onClick: () => void
  disabled: boolean
}

export default function ConvertButton({ jobStatus, uploadProgress, onClick, disabled }: Props) {
  if (jobStatus === 'uploading') {
    return (
      <div className="w-full">
        <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400 mb-1.5">
          <span>Uploading…</span>
          <span>{uploadProgress}%</span>
        </div>
        <div className="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            className="h-full bg-blue-600 rounded-full transition-[width] duration-150"
            style={{ width: `${uploadProgress}%` }}
          />
        </div>
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="w-full py-2.5 px-4 rounded-lg bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
    >
      Convert
    </button>
  )
}
