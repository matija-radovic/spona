// Module-level cache for the profile's Job-postings pane, keyed
// `actorId::status::sort` - one entry per list the server can be asked for, in
// the shape of activityCache. Lives outside React so accumulated pages survive
// the pane unmounting on a tab switch.

import type { JobPostingsSort, SavedJobItemDto } from '@utils/api/jobs'

export interface JobPostingsCacheEntry {
  items: SavedJobItemDto[]
  /** last page fetched (1-indexed) so infinite scroll can resume paging */
  page: number
  last: boolean
}

const cache = new Map<string, JobPostingsCacheEntry>()

/** `status` is empty for "all" and for the public shape, which asks for none. */
export const jobPostingsCacheKey = (
  actorId: string,
  status: string,
  sort: JobPostingsSort | '',
): string => `${actorId}::${status}::${sort}`

export const getJobPostingsCache = (key: string): JobPostingsCacheEntry | undefined => cache.get(key)

export const setJobPostingsCache = (key: string, entry: JobPostingsCacheEntry): void => {
  cache.set(key, entry)
}
