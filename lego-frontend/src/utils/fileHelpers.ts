export type FileType = 'obj' | 'glb'

export function getFileType(file: File): FileType | null {
  const name = file.name.toLowerCase()
  if (name.endsWith('.obj')) return 'obj'
  if (name.endsWith('.glb')) return 'glb'
  return null
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
