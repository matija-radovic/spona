// -----------------------------------------------------------------------------
// The signed-in reader's feed is served by the backend. `GET /feed` answers
// `401` to a guest and takes `page` and `size` and nothing else: newest first
// is the feed's only ordering.
//
// The response is a stream of discriminated blocks, not a list of posts. The
// union has exactly one arm today, `{ kind: 'post', post: PostDto }`, and the
// renderer switches on `kind` with a `default` that renders nothing, so a block
// an already-loaded tab has never heard of makes a shorter page instead of a
// crash. A block's `post` is the same projection `GET /posts/{postId}` serves.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { PagedResponse } from './types'
import type { PostDto } from './posts'

// ── DTOs ─────────────────────────────────────────────────────────────────────

/** One block in the feed stream. The union has exactly one arm today. */
export interface FeedPostBlockDto {
  kind: 'post'
  post: PostDto
}

export type FeedBlockDto = FeedPostBlockDto

/** Exported from the service and not from the page's cache module: that module
 *  imports this file's DTO, and the reverse direction would be a `utils/` module
 *  importing `pages/`. */
export const FEED_PAGE_SIZE = 10

// ── Feed ─────────────────────────────────────────────────────────────────────

/** An account that follows nobody gets an empty page, never a 404. */
export const fetchFeed = async (
  page = 1,
  size = FEED_PAGE_SIZE,
): Promise<PagedResponse<FeedBlockDto>> => {
  const { data } = await api.get<PagedResponse<FeedBlockDto>>('/feed', { params: { page, size } })
  return data
}
