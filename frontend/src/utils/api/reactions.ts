// -----------------------------------------------------------------------------
// MOCK reactions service. The Spring Boot backend does not exist yet, so every
// call fakes a delayed response; the real axios call is left commented above
// each mock body. Rejections are `ProblemDetail` shapes so callers can
// `parseProblem(err)`.
//
// Reactions are their own domain rather than a corner of one: posts, comments
// and the profile Activity pane all read the summary and all write through this
// one call.
//
// THE ROSTER IS THE SOURCE OF TRUTH, AND THE SUMMARY IS DERIVED FROM IT. One
// seeded `{ emoji, reactors }[]` per target, mutable module state in the
// `posts.ts` discipline: seeded on first read, mutated in place, so a reaction
// taken back does not come back on the next fetch. `summaryFor` projects the
// three-plus-"+N" shape a card renders and `fetchReactionBreakdown` serves the
// whole thing, so the two can never disagree - which is the one bug this feature
// is capable of having, since the breakdown opens two pixels under the summary.
// Every read in `posts.ts` and `activity.ts` re-stamps its summary from here,
// exactly as `fetchPost` re-stamps `commentCount` from the thread store.
//
// `fetchReactionBreakdown` IS THE ONE READ IN THIS FOLDER THAT IS NOT PAGED, and
// that is deliberate rather than forgotten. It takes no `page`, no `size`, no
// `emoji` and answers no envelope: the response is bounded by one target's own
// reactions, the modal is a whole-picture view that has to state every count
// before the reader picks anything, and paging it would put an infinite scroll
// inside a modal pane. The cost is honest - a heavily reacted target sends every
// reactor in one body. The trigger to revisit is a measurement, thousands of
// reactions on one target or a body past a few hundred kilobytes, and the
// upgrade is `?emoji=` answering `PagedResponse<ActorRefDto>` with the
// unfiltered call still serving the counts. It is not served today because
// nothing sends it, and a parameter no caller sends is an untested shape.
// backend-contract.md §7e has the same reasoning for the backend.
//
// Magic inputs:
//   - setReaction             : empty targetId -> 400 Bad Request
//   - fetchReactionBreakdown  : empty targetId -> 400 Bad Request
//   - fetchReactionBreakdown  : targetId === "post-reactions" -> all twenty
//                               palette emojis and roughly six hundred reactors,
//                               which is the row to open when checking that one
//                               unpaged request and two scrolled panes behave
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import { REACTION_EMOJIS } from '@utils/constants/reactions'
import type { ProblemDetail } from './types'
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

const MAGIC_MANY_REACTIONS = 'post-reactions'

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

/** The `post-reactions` fixture: a steep curve rather than a flat one, so the
 *  lane has a widest row worth measuring and twenty entries still land near six
 *  hundred reactors. */
const manyCount = (seed: number, index: number): number =>
  Math.max(20, Math.round(120 / (index + 1))) + ((seed + index) % 5)

const seedRoster = (targetType: ReactionTargetType, targetId: string): ReactionRoster => {
  const seed = hashString(rosterKey(targetType, targetId))
  const many = targetId === MAGIC_MANY_REACTIONS
  // Every fifth target has no reactions at all: the empty summary path is live
  // and has to stay reachable.
  const distinct = many ? REACTION_EMOJIS.length : seed % 5 === 0 ? 0 : 1 + (seed % 8)
  const start = seed % REACTION_EMOJIS.length

  const entries = Array.from({ length: distinct }, (_, index) => {
    const emoji = REACTION_EMOJIS[many ? index : (start + index) % REACTION_EMOJIS.length]
    const count = many ? manyCount(seed, index) : 1 + ((seed + index * 13) % 40)
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

/** MOCK ONLY - a row that was just created carries no reactions, and a roster
 *  seeded lazily off the id hash would invent some. The real API has rows and
 *  needs no such seam. */
export const seedEmptyRoster = (targetType: ReactionTargetType, targetId: string): void => {
  rosters.set(rosterKey(targetType, targetId), { entries: [] })
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

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/reactions/${targetType}/${targetId}`)
 * One request answers the whole breakdown; there is no `emoji` parameter and no
 * `PagedResponse` here, for the reason in this file's header. The response is a
 * deep copy of the roster rather than the roster itself: a caller holding the
 * live arrays would watch counts change under it when `setReaction` runs, which
 * is not what an HTTP response does.
 */
export const fetchReactionBreakdown = async (
  targetType: ReactionTargetType,
  targetId: string,
): Promise<ReactionBreakdownDto> => {
  await delay(320)
  if (!targetId) return rejectProblem(400, 'Nedostaje identifikator sadržaja.', 'Bad Request')

  const { entries } = rosterFor(targetType, targetId)
  return {
    entries: entries.map((entry) => ({
      emoji: entry.emoji,
      count: entry.reactors.length,
      reactors: entry.reactors.map((actor) => ({ ...actor })),
    })),
    total: entries.reduce((sum, entry) => sum + entry.reactors.length, 0),
  }
}

// ── The write ────────────────────────────────────────────────────────────────

/**
 * MOCK. Adds or removes the viewer's reaction (one user x one emoji x one target,
 * per the Reaction unique constraint). Real call switches verb on `next`:
 *   await api({ method: next ? 'put' : 'delete',
 *               url: `/reactions/${targetType}/${targetId}`, data: { emoji } })
 * It writes the roster rather than only delaying, so a viewer who reacts and
 * then opens the breakdown is in the list they just joined.
 */
export const setReaction = async (
  targetType: ReactionTargetType,
  targetId: string,
  emoji: string,
  next: boolean,
): Promise<void> => {
  await delay(200)
  if (!targetId) return rejectProblem(400, 'Nedostaje identifikator sadržaja.', 'Bad Request')

  const roster = rosterFor(targetType, targetId)
  const entry = roster.entries.find((row) => row.emoji === emoji)

  if (next) {
    if (!entry) roster.entries.push({ emoji, reactors: [personRef(VIEWER.username)] })
    else if (!entry.reactors.some((actor) => actor.id === VIEWER.id)) {
      entry.reactors = [personRef(VIEWER.username), ...entry.reactors]
    }
  } else if (entry) {
    entry.reactors = entry.reactors.filter((actor) => actor.id !== VIEWER.id)
    if (entry.reactors.length === 0) {
      roster.entries = roster.entries.filter((row) => row !== entry)
    }
  }

  roster.entries.sort(byCountThenPalette)
}
