import api from '@utils/api'

import type { PagedResponse } from './types'
import type { PostDto } from './posts'

// --- DTOs ---

export interface FeedPostBlockDto {
  kind: 'post'
  post: PostDto
}

/** Every block kind the feed can serve */
export type FeedBlockDto = FeedPostBlockDto

// --- Calls ---

export const FEED_PAGE_SIZE = 10

export const fetchFeed = async (
  page = 1,
  size = FEED_PAGE_SIZE,
): Promise<PagedResponse<FeedBlockDto>> => {
  const { data } = await api.get<PagedResponse<FeedBlockDto>>('/feed', { params: { page, size } })
  return data
}
