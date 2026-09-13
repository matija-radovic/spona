import api from '@utils/api'

import type { PagedResponse } from './types'
import type { ActorRefDto } from './profiles'
import type { ReactionSummaryDto } from './reactions'
import type { PostDto } from './posts'

// --- DTOs ---

/** A post in an activity feed is the post, discriminated - one shape, so the
 *  feed row and the post page cannot drift apart. */
export type ActivityPostDto = PostDto & { kind: 'post' }

export interface ActivityCommentDto {
  kind: 'comment'
  id: string
  postId: string
  /** responseToCommentId != null - replies get the "replied to a comment"
   *  heading and NO comment-jump button. */
  isReply: boolean
  author: ActorRefDto
  content: string
  createdAt: string
  reactions: ReactionSummaryDto
  /** Direct replies; always 0 on a reply, because the depth cap makes it
   *  structurally so. Carried here too so the delete confirmation can say how
   *  many replies go with the comment - a cascade the reader must be told about
   *  before it happens. */
  responseCount: number
  /** As on `PostDto`: server-computed, mutually independent, and absent
   *  = false. */
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
