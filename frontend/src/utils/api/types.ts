// -----------------------------------------------------------------------------
// Shared backend transport shapes + helpers. Mirrors the transport envelopes at
// the end of new_docx/types.ts verbatim.
//
// These are app-wide, not jobs-specific: the same envelopes back companies/feed
// later. Three response scenarios the (future) Spring Boot API uses:
//   1. SINGLE entry  -> the bare DTO, no wrapper (endpoints just return `T`).
//   2. PAGINATED     -> `PagedResponse<T>` (Spring Data Page + HAL links).
//   3. ERROR         -> `ProblemDetail` (RFC-7807), arriving as error.response.data.
// -----------------------------------------------------------------------------

/** HAL-style link. */
export interface HalLink {
  href: string
}

/**
 * Paginated envelope. `_links.prev` is absent on the first page; `_links.next`
 * on the last. Each href embeds the whole query string ordered: page, size,
 * then the rest. `last === true` => stop infinite scroll.
 */
export interface PagedResponse<T> {
  _links: {
    self: HalLink
    prev?: HalLink
    next?: HalLink
  }
  content: T[]
  /** current page, 1-indexed */
  page: number
  /** current page size */
  size: number
  /** total rows matching the query — use for "N oglasa pronađeno" */
  totalElements: number
  totalPages: number
  /** derived: page >= totalPages -> no more pages */
  last: boolean
}

/**
 * RFC-7807 / Spring Boot error body. Spring may attach extension members (field
 * errors etc.), hence the index signature.
 */
export interface ProblemDetail {
  /** problem-type URI; defaults to "about:blank" */
  type: string
  /** short human-readable summary of the status, e.g. "Bad Request" */
  title: string
  /** HTTP status code */
  status: number
  /** explanation specific to this occurrence */
  detail: string
  /** URI of the request that failed */
  instance: string
  [extension: string]: unknown
}

/** Narrowing guard for a raw `ProblemDetail` (a rejected value or axios body). */
export const isProblemDetail = (x: unknown): x is ProblemDetail => {
  if (typeof x !== 'object' || x === null) return false
  const p = x as Record<string, unknown>
  return typeof p.status === 'number' && typeof p.title === 'string' && typeof p.detail === 'string'
}

/**
 * Normalises any thrown value into a `ProblemDetail` so callers handle failures
 * uniformly. Order: a raw ProblemDetail (our mock rejects with these) -> the
 * axios `error.response.data` -> a synthesized network problem (status 0).
 */
export const parseProblem = (error: unknown): ProblemDetail => {
  if (isProblemDetail(error)) return error

  const response = (error as { response?: { data?: unknown; status?: number } } | undefined)?.response
  if (isProblemDetail(response?.data)) return response.data

  return {
    type: 'about:blank',
    title: '',
    status: response?.status ?? 0,
    detail: '',
    instance: '',
  }
}
