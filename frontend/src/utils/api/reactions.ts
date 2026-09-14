// -----------------------------------------------------------------------------
// Reactions are served by the backend. Posts, comments and the profile Activity
// pane all read the summary and all write through `setReaction`.
//
// `fetchReactionBreakdown` is the one read in this folder that is not paged: it
// takes no `page`, no `size`, no `emoji` and answers no envelope, because the
// response is bounded by one target's own reactions.
//
// `summaryFor` and its seeded roster are the one mock left in this file: the
// feed mock stamps its borrowed post rows with them.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import { REACTION_EMOJIS } from '@utils/constants/reactions'
import type { ActorRefDto } from './profiles'
import { PEOPLE, REACTOR_POOL, VIEWER, personRef } from './mockActors'

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

// ── Mock plumbing ────────────────────────────────────────────────────────────

/** Cheap stable hash so a given target always seeds the same roster. */
const hashString = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash
}

// ── The roster ───────────────────────────────────────────────────────────────

interface ReactionRoster {
  /** Ordered count desc, then palette index, so ties are stable across calls. */
  entries: { emoji: string; reactors: ActorRefDto[] }[]
}

const rosters = new Map<string, ReactionRoster>()

const rosterKey = (targetType: ReactionTargetType, targetId: string): string =>
  `${targetType}:${targetId}`

const paletteIndex = (emoji: string): number => REACTION_EMOJIS.indexOf(emoji)

/** Most reacted first, which is the order the lane renders and the order
 *  `summaryFor` slices `top` off. The tie break is not decoration: two emojis on
 *  equal counts that swap places between two calls look like the data moved. */
const byCountThenPalette = (
  a: { emoji: string; reactors: ActorRefDto[] },
  b: { emoji: string; reactors: ActorRefDto[] },
): number => b.reactors.length - a.reactors.length || paletteIndex(a.emoji) - paletteIndex(b.emoji)

/** The viewer is excluded so `viewerReactions` stays something the roster
 *  answers rather than something asserted beside it: they are spliced into one
 *  entry below, and nowhere else. */
const KNOWN_REACTORS: readonly ActorRefDto[] = PEOPLE.filter((row) => row.id !== VIEWER.id).map(
  (row) => personRef(row.username),
)

const rotate = <T>(rows: readonly T[], by: number): T[] => {
  const at = ((by % rows.length) + rows.length) % rows.length
  return [...rows.slice(at), ...rows.slice(0, at)]
}

/** The fixture people lead every list so the names in the modal are the names
 *  elsewhere in the app; the pool fills the rest. Both are rotated by the seed
 *  and the entry index, so two emojis on one target are not the same people in
 *  the same order. */
const reactorsFor = (seed: number, index: number, count: number): ActorRefDto[] => [
  ...rotate(KNOWN_REACTORS, seed + index),
  ...rotate(REACTOR_POOL, seed * 7 + index * 31),
].slice(0, count)

const seedRoster = (targetType: ReactionTargetType, targetId: string): ReactionRoster => {
  const seed = hashString(rosterKey(targetType, targetId))
  // Every fifth target has no reactions at all: the empty summary path is live
  // and has to stay reachable.
  const distinct = seed % 5 === 0 ? 0 : 1 + (seed % 8)
  const start = seed % REACTION_EMOJIS.length

  const entries = Array.from({ length: distinct }, (_, index) => {
    const emoji = REACTION_EMOJIS[(start + index) % REACTION_EMOJIS.length]
    const count = 1 + ((seed + index * 13) % 40)
    return { emoji, reactors: reactorsFor(seed, index, count) }
  }).sort(byCountThenPalette)

  // A third of targets carry the viewer, on their most reacted emoji. This is
  // the whole of what `viewerReactions` means: `summaryFor` reads it back off
  // the roster rather than asserting it beside one.
  if (entries.length > 0 && seed % 3 === 0) {
    entries[0].reactors = [personRef(VIEWER.username), ...entries[0].reactors.slice(0, -1)]
  }

  return { entries }
}

const rosterFor = (targetType: ReactionTargetType, targetId: string): ReactionRoster => {
  const key = rosterKey(targetType, targetId)
  const existing = rosters.get(key)
  if (existing) return existing
  const seeded = seedRoster(targetType, targetId)
  rosters.set(key, seeded)
  return seeded
}

// ── Derivations ──────────────────────────────────────────────────────────────

/** The three plus "+N" projection every card renders. Derived, never stored. */
export const summaryFor = (
  targetType: ReactionTargetType,
  targetId: string,
): ReactionSummaryDto => {
  const { entries } = rosterFor(targetType, targetId)
  const top = entries
    .slice(0, REACTION_TOP_MAX)
    .map((entry) => ({ emoji: entry.emoji, count: entry.reactors.length }))
  const total = entries.reduce((sum, entry) => sum + entry.reactors.length, 0)
  const othersCount = total - top.reduce((sum, entry) => sum + entry.count, 0)
  const viewerReactions = entries
    .filter((entry) => entry.reactors.some((actor) => actor.id === VIEWER.id))
    .map((entry) => entry.emoji)

  // Omitted rather than empty, so the `?? []` call sites keep behaving.
  return { top, othersCount, total, ...(viewerReactions.length > 0 ? { viewerReactions } : {}) }
}

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
