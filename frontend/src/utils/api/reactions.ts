// -----------------------------------------------------------------------------
// MOCK reactions service. The Spring Boot backend does not exist yet, so the
// call fakes a delayed response; the real axios call is left commented above the
// mock body. Rejections are `ProblemDetail` shapes so callers can
// `parseProblem(err)`.
//
// Reactions are their own domain rather than a corner of one: posts, comments
// and the profile Activity pane all read the summary and all write through this
// one call.
//
// Magic inputs:
//   - setReaction : empty targetId -> 400 Bad Request
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ProblemDetail } from './types'

// ── DTOs ─────────────────────────────────────────────────────────────────────

export interface ReactionSummaryDto {
  /** Up to 3 entries, ordered by count desc. */
  top: { emoji: string; count: number }[]
  /** Total count of reactions NOT covered by `top` - renders as "+N". */
  othersCount: number
  total: number
  /** Emojis the current viewer has used on this target (authenticated only). */
  viewerReactions?: string[]
}

/** The two rows `ReactionTargetTypes` is seeded with. Jobs are not reactable. */
export type ReactionTargetType = 'post' | 'comment'

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/reactions',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

/**
 * MOCK. Adds or removes the viewer's reaction (one user x one emoji x one target,
 * per the Reaction unique constraint). Real call switches verb on `next`:
 *   await api({ method: next ? 'put' : 'delete',
 *               url: `/reactions/${targetType}/${targetId}`, data: { emoji } })
 */
export const setReaction = async (
  targetType: ReactionTargetType,
  targetId: string,
  emoji: string,
  next: boolean,
): Promise<void> => {
  void targetType
  void emoji
  void next
  await delay(200)
  if (!targetId) return rejectProblem(400, 'Nedostaje identifikator sadržaja.', 'Bad Request')
}
