// -----------------------------------------------------------------------------
// Reactions are served by the backend. Posts, comments and the profile Activity
// pane all read the summary and all write through `setReaction`.
//
// `fetchReactionBreakdown` is the one read in this folder that is not paged: it
// takes no `page`, no `size`, no `emoji` and answers no envelope, because the
// response is bounded by one target's own reactions.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ActorRefDto } from './profiles'

// ── DTOs ─────────────────────────────────────────────────────────────────────

/** How many entries `ReactionSummaryDto.top` carries at most. Exported because
 *  the client's optimistic patch has to apply the same clamp the server does, or
 *  a reaction lands in "+N" while there is still room beside it. */
export const REACTION_TOP_MAX = 3

export interface ReactionSummaryDto {
  /** Up to `REACTION_TOP_MAX` entries, ordered by count desc. */
  top: { emoji: string; count: number }[]
  /** Total count of reactions NOT covered by `top` - renders as "+N". */
  othersCount: number
  total: number
  /** Emojis the current viewer has used on this target (authenticated only). */
  viewerReactions?: string[]
}

/** One emoji's row in the breakdown, with the people behind it. */
export interface ReactionBreakdownEntryDto {
  emoji: string
  /** reactors.length. On the wire because a client may render the lane before the rows. */
  count: number
  /** Always users: a reaction has no acting identity to be taken as. */
  reactors: ActorRefDto[]
}

/** Every distinct emoji on a target, count desc. Not clamped and not paged: this
 *  is the "+N" destination, and the reader is asking for the whole picture. */
export interface ReactionBreakdownDto {
  entries: ReactionBreakdownEntryDto[]
  total: number
}

/** The two rows `ReactionTargetTypes` is seeded with. Jobs are not reactable. */
export type ReactionTargetType = 'post' | 'comment'

// ── Reads ────────────────────────────────────────────────────────────────────

/** The whole breakdown in one request: no `emoji` parameter and no `PagedResponse`. */
export const fetchReactionBreakdown = async (
  targetType: ReactionTargetType,
  targetId: string,
): Promise<ReactionBreakdownDto> => {
  const { data } = await api.get<ReactionBreakdownDto>(`/reactions/${targetType}/${targetId}`)
  return data
}

// ── The write ────────────────────────────────────────────────────────────────

/** Adds or removes the viewer's reaction; both verbs are idempotent. */
export const setReaction = async (
  targetType: ReactionTargetType,
  targetId: string,
  emoji: string,
  next: boolean,
): Promise<void> => {
  await api({ method: next ? 'put' : 'delete', url: `/reactions/${targetType}/${targetId}`, data: { emoji } })
}
