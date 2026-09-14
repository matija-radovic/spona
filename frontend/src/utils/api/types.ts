export interface HalLink {
  href: string
}

export interface PagedResponse<T> {
  _links: {
    self: HalLink
    prev?: HalLink
    next?: HalLink
  }
  content: T[]
  /** 1-indexed */
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/** Error body of a failed request */
export interface ProblemDetail {
  /** Problem type URI */
  type: string
  title: string
  status: number
  detail: string
  /** URI of the failed request */
  instance: string
  [extension: string]: unknown
}

export const isProblemDetail = (x: unknown): x is ProblemDetail => {
  if (typeof x !== 'object' || x === null) return false
  const p = x as Record<string, unknown>
  return typeof p.status === 'number' && typeof p.title === 'string' && typeof p.detail === 'string'
}

/** Normalises any thrown value into a ProblemDetail, with status 0 for a network failure */
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
