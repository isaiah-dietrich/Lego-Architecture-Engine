import { useRef, useState } from 'react'
import { getFileType, formatBytes } from '../utils/fileHelpers'

interface Props {
  file: File | null
  onFile: (file: File) => void
  onClear: () => void
  disabled: boolean
}

export default function UploadZone({ file, onFile, onClear, disabled }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  function accept(candidate: File) {
    const type = getFileType(candidate)
    if (!type) return
    onFile(candidate)
  }

  function onDragOver(e: React.DragEvent) {
    e.preventDefault()
    if (!disabled) setDragging(true)
  }

  function onDragLeave(e: React.DragEvent) {
    if (!e.currentTarget.contains(e.relatedTarget as Node)) setDragging(false)
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    if (disabled) return
    const f = e.dataTransfer.files[0]
    if (f) accept(f)
  }

  function onInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (f) accept(f)
    e.target.value = ''
  }

  const fileType = file ? getFileType(file) : null

  const borderColor = dragging
    ? 'border-blue-500 dark:border-blue-400'
    : file
      ? 'border-gray-300 dark:border-gray-700'
      : 'border-gray-300 dark:border-gray-700 hover:border-gray-400 dark:hover:border-gray-600'

  const bgColor = dragging
    ? 'bg-blue-50 dark:bg-blue-950'
    : 'bg-white dark:bg-gray-900'

  return (
    <div
      className={`w-full border-2 border-dashed rounded-xl transition-colors ${borderColor} ${bgColor} ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      {file ? (
        /* ── Accepted file display ── */
        <div className="flex items-center gap-4 px-5 py-4">
          <div className="flex items-center justify-center w-10 h-10 rounded-lg bg-gray-100 dark:bg-gray-800 shrink-0">
            <svg className="w-5 h-5 text-gray-500 dark:text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{file.name}</p>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              {formatBytes(file.size)}
              <span className="ml-2 uppercase font-mono text-xs px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300">
                {fileType}
              </span>
            </p>
          </div>
          {!disabled && (
            <button
              onClick={onClear}
              className="shrink-0 w-7 h-7 flex items-center justify-center rounded-md text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              aria-label="Remove file"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>
      ) : (
        /* ── Empty drop zone ── */
        <button
          type="button"
          className="w-full px-6 py-10 flex flex-col items-center gap-3 cursor-pointer disabled:cursor-not-allowed"
          onClick={() => !disabled && inputRef.current?.click()}
          disabled={disabled}
        >
          <div className="w-10 h-10 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
            <svg className="w-5 h-5 text-gray-400 dark:text-gray-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
          </div>
          <div className="text-center">
            <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
              {dragging ? 'Drop to upload' : 'Drop a file or click to browse'}
            </p>
            <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">.obj or .glb</p>
          </div>
        </button>
      )}

      <input
        ref={inputRef}
        type="file"
        accept=".obj,.glb"
        className="sr-only"
        onChange={onInputChange}
        disabled={disabled}
        tabIndex={-1}
      />
    </div>
  )
}
