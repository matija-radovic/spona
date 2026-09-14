import api from '@utils/api'

import type { PagedResponse } from './types'
import type { ActorRefDto } from './profiles'
import type { ReactionSummaryDto } from './reactions'
import type { PostDto } from './posts'

// --- DTOs ---

export type ActivityPostDto = PostDto & { kind: 'post' }

export interface ActivityCommentDto {
  kind: 'comment'
  id: string
  postId: string
  isReply: boolean
  author: ActorRefDto
  content: string
  createdAt: string
  reactions: ReactionSummaryDto
  /** Direct replies, always 0 on a reply */
  responseCount: number
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
}

export type ActivityItemDto = ActivityPostDto | ActivityCommentDto
export type ActivityKind = 'all' | 'posts' | 'comments'

// --- Calls ---

export const fetchActorActivity = async (
  actorId: string,
  kind: ActivityKind,
  page = 1,
  size = 10,
): Promise<PagedResponse<ActivityItemDto>> => {
  const { data } = await api.get<PagedResponse<ActivityItemDto>>(`/actors/${actorId}/activity`, {
    params: { kind, page, size },
  })
  return data
}
