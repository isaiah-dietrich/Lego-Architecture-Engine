import { useState } from 'react'

/**
 * Works like useState but persists the value in localStorage.
 * Falls back to the initial value if the key doesn't exist or can't be parsed.
 */
export function useLocalStorage<T>(key: string, initialValue: T): [T, (value: T) => void] {
  const [stored, setStored] = useState<T>(() => {
    try {
      const raw = localStorage.getItem(key)
      return raw !== null ? (JSON.parse(raw) as T) : initialValue
    } catch {
      return initialValue
    }
  })

  const setValue = (value: T) => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch {
      // ignore quota errors
    }
    setStored(value)
  }

  return [stored, setValue]
}
