import type { CommentSort, ThreadCommentDto } from '@utils/api/posts'

export const COMMENTS_PAGE_SIZE = 10
export const DEFAULT_COMMENT_SORT: CommentSort = 'top'

export interface CommentsCacheEntry {
  items: ThreadCommentDto[]
  /** last page fetched (1-indexed) so infinite scroll can resume paging */
  page: number
  last: boolean
  totalElements: number
}

const cache = new Map<string, CommentsCacheEntry>()

export const commentsCacheKey = (postId: string, sort: CommentSort): string => `${postId}::${sort}`

export const getCommentsCache = (key: string): CommentsCacheEntry | undefined => cache.get(key)

export const setCommentsCache = (key: string, entry: CommentsCacheEntry): void => {
  cache.set(key, entry)
}

/** Drops every sort's list for one post. */
export const evictPostComments = (postId: string): void => {
  for (const key of [...cache.keys()]) {
    if (key.startsWith(`${postId}::`)) cache.delete(key)
  }
}
