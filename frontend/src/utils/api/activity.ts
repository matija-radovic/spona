// -----------------------------------------------------------------------------
// MOCK activity service (posts + comments for an actor's profile Activity pane).
// The Spring Boot backend does not exist yet, so every call fakes a delayed
// response; the real axios call is left commented above each mock body. DTOs are
// separate from the DB model (new_docx/types.ts): each item ships a stripped,
// discriminated `author` (== the profile owner here, generic for feed reuse).
//
// Magic inputs:
//   - fetchActorActivity : actorId === "user-marko"  -> empty page (no activity)
//   - fetchActorActivity : the viewer, or an org they own/administer -> viewerCan*
//                      true on every item (`viewerOwnsContentOf`)
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { PagedResponse } from './types'
import type { ActorRefDto } from './profiles'
import type { ReactionSummaryDto } from './reactions'
import type { PostDto } from './posts'
import { userIdFor, viewerOwnsContentOf } from './mockActors'

// ── DTOs ────────────────────────────────────────────────────────────────────

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

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const MAGIC_EMPTY_ACTOR = userIdFor('marko')
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

/** `viewerOwns` grants both authority flags; false leaves them off entirely,
 *  which is the wire shape a guest or a non-author gets. */
const buildPost = (author: ActorRefDto, index: number, viewerOwns: boolean): ActivityPostDto => ({
  kind: 'post',
  id: makeId(),
  author,
  content: POST_BODIES[index % POST_BODIES.length],
  createdAt: iso(index * 7 + 2),
  commentCount: index % 3 === 0 ? 0 : (index * 2) % 9,
  reactions: mockReactions(index + 1),
  ...(viewerOwns ? { viewerCanEdit: true, viewerCanDelete: true } : {}),
})

/** Comment ids follow the thread mock's `{postId}-c{n}` / `{postId}-c0-r{n}`
 *  scheme, so the jump button's deep link resolves to a real comment on the post
 *  page instead of a 404. The real API has one id space and needs no such rule. */
const buildComment = (
  author: ActorRefDto,
  index: number,
  viewerOwns: boolean,
): ActivityCommentDto => {
  const postId = `post-${(index % 4) + 1}`
  const isReply = index % 2 === 1
  return {
    kind: 'comment',
    id: isReply ? `${postId}-c0-r${index % 23}` : `${postId}-c${index % 26}`,
    postId,
    isReply,
    author,
    content: COMMENT_BODIES[index % COMMENT_BODIES.length],
    createdAt: iso(index * 9 + 5),
    reactions: mockReactions(index),
    responseCount: isReply ? 0 : index % 3 === 0 ? 0 : (index % 4) + 1,
    ...(viewerOwns ? { viewerCanEdit: true, viewerCanDelete: true } : {}),
  }
}

/** Interleaves posts and comments for the `all` feed; single-kind otherwise. */
const buildPage = (
  author: ActorRefDto,
  kind: ActivityKind,
  page: number,
  size: number,
  viewerOwns: boolean,
): ActivityItemDto[] => {
  const base = (page - 1) * size
  return Array.from({ length: size }, (_, i) => {
    const n = base + i
    if (kind === 'posts') return buildPost(author, n, viewerOwns)
    if (kind === 'comments') return buildComment(author, n, viewerOwns)
    return n % 2 === 0 ? buildPost(author, n, viewerOwns) : buildComment(author, n, viewerOwns)
  })
}

const hrefFor = (actorId: string, kind: string, page: number, size: number): string =>
  `/api/actors/${actorId}/activity?page=${page}&size=${size}&kind=${kind}`

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/actors/${actorId}/activity`, { params: { kind, page, size } })
 * Results are capped at MAX_MOCK_PAGES so infinite scroll terminates. The `owner`
 * ref is stamped as each item's author (the real API embeds it server-side).
 * Items on a profile whose content the viewer owns get both authority flags -
 * the real API reads them off the JWT, so no request parameter selects this.
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

  const content = buildPage(owner, kind, page, size, viewerOwnsContentOf(actorId)).slice(0, count)

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

