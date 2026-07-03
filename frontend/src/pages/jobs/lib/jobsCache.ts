// Module-level cache keyed by the filter querystring. Lets JobsLayout restore a
// previous search (accumulated pages + scroll position) instead of refetching,
// which is what makes browser Back land on the exact prior result set with no
// render storm (job-page.md §7.1). Lives outside React so it survives unmounts.

import type { JobListItemDto } from '@utils/api/jobs'

export interface JobsCacheEntry {
  items: JobListItemDto[]
  /** last page fetched (1-indexed) so infinite scroll can resume paging */
  page: number
  last: boolean
  totalElements: number
  scrollTop: number
}

const cache = new Map<string, JobsCacheEntry>()

export const getJobsCache = (key: string): JobsCacheEntry | undefined => cache.get(key)

export const setJobsCache = (key: string, entry: JobsCacheEntry): void => {
  cache.set(key, entry)
}

export const hasJobsCache = (key: string): boolean => cache.has(key)

/** Update only the saved scroll offset for a key, if it is cached. */
export const saveJobsScroll = (key: string, scrollTop: number): void => {
  const entry = cache.get(key)
  if (entry) entry.scrollTop = scrollTop
}
