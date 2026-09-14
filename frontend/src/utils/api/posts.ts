import api from '@utils/api'

import type { PagedResponse } from './types'
import type { ActorRefDto } from './profiles'
import type { ReactionSummaryDto } from './reactions'

// --- DTOs ---

export interface PostDto {
  id: string
  author: ActorRefDto
  content: string
  createdAt: string
  /** Includes replies */
  commentCount: number
  reactions: ReactionSummaryDto
  /** Truncated for the post page's rail */
  authorBio?: string | null
  /** Authenticated viewers only */
  authorFollowedByViewer?: boolean
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
}

export interface ThreadCommentDto {
  id: string
  postId: string
  /** Null on a root comment */
  parentId: string | null
  author: ActorRefDto
  content: string
  createdAt: string
  reactions: ReactionSummaryDto
  /** Direct replies, always 0 on a reply */
  responseCount: number
  viewerCanEdit?: boolean
  viewerCanDelete?: boolean
}

export interface CommentContextDto {
  comment: ThreadCommentDto
  /** The root, when comment is a reply */
  parent: ThreadCommentDto | null
  /** 0-based position among the siblings */
  siblingIndex: number
  siblingTotal: number
}

export type CommentSort = 'top' | 'new' | 'old'

export interface CommentQuery {
  page?: number
  size?: number
  sort?: CommentSort
  /** Comment id to leave out, rendered separately by a deep link */
  exclude?: string
}

export interface CreatePostPayload {
  content: string
  /** The actor this is published as */
  createdForId: string
}

export interface CreateCommentPayload {
  content: string
  /** The actor this is published as */
  createdForId: string
  /** Root comment id for a reply, replies are one level deep */
  parentId?: string | null
}

export interface DeleteCommentResultDto {
  /** Includes the replies deleted with a root */
  deletedCount: number
}

// --- Posts ---

export const fetchPost = async (postId: string): Promise<PostDto> => {
  const { data } = await api.get<PostDto>(`/posts/${postId}`)
  return data
}

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

// --- Comments ---

export const fetchComments = async (
  postId: string,
  { page = 1, size = 10, sort = 'top', exclude }: CommentQuery = {},
): Promise<PagedResponse<ThreadCommentDto>> => {
  const { data } = await api.get<PagedResponse<ThreadCommentDto>>(`/posts/${postId}/comments`, {
    params: { page, size, sort: sort === 'top' ? undefined : sort, exclude },
  })
  return data
}

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

export const deleteComment = async (commentId: string): Promise<DeleteCommentResultDto> => {
  const { data } = await api.delete<DeleteCommentResultDto>(`/comments/${commentId}`)
  return data
}
