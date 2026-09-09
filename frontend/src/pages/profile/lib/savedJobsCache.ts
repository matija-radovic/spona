import type { SavedJobItemDto } from '@utils/api/jobs'

export interface SavedJobsCacheEntry {
  /** The saved ids this cache was built from (order-insensitive). */
  ids: string[]
  items: SavedJobItemDto[]
  hydratedOpen: boolean
  hydratedClosed: boolean
}

let entry: SavedJobsCacheEntry | null = null

export const getSavedJobsCache = (): SavedJobsCacheEntry | null => entry

export const setSavedJobsCache = (next: SavedJobsCacheEntry): void => {
  entry = next
}

export const clearSavedJobsCache = (): void => {
  entry = null
}

/** True when two id lists hold the same members, regardless of order. */
export const sameIdSet = (a: string[], b: string[]): boolean =>
  a.length === b.length && a.every((id) => b.includes(id))
