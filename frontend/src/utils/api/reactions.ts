import api from '@utils/api'

import type { ActorRefDto } from './profiles'

// --- DTOs ---

/** Max entries in ReactionSummaryDto.top, mirrored by the optimistic patch */
export const REACTION_TOP_MAX = 3

export interface ReactionSummaryDto {
  /** Ordered by count, descending */
  top: { emoji: string; count: number }[]
  /** Reactions not covered by top, rendered as +N */
  othersCount: number
  total: number
  /** Authenticated viewers only */
  viewerReactions?: string[]
}

export interface ReactionBreakdownEntryDto {
  emoji: string
  /** Equals reactors.length */
  count: number
  /** Always users */
  reactors: ActorRefDto[]
}

/** Every emoji on a target, neither clamped nor paged */
export interface ReactionBreakdownDto {
  entries: ReactionBreakdownEntryDto[]
  total: number
}

export type ReactionTargetType = 'post' | 'comment'

// --- Calls ---

export const fetchReactionBreakdown = async (
  targetType: ReactionTargetType,
  targetId: string,
): Promise<ReactionBreakdownDto> => {
  const { data } = await api.get<ReactionBreakdownDto>(`/reactions/${targetType}/${targetId}`)
  return data
}

export const setReaction = async (
  targetType: ReactionTargetType,
  targetId: string,
  emoji: string,
  next: boolean,
): Promise<void> => {
  await api({ method: next ? 'put' : 'delete', url: `/reactions/${targetType}/${targetId}`, data: { emoji } })
}
