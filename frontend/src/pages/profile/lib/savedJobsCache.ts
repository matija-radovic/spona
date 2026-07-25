// Module-level cache for the Saved-jobs pane. There is only ever one saved set
// (the viewer's own), so a single entry suffices. It records which id set it was
// built from and which statuses have been hydrated, so tab switches never
// refetch - but the pane invalidates it on mount when the saved set changed
// (§10.3). Lives outside React so it survives the pane unmounting.

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
