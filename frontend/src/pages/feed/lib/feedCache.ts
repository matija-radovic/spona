import type { FeedBlockDto } from '@utils/api/feed'

export interface FeedCacheEntry {
  blocks: FeedBlockDto[]
  /** last page fetched */
  page: number
  last: boolean
  scrollTop: number
}

const cache = new Map<string, FeedCacheEntry>()

export const getFeedCache = (userId: string): FeedCacheEntry | undefined => cache.get(userId)

export const setFeedCache = (userId: string, entry: FeedCacheEntry): void => {
  cache.set(userId, entry)
}

export const saveFeedScroll = (userId: string, scrollTop: number): void => {
  const entry = cache.get(userId)
  if (entry) entry.scrollTop = scrollTop
}

export const clearFeedCache = (): void => {
  cache.clear()
}
