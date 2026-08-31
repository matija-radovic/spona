// -----------------------------------------------------------------------------
// MOCK posts + comments service (the post page and its thread). The Spring Boot
// backend does not exist yet, so every call fakes a delayed response; the real
// axios call is left commented above each mock body. DTOs are separate from the
// DB model (new_docx/types.ts): `responseCount`, `siblingIndex`, `siblingTotal`
// and the `viewerCan*` pair are DERIVED, not columns.
//
// The thread is MUTABLE module state, seeded per post on first read, so an edit
// or a delete survives the next fetch instead of being silently undone - the
// memberships.ts discipline.
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`.
// Magic inputs:
//   - fetchPost      : postId === "post-404"   -> 404 Not Found
//   - fetchPost      : postId === "post-mine"  -> authored by user-my-account,
//                                                 both viewerCan* flags set
//   - fetchComments  : postId === "post-empty" -> zero comments, empty thread
//   - every post     : root comment #1 has 23 replies, to exercise the
//                      load-more arithmetic and the sibling windows
//   - every post     : every 4th comment is authored by user-my-account and
//                      carries both viewerCan* flags (OWN_ACTOR_IDS in
//                      activity.ts does the same job there)
//   - createComment  : content containing "403" -> 403, which is how the
//                      acting-identity cache invalidation path is reachable
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { PagedResponse, ProblemDetail } from './types'
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

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/posts',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const MAGIC_NOT_FOUND_POST = 'post-404'
const MAGIC_EMPTY_POST = 'post-empty'
const MAGIC_OWN_POST = 'post-mine'
const MAGIC_FORBIDDEN_CONTENT = '403'
/** Comments per post, and the reply count on the first root of every post. */
const ROOTS_PER_POST = 26
const REPLIES_ON_FIRST_ROOT = 23
const MAX_MOCK_PAGES = 3
/** Below this many reactions a comment ranks as 0 under `sort=top` (D43). */
const TOP_CLAMP = 3
const PALETTE = ['👍', '❤️', '😂', '🎉', '🔥', '🙏', '👏', '😮'] as const

const iso = (minutesAgo: number): string =>
  new Date(Date.now() - minutesAgo * 60 * 1000).toISOString()

// Mock actors, matching the ids and handles profiles.ts serves so a byline links
// somewhere real. `my-account` is the editable "own" identity there too.
const AUTHORS: readonly ActorRefDto[] = [
  { actorType: 'user', id: 'user-my-account', name: 'Ana Jovanović', handle: 'my-account', avatarUrl: null },
  { actorType: 'user', id: 'user-marko', name: 'Marko Marković', handle: 'marko', avatarUrl: null },
  { actorType: 'user', id: 'user-jelena', name: 'Jelena Petrović', handle: 'jelena', avatarUrl: null },
  { actorType: 'org', id: 'org-moja', name: 'Moja Kompanija', handle: 'moja-kompanija', avatarUrl: null },
  { actorType: 'org', id: 'org-nordeus', name: 'Nordeus', handle: 'nordeus', avatarUrl: null },
]

const AUTHOR_BIOS: Readonly<Record<string, string>> = {
  'user-my-account': 'Frontend inženjerka. Gradim pristupačne interfejse i volim dobar dizajn sistema.',
  'user-marko': 'Backend developer. Java, Spring, PostgreSQL.',
  'user-jelena': 'Product designer. Radim na proizvodima za region.',
  'org-moja': 'Softverska agencija. Radimo web i mobilne aplikacije po meri.',
  'org-nordeus': 'Gejming studio iz Beograda. Pravimo igre koje igraju milioni.',
}

/** The actor whose content the mock viewer may edit and delete. */
const OWN_ACTOR_ID = 'user-my-account'

/** Resolves a `createdForId` to a byline. Ids the mock does not know about are
 *  derived the way the auth mock derives a user from a login identifier, so
 *  signing in as anyone produces a believable author. */
const authorFor = (actorId: string): ActorRefDto => {
  const known = AUTHORS.find((actor) => actor.id === actorId)
  if (known) return known
  const handle = actorId.replace(/^(user|org)-/, '')
  return {
    actorType: actorId.startsWith('org-') ? 'org' : 'user',
    id: actorId,
    name: handle,
    handle,
    avatarUrl: null,
  }
}

const POST_BODIES = [
  'Danas smo objavili novu verziju - hvala celom timu na trudu!\n\nNajveća promena je novi sistem za komentare: sada možete da odgovarate direktno u niti, sa reakcijama na svakom nivou.',
  'Tražimo saradnike za zanimljiv projekat. Javite se ako vas zanima frontend.',
  'Nekoliko misli o pristupačnosti na webu i zašto je važna od prvog dana.\n\nNije stvar u tome da naknadno dodate ARIA atribute - stvar je u tome da struktura stranice od početka opisuje šta se na njoj nalazi.',
  'Odlična konferencija ovog vikenda. Puno novih ideja za naredni kvartal.',
  'Podelili bismo iskustvo sa migracijom na nov sistem - AMA u komentarima.',
]

const COMMENT_BODIES = [
  'Sjajno, čestitke celom timu!',
  'Slažem se, ovo je odavno trebalo.',
  'Možeš li da pojasniš kako ste rešili keširanje?',
  'Hvala na deljenju, korisno.',
  'Zanimljiv ugao, nisam tako gledao na to.',
  'Da li planirate da objavite i merenja performansi?',
  'Ovo je tačno ono što nam je trebalo na prošlom projektu.',
]

const REPLY_BODIES = [
  'Tako je.',
  'Detaljnije smo pisali o tome u dokumentaciji.',
  'Hvala, javljam se porukom.',
  'Nismo, ali je na listi za sledeći kvartal.',
  'Dobra primedba - proveriću i vraćam se.',
]

/** Cheap stable hash so a given id always renders the same shape. */
const hashString = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash
}

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

// ── Mutable stores (see the header note) ─────────────────────────────────────

const posts = new Map<string, PostDto>()
/** Flat per post: roots and replies together, `parentId` telling them apart. */
const threads = new Map<string, ThreadCommentDto[]>()

const buildPost = (postId: string): PostDto => {
  const seed = hashString(postId)
  const own = postId === MAGIC_OWN_POST
  const author = own ? AUTHORS[0] : AUTHORS[seed % AUTHORS.length]
  return {
    id: postId,
    author,
    content: POST_BODIES[seed % POST_BODIES.length],
    createdAt: iso(60 * 8 + (seed % 400)),
    commentCount: 0, // stamped from the thread on every read
    reactions: mockReactions(seed + 1),
    authorBio: AUTHOR_BIOS[author.id] ?? null,
    authorFollowedByViewer: seed % 3 === 0,
    ...(own || author.id === OWN_ACTOR_ID ? { viewerCanEdit: true, viewerCanDelete: true } : {}),
  }
}

const buildComment = (
  postId: string,
  id: string,
  parentId: string | null,
  index: number,
  minutesAgo: number,
): ThreadCommentDto => {
  const bodies = parentId ? REPLY_BODIES : COMMENT_BODIES
  // Every 4th comment is the viewer's own, so the edit/delete affordances are
  // reachable in the mock. The real API reads both flags off the JWT.
  const own = index % 4 === 3
  const author = own ? AUTHORS[0] : AUTHORS[(index + 1) % AUTHORS.length]
  return {
    id,
    postId,
    parentId,
    author,
    content: bodies[index % bodies.length],
    createdAt: iso(minutesAgo),
    reactions: mockReactions(index + (parentId ? 2 : 0)),
    responseCount: 0, // stamped from the thread on every read
    ...(own ? { viewerCanEdit: true, viewerCanDelete: true } : {}),
  }
}

const seedThread = (postId: string): ThreadCommentDto[] => {
  const existing = threads.get(postId)
  if (existing) return existing

  const rows: ThreadCommentDto[] = []
  if (postId !== MAGIC_EMPTY_POST) {
    for (let i = 0; i < ROOTS_PER_POST; i += 1) {
      const rootId = `${postId}-c${i}`
      rows.push(buildComment(postId, rootId, null, i, 60 * 6 - i * 11))
      // The first root of every post is the load-more / sibling-window fixture.
      const replies = i === 0 ? REPLIES_ON_FIRST_ROOT : i % 3 === 0 ? 0 : (i % 4) + 1
      for (let r = 0; r < replies; r += 1) {
        rows.push(buildComment(postId, `${rootId}-r${r}`, rootId, r, 60 * 6 - i * 11 - r * 3 - 1))
      }
    }
  }
  threads.set(postId, rows)
  return rows
}

const seedPost = (postId: string): PostDto => {
  const existing = posts.get(postId)
  if (existing) return existing
  const built = buildPost(postId)
  posts.set(postId, built)
  return built
}

const rootsOf = (postId: string): ThreadCommentDto[] =>
  seedThread(postId).filter((row) => row.parentId === null)

const findComment = (commentId: string): ThreadCommentDto | undefined => {
  for (const rows of threads.values()) {
    const hit = rows.find((row) => row.id === commentId)
    if (hit) return hit
  }
  // A deep link can arrive before its post's thread has ever been seeded; the
  // generated id carries the post it belongs to.
  const postId = commentId.split('-c')[0]
  if (!postId || postId === commentId) return undefined
  return seedThread(postId).find((row) => row.id === commentId)
}

const repliesOf = (postId: string, parentId: string): ThreadCommentDto[] =>
  seedThread(postId).filter((row) => row.parentId === parentId)

/** roots + replies (D42). */
const commentCountOf = (postId: string): number => seedThread(postId).length

/** A copy with `responseCount` stamped from the live thread. */
const project = (row: ThreadCommentDto): ThreadCommentDto => ({
  ...row,
  responseCount: row.parentId === null ? repliesOf(row.postId, row.id).length : 0,
})

const projectPost = (post: PostDto): PostDto => ({
  ...post,
  commentCount: commentCountOf(post.id),
})

/**
 * The `top` clamp: 0, 1 and 2 reactions all rank as 0, so a single reaction
 * cannot promote a comment above a genuinely discussed one. Ties break on
 * `createdAt` ascending so the order is reproducible across requests.
 */
const sortRoots = (rows: ThreadCommentDto[], sort: CommentSort): ThreadCommentDto[] => {
  const byOldest = (a: ThreadCommentDto, b: ThreadCommentDto) =>
    Date.parse(a.createdAt) - Date.parse(b.createdAt)

  if (sort === 'old') return [...rows].sort(byOldest)
  if (sort === 'new') return [...rows].sort((a, b) => byOldest(b, a))

  const rank = (row: ThreadCommentDto) => (row.reactions.total < TOP_CLAMP ? 0 : row.reactions.total)
  return [...rows].sort((a, b) => rank(b) - rank(a) || byOldest(a, b))
}

const hrefFor = (path: string, params: Record<string, string | number | undefined>): string => {
  const sp = new URLSearchParams()
  for (const [key, raw] of Object.entries(params)) {
    if (raw !== undefined && raw !== '') sp.set(key, String(raw))
  }
  const query = sp.toString()
  return query ? `/api${path}?${query}` : `/api${path}`
}

const pageOf = <T>(rows: T[], page: number, size: number, self: string, next: string, prev: string): PagedResponse<T> => {
  const totalElements = rows.length
  const totalPages = totalElements === 0 ? 0 : Math.min(Math.ceil(totalElements / size), MAX_MOCK_PAGES)
  const last = page >= totalPages
  const content = page > totalPages ? [] : rows.slice((page - 1) * size, (page - 1) * size + size)

  return {
    _links: {
      self: { href: self },
      ...(page > 1 ? { prev: { href: prev } } : {}),
      ...(last ? {} : { next: { href: next } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}

// ── Posts ────────────────────────────────────────────────────────────────────

/** MOCK - would be: const { data } = await api.get(`/posts/${postId}`) */
export const fetchPost = async (postId: string): Promise<PostDto> => {
  await delay(380)
  if (postId === MAGIC_NOT_FOUND_POST) {
    return rejectProblem(404, 'Objava ne postoji ili je obrisana.', 'Not Found')
  }
  return projectPost(seedPost(postId))
}

/**
 * MOCK - would be: const { data } = await api.put(`/posts/${postId}`, { content })
 * There is no `updatedAt` and no "edited" marker: an edit is a silent in-place
 * replacement, which is a decision and not an oversight.
 */
export const updatePost = async (postId: string, content: string): Promise<PostDto> => {
  await delay(360)
  const post = seedPost(postId)
  if (!post.viewerCanEdit) {
    return rejectProblem(403, 'Nemate pravo da menjate ovu objavu.', 'Forbidden')
  }
  const next = { ...post, content }
  posts.set(postId, next)
  return projectPost(next)
}

/** MOCK - would be: await api.delete(`/posts/${postId}`) */
export const deletePost = async (postId: string): Promise<void> => {
  await delay(380)
  const post = seedPost(postId)
  if (!post.viewerCanDelete) {
    return rejectProblem(403, 'Nemate pravo da obrišete ovu objavu.', 'Forbidden')
  }
  posts.delete(postId)
  threads.delete(postId)
}

// ── Comments ─────────────────────────────────────────────────────────────────

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/posts/${postId}/comments`,
 *                                  { params: { page, size, sort, exclude } })
 * `sort` is omitted for the default (`top`) - a default is the absence of a
 * value, not a value. `exclude` names one comment and withholds the ROOT it
 * belongs to, so a deep link renders that thread out of band and the paginated
 * list never serves it a second time - which is what removes every client-side
 * deduplication pass, including for a link to a reply. Results are capped at
 * MAX_MOCK_PAGES so infinite scroll terminates.
 */
