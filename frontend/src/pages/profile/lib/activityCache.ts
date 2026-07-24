// Module-level cache for the Activity pane, keyed `actorId::kind`. Lets tab flips
// (and Activity <-> other panes) reuse accumulated pages instead of refetching -
// the same discipline as jobsCache. Optimistic reaction updates write through
// here so a round-trip does not resurrect stale counts. Lives outside React so
// it survives the pane unmounting on tab switch.

import type { ActivityItemDto } from '@utils/api/activity'

export interface ActivityCacheEntry {
  items: ActivityItemDto[]
  /** last page fetched (1-indexed) so infinite scroll can resume paging */
  page: number
  last: boolean
  totalElements: number
}

const cache = new Map<string, ActivityCacheEntry>()

export const activityCacheKey = (actorId: string, kind: string): string => `${actorId}::${kind}`

export const getActivityCache = (key: string): ActivityCacheEntry | undefined => cache.get(key)

export const setActivityCache = (key: string, entry: ActivityCacheEntry): void => {
  cache.set(key, entry)
}
