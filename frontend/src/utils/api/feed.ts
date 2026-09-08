// -----------------------------------------------------------------------------
// MOCK feed service (`GET /feed`, the signed-in reader's stream). The Spring
// Boot backend does not exist yet, so the call fakes a delayed response; the
// real axios call is left commented above the mock body.
//
// AUTHENTICATED ONLY. The real route answers `401` to a guest and there is no
// public feed and no public form of it. The mock holds no token to check, so
// nothing here enforces that - the `ProtectedRoute` around `/feed` is what
// stands in, and it is a convenience rather than the authorization.
//
// THE RESPONSE IS A STREAM OF DISCRIMINATED BLOCKS, not a list of posts. The
// union has exactly one arm today, `{ kind: 'post', post: PostDto }`, and the
// renderer switches on `kind` with a `default` that renders nothing. That
// default is the whole forward-compatibility mechanism: a deployed backend can
// begin emitting a block an already-loaded tab has never heard of, and the
// reader sees a shorter page instead of a crash. An envelope rather than the
// activity list's flattened `PostDto & { kind }`, because a future block need
// not be an entity at all, and two arms disagreeing about what `id` means is a
// bug that type-checks.
//
// THE ROUTE TAKES `page` AND `size` AND NOTHING ELSE. No `q`, no `sort`, no
// filter of any kind: newest first is the feed's only ordering, and a second one
// would need a second index, a second empty state and a control on a page whose
// whole design is that it has none.
//
// THE ROWS ARE REAL POSTS, borrowed from `posts.ts` through `mockPostRow`, so a
// row in the list and the page it opens are one projection. Two fixture sets
// over one entity is how a list and a page start disagreeing about a post.
//
// Magic input:
//   - MOCK_EMPTY_FEED, a module constant flipped by hand -> an empty page, which
//     is the normal first screen of an account that follows nobody. The route
//     takes no parameter that could select it and must not grow one.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { PagedResponse } from './types'
import { mockPostRow, type PostDto } from './posts'

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

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** Flip by hand to exercise the empty state. */
const MOCK_EMPTY_FEED = false
const MAX_MOCK_PAGES = 3
/** Infinite scroll terminates at 30 rows, which is enough to exercise scroll
 *  restoration properly. */
const MOCK_ROWS = MAX_MOCK_PAGES * FEED_PAGE_SIZE

const hrefFor = (page: number, size: number): string => `/api/feed?page=${page}&size=${size}`

/**
 * MOCK - would be:
 *   const { data } = await api.get('/feed', { params: { page, size } })
 * The row ids are stable, so `fetchPost` on any of them answers with the post
 * this list served.
 */
export const fetchFeed = async (
  page = 1,
  size = FEED_PAGE_SIZE,
): Promise<PagedResponse<FeedBlockDto>> => {
  await delay(430)

  const totalElements = MOCK_EMPTY_FEED ? 0 : MOCK_ROWS
  const totalPages = totalElements === 0 ? 0 : Math.min(Math.ceil(totalElements / size), MAX_MOCK_PAGES)
  const last = page >= totalPages
  const count = totalElements === 0 || page > totalPages ? 0 : last ? totalElements - (page - 1) * size : size

  const content: FeedBlockDto[] = Array.from({ length: count }, (_, i) => ({
    kind: 'post',
    post: mockPostRow(`feed-post-${(page - 1) * size + i}`),
  }))

  return {
    _links: {
      self: { href: hrefFor(page, size) },
      ...(page > 1 ? { prev: { href: hrefFor(page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: hrefFor(page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}