export const fetchComments = async (
  postId: string,
  { page = 1, size = 10, sort = 'top', exclude }: CommentQuery = {},
): Promise<PagedResponse<ThreadCommentDto>> => {
  await delay(420)
  if (postId === MAGIC_NOT_FOUND_POST) {
    return rejectProblem(404, 'Objava ne postoji ili je obrisana.', 'Not Found')
  }

  const excludedRoot = exclude ? (findComment(exclude)?.parentId ?? exclude) : undefined
  const rows = sortRoots(rootsOf(postId), sort)
    .filter((row) => row.id !== excludedRoot)
    .map(project)

  const params = { page, size, sort: sort === 'top' ? undefined : sort, exclude }
  return pageOf(
    rows,
    page,
    size,
    hrefFor(`/posts/${postId}/comments`, params),
    hrefFor(`/posts/${postId}/comments`, { ...params, page: page + 1 }),
    hrefFor(`/posts/${postId}/comments`, { ...params, page: page - 1 }),
  )
}

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/comments/${commentId}/responses`,
 *                                  { params: { page, size } })
 * Responses are ALWAYS chronological and the route takes no `sort`: the deep
 * link's sibling arithmetic indexes into this ordering, and an index into a
 * reaction-ranked list is not stable across requests.
 */
export const fetchResponses = async (
  commentId: string,
  page = 1,
  size = 5,
): Promise<PagedResponse<ThreadCommentDto>> => {
  await delay(340)
  const parent = findComment(commentId)
  if (!parent) return rejectProblem(404, 'Komentar ne postoji.', 'Not Found')

  const rows = repliesOf(parent.postId, parent.id)
    .slice()
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    .map(project)

  // Responses are a bounded set under the depth cap, so they are never truncated
  // to MAX_MOCK_PAGES the way an endless root list is.
  const totalElements = rows.length
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
  const params = { page, size }
  return {
    _links: {
      self: { href: hrefFor(`/comments/${commentId}/responses`, params) },
      ...(page > 1 ? { prev: { href: hrefFor(`/comments/${commentId}/responses`, { ...params, page: page - 1 }) } } : {}),
      ...(page >= totalPages
        ? {}
        : { next: { href: hrefFor(`/comments/${commentId}/responses`, { ...params, page: page + 1 }) } }),
    },
    content: rows.slice((page - 1) * size, (page - 1) * size + size),
    page,
    size,
    totalElements,
    totalPages,
    last: page >= totalPages,
  }
}

/**
 * MOCK - would be:
 *   const { data } = await api.get(`/comments/${commentId}`, { params: { context: 1 } })
 * `context=1` additionally returns the parent (when this is a reply) and the
 * comment's position among its siblings, which is what lets the client window
 * the surrounding replies from plain page/size.
 *
 * The post-scoped alias `/posts/{postId}/comments/{commentId}` reaches the same
 * handler and adds one containment predicate; the client only calls the flat
 * form.
 */
export const fetchCommentContext = async (commentId: string): Promise<CommentContextDto> => {
  await delay(360)
  const comment = findComment(commentId)
  if (!comment) return rejectProblem(404, 'Komentar ne postoji ili je obrisan.', 'Not Found')

  const parent = comment.parentId ? (findComment(comment.parentId) ?? null) : null
  const siblings = comment.parentId
    ? repliesOf(comment.postId, comment.parentId)
        .slice()
        .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    : sortRoots(rootsOf(comment.postId), 'top')

  return {
    comment: project(comment),
    parent: parent ? project(parent) : null,
    siblingIndex: Math.max(0, siblings.findIndex((row) => row.id === comment.id)),
    siblingTotal: siblings.length,
  }
}

/**
 * MOCK - would be:
 *   const { data } = await api.post(`/posts/${postId}/comments`, payload)
 * `createdForId` is always sent and never null; the server re-checks the role
 * matrix on every write, so a 403 here means the caller may not publish as that
 * actor and must not be retried under a different identity.
 */
export const createComment = async (
  postId: string,
  payload: CreateCommentPayload,
): Promise<ThreadCommentDto> => {
  await delay(460)
  if (payload.content.includes(MAGIC_FORBIDDEN_CONTENT)) {
    return rejectProblem(403, 'Ne možete objavljivati u ime te organizacije.', 'Forbidden')
  }

  const rows = seedThread(postId)
  const created: ThreadCommentDto = {
    id: makeId(),
    postId,
    parentId: payload.parentId ?? null,
    author: authorFor(payload.createdForId),
    content: payload.content,
    createdAt: new Date().toISOString(),
    reactions: { top: [], othersCount: 0, total: 0 },
    responseCount: 0,
    viewerCanEdit: true,
    viewerCanDelete: true,
  }
  threads.set(postId, [...rows, created])
  return project(created)
}

/** MOCK - would be: const { data } = await api.put(`/comments/${commentId}`, { content }) */
export const updateComment = async (commentId: string, content: string): Promise<ThreadCommentDto> => {
  await delay(340)
  const comment = findComment(commentId)
  if (!comment) return rejectProblem(404, 'Komentar ne postoji.', 'Not Found')
  if (!comment.viewerCanEdit) {
    return rejectProblem(403, 'Nemate pravo da menjate ovaj komentar.', 'Forbidden')
  }

  const next = { ...comment, content }
  threads.set(
    comment.postId,
    seedThread(comment.postId).map((row) => (row.id === commentId ? next : row)),
  )
  return project(next)
}

/**
 * MOCK - would be: const { data } = await api.delete(`/comments/${commentId}`)
 * Deleting a root cascades to its replies. The response reports how many rows
 * went, because the client has to warn before the fact and reconcile the post's
 * comment count after it.
 */
export const deleteComment = async (commentId: string): Promise<DeleteCommentResultDto> => {
  await delay(380)
  const comment = findComment(commentId)
  if (!comment) return rejectProblem(404, 'Komentar ne postoji.', 'Not Found')
  if (!comment.viewerCanDelete) {
    return rejectProblem(403, 'Nemate pravo da obrišete ovaj komentar.', 'Forbidden')
  }

  const rows = seedThread(comment.postId)
  const doomed = new Set([commentId, ...rows.filter((row) => row.parentId === commentId).map((row) => row.id)])
  threads.set(
    comment.postId,
    rows.filter((row) => !doomed.has(row.id)),
  )
  return { deletedCount: doomed.size }
}
