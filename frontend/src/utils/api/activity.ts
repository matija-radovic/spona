// -----------------------------------------------------------------------------
// MOCK activity service (posts + comments for an actor's profile Activity pane).
// The Spring Boot backend does not exist yet, so every call fakes a delayed
// response; the real axios call is left commented above each mock body. DTOs are
// separate from the DB model (types-ref.r3): each item ships a stripped,
// discriminated `author` (== the profile owner here, generic for feed reuse).
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`.
// Magic input:
//   - fetchActorActivity : actorId === "user-marko" -> empty page (no activity)
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { PagedResponse, ProblemDetail } from './types'
import type { ActorRefDto } from './profiles'

// ── DTOs ────────────────────────────────────────────────────────────────────

export interface ReactionSummaryDto {
  /** Up to 3 entries, ordered by count desc. */
  top: { emoji: string; count: number }[]
  /** Total count of reactions NOT covered by `top` - renders as "+N". */
  othersCount: number
  total: number
  /** Emojis the current viewer has used on this target (authenticated only). */
  viewerReactions?: string[]
}

export interface ActivityPostDto {
  kind: 'post'
  id: string
  author: ActorRefDto
  content: string
  createdAt: string
  commentCount: number
  reactions: ReactionSummaryDto
}

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
}

export type ActivityItemDto = ActivityPostDto | ActivityCommentDto
export type ActivityKind = 'all' | 'posts' | 'comments'

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/activity',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const MAGIC_EMPTY_ACTOR = 'user-marko'
const MAX_MOCK_PAGES = 3
const PALETTE = ['👍', '❤️', '😂', '🎉', '🔥', '🙏', '👏', '😮'] as const

const iso = (hoursAgo: number): string =>
  new Date(Date.now() - hoursAgo * 60 * 60 * 1000).toISOString()

const POST_BODIES = [
  'Danas smo objavili novu verziju - hvala celom timu na trudu!',
  'Tražimo saradnike za zanimljiv projekat. Javite se ako vas zanima frontend.',
  'Nekoliko misli o pristupačnosti na webu i zašto je važna od prvog dana.',
  'Odlična konferencija ovog vikenda. Puno novih ideja za naredni kvartal.',
  'Podelili bismo iskustvo sa migracijom na nov sistem - AMA u komentarima.',
]

const COMMENT_BODIES = [
  'Sjajno, čestitke celom timu!',
  'Slažem se, ovo je odavno trebalo.',
  'Možeš li da pojasniš kako ste rešili keširanje?',
  'Hvala na deljenju, korisno.',
  'Zanimljiv ugao, nisam tako gledao na to.',
]

/** Builds a believable reaction summary for a given seed: 0 / 1-3 / >3 emojis. */
const mockReactions = (seed: number): ReactionSummaryDto => {
  const distinct = seed % 5 // 0..4 distinct emojis
  if (distinct === 0) return { top: [], othersCount: 0, total: 0 }

  const top = Array.from({ length: Math.min(distinct, 3) }, (_, i) => ({
    emoji: PALETTE[(seed + i) % PALETTE.length],
    count: Math.max(1, ((seed + i * 3) % 12) + (3 - i)),
  }))
  const others = distinct > 3 ? Array.from({ length: distinct - 3 }, (_, i) => ((seed + i) % 4) + 1) : []
  const othersCount = others.reduce((a, b) => a + b, 0)
  const total = top.reduce((a, e) => a + e.count, 0) + othersCount
  const viewerReactions = seed % 3 === 0 ? [top[0].emoji] : undefined
  return { top, othersCount, total, viewerReactions }
}

const makeId = (): string =>
  crypto.randomUUID?.() ?? `mock-${Math.floor(Math.random() * 10000000)}`

const buildPost = (author: ActorRefDto, index: number): ActivityPostDto => ({
  kind: 'post',
  id: makeId(),
  author,
  content: POST_BODIES[index % POST_BODIES.length],
  createdAt: iso(index * 7 + 2),
  commentCount: index % 3 === 0 ? 0 : (index * 2) % 9,
  reactions: mockReactions(index + 1),
})

const buildComment = (author: ActorRefDto, index: number): ActivityCommentDto => ({
  kind: 'comment',
  id: makeId(),
  postId: `post-${(index % 4) + 1}`,
  isReply: index % 2 === 1,
  author,
  content: COMMENT_BODIES[index % COMMENT_BODIES.length],
  createdAt: iso(index * 9 + 5),
  reactions: mockReactions(index),
})

/** Interleaves posts and comments for the `all` feed; single-kind otherwise. */
const buildPage = (
  author: ActorRefDto,
  kind: ActivityKind,
  page: number,
  size: number,
): ActivityItemDto[] => {
  const base = (page - 1) * size
  return Array.from({ length: size }, (_, i) => {
    const n = base + i
    if (kind === 'posts') return buildPost(author, n)
    if (kind === 'comments') return buildComment(author, n)
    return n % 2 === 0 ? buildPost(author, n) : buildComment(author, n)
  })
}

const hrefFor = (actorId: string, kind: string, page: number, size: number): string =>
  `/api/actors/${actorId}/activity?page=${page}&size=${size}&kind=${kind}`

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/actors/${actorId}/activity`, { params: { kind, page, size } })
 * Results are capped at MAX_MOCK_PAGES so infinite scroll terminates. The `owner`
 * ref is stamped as each item's author (the real API embeds it server-side).
 */
export const fetchActorActivity = async (
  actorId: string,
  kind: ActivityKind,
  owner: ActorRefDto,
  page = 1,
  size = 10,
): Promise<PagedResponse<ActivityItemDto>> => {
  await delay(430)

  const empty = actorId === MAGIC_EMPTY_ACTOR
  const totalElements = empty ? 0 : 26
  const totalPages = totalElements === 0 ? 0 : Math.min(Math.ceil(totalElements / size), MAX_MOCK_PAGES)
  const last = page >= totalPages
  const count = totalElements === 0 || page > totalPages ? 0 : last ? totalElements - (page - 1) * size : size

  const content = buildPage(owner, kind, page, size).slice(0, count)

  return {
    _links: {
      self: { href: hrefFor(actorId, kind, page, size) },
      ...(page > 1 ? { prev: { href: hrefFor(actorId, kind, page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: hrefFor(actorId, kind, page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}

/**
 * MOCK. Adds or removes the viewer's reaction (one user x one emoji x one target,
 * per the Reaction unique constraint). Real call switches verb on `next`:
 *   await api({ method: next ? 'put' : 'delete',
 *               url: `/reactions/${targetType}/${targetId}`, data: { emoji } })
 */
export const setReaction = async (
  targetType: 'post' | 'comment',
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
