// -----------------------------------------------------------------------------
// Posts and their comment threads are served by the backend. DTOs are separate
// from the DB model: `responseCount`, `siblingIndex`, `siblingTotal` and the
// `viewerCan*` pair are DERIVED, not columns.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { PagedResponse } from './types'
import type { ActorRefDto } from './profiles'
import type { ReactionSummaryDto } from './reactions'

// ── DTOs ─────────────────────────────────────────────────────────────────────

export interface PostDto {
  id: string
  author: ActorRefDto
  content: string
  createdAt: string
  /** roots + replies */
  commentCount: number
  reactions: ReactionSummaryDto
  /** Author's bio/description, snipped for the post page's rail. */
  authorBio?: string | null
  /** authenticated viewers only; drives the rail's FollowButton */
  authorFollowedByViewer?: boolean
  /** Management authority, computed server-side from the caller's token: the
   *  client cannot derive it, because user-facing DTOs omit `createdBy`
   *  (backend-contract.md §8 rule 7). Two independent booleans, never collapsed
   *  into one - the backend grants them together today, but an ADMIN allowed to
   *  delete a post they may not edit is a plausible split. Absent = false: a
   *  guest, or a viewer with no authority over this item. */
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
}

export interface ThreadCommentDto {
  id: string
  postId: string
  /** null on a root comment */
  parentId: string | null
  author: ActorRefDto
  content: string
  createdAt: string
  reactions: ReactionSummaryDto
  /** direct replies; always 0 when parentId != null */
  responseCount: number
  /** As on `PostDto`: server-computed, mutually independent, absent = false. */
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
}

/** GET /comments/{id}?context=1 */
export interface CommentContextDto {
  comment: ThreadCommentDto
  /** the root, when `comment` is a reply */
  parent: ThreadCommentDto | null
  /** 0-based position among siblings, in the order they are served */
  siblingIndex: number
  siblingTotal: number
}

/** `top` | `new` | `old`. The client omits it for the default and the backend infers. */
export type CommentSort = 'top' | 'new' | 'old'

export interface CommentQuery {
  page?: number
  size?: number
  sort?: CommentSort
  /** One comment id to withhold, so a deep link can render it out of band. */
  exclude?: string
}

export interface CreatePostPayload {
  content: string
  /** The actor this is published as - never omitted, never null on the wire. */
  createdForId: string
}

export interface CreateCommentPayload {
  content: string
  /** The actor this is published as - never omitted, never null on the wire. */
  createdForId: string
  /** Root comment id when this is a reply; depth is capped at 1. */
  parentId?: string | null
}

/** Deleting a root cascades to its replies; the client reconciles counts with this. */
export interface DeleteCommentResultDto {
  deletedCount: number
}

// ── Posts ────────────────────────────────────────────────────────────────────

export const fetchPost = async (postId: string): Promise<PostDto> => {
  const { data } = await api.get<PostDto>(`/posts/${postId}`)
  return data
}

/** A 403 means the caller may not publish as `createdForId`; do not retry under another identity. */
export const createPost = async (payload: CreatePostPayload): Promise<PostDto> => {
  const { data } = await api.post<PostDto>('/posts', payload)
  return data
}

export const updatePost = async (postId: string, content: string): Promise<PostDto> => {
  const { data } = await api.put<PostDto>(`/posts/${postId}`, { content })
  return data
}

export const deletePost = async (postId: string): Promise<void> => {
  await api.delete(`/posts/${postId}`)
}

// ── Comments ─────────────────────────────────────────────────────────────────

/** `sort` is omitted for the default (`top`); `exclude` withholds the root the named comment belongs to. */
export const fetchComments = async (
  postId: string,
  { page = 1, size = 10, sort = 'top', exclude }: CommentQuery = {},
): Promise<PagedResponse<ThreadCommentDto>> => {
  const { data } = await api.get<PagedResponse<ThreadCommentDto>>(`/posts/${postId}/comments`, {
    params: { page, size, sort: sort === 'top' ? undefined : sort, exclude },
  })
  return data
}

/** Always chronological, and takes no `sort`. */
export const fetchResponses = async (
  commentId: string,
  page = 1,
  size = 5,
): Promise<PagedResponse<ThreadCommentDto>> => {
  const { data } = await api.get<PagedResponse<ThreadCommentDto>>(`/comments/${commentId}/responses`, {
    params: { page, size },
  })
  return data
}

export const fetchCommentContext = async (commentId: string): Promise<CommentContextDto> => {
  const { data } = await api.get<CommentContextDto>(`/comments/${commentId}`, { params: { context: 1 } })
  return data
}

/** A 403 means the caller may not publish as `createdForId`; do not retry under another identity. */
export const createComment = async (
  postId: string,
  payload: CreateCommentPayload,
): Promise<ThreadCommentDto> => {
  const { data } = await api.post<ThreadCommentDto>(`/posts/${postId}/comments`, payload)
  return data
}

export const updateComment = async (commentId: string, content: string): Promise<ThreadCommentDto> => {
  const { data } = await api.put<ThreadCommentDto>(`/comments/${commentId}`, { content })
  return data
}

/** Deleting a root takes its replies with it; `deletedCount` says how many rows went. */
export const deleteComment = async (commentId: string): Promise<DeleteCommentResultDto> => {
  const { data } = await api.delete<DeleteCommentResultDto>(`/comments/${commentId}`)
  return data
}
